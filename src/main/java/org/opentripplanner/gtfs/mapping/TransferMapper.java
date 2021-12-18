package org.opentripplanner.gtfs.mapping;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.onebusaway.gtfs.model.Transfer;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.Station;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopLocation;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.Trip;
import org.opentripplanner.model.TripStopTimes;
import org.opentripplanner.model.transfer.ConstrainedTransfer;
import org.opentripplanner.model.transfer.RouteTransferPoint;
import org.opentripplanner.model.transfer.StationTransferPoint;
import org.opentripplanner.model.transfer.StopTransferPoint;
import org.opentripplanner.model.transfer.TransferConstraint;
import org.opentripplanner.model.transfer.TransferPoint;
import org.opentripplanner.model.transfer.TransferPriority;
import org.opentripplanner.model.transfer.TripTransferPoint;
import org.opentripplanner.util.logging.ThrottleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for mapping GTFS Transfer into the OTP model.
 *
 * <p>This mapper is stateful and not thread safe. Create a new mapper for every set
 * of transfers you want to map.
 */
class TransferMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TransferMapper.class);
  private static final Logger FIXED_ROUTE_ERROR = ThrottleLogger.throttle(LOG);

  /**
   * This transfer is recommended over other transfers. The routing algorithm should prefer this
   * transfer compared to other transfers, for example by assigning a lower weight to it.
   */
  private static final int RECOMMENDED = 0;

  /**
   * This means the departing vehicle will wait for the arriving one and leave sufficient time for a
   * rider to transfer between routes.
   */
  private static final int GUARANTEED = 1;

  /**
   * This is a regular transfer that is defined in the transit data (as opposed to OpenStreetMap
   * data). In the case that both are present, this should take precedence. Because the the duration
   * of the transfer is given and not the distance, walk speed will have no effect on this.
   */
  private static final int MIN_TIME = 2;

  /**
   * Transfers between these stops (and route/trip) is not possible (or not allowed), even if a
   * transfer is already defined via OpenStreetMap data or in transit data.
   */
  private static final int FORBIDDEN = 3;


  private final RouteMapper routeMapper;

  private final StationMapper stationMapper;

  private final StopMapper stopMapper;

  private final TripMapper tripMapper;

  private final TripStopTimes stopTimesByTrip;

  private final Multimap<Route, Trip> tripsByRoute = ArrayListMultimap.create();


  TransferMapper(
      RouteMapper routeMapper,
      StationMapper stationMapper,
      StopMapper stopMapper,
      TripMapper tripMapper,
      TripStopTimes stopTimesByTrip
  ) {
    this.routeMapper = routeMapper;
    this.stationMapper = stationMapper;
    this.stopMapper = stopMapper;
    this.tripMapper = tripMapper;
    this.stopTimesByTrip = stopTimesByTrip;
  }

  static TransferPriority mapTypeToPriority(int type) {
    switch (type) {
      case FORBIDDEN:
        return TransferPriority.NOT_ALLOWED;
      case GUARANTEED:
      case MIN_TIME:
        return TransferPriority.ALLOWED;
      case RECOMMENDED:
        return TransferPriority.RECOMMENDED;
    }
    throw new IllegalArgumentException("Mapping missing for type: " + type);
  }

  Collection<ConstrainedTransfer> map(Collection<org.onebusaway.gtfs.model.Transfer> allTransfers) {
    setup(!allTransfers.isEmpty());

    return allTransfers.stream().map(this::map)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
  }

  ConstrainedTransfer map(org.onebusaway.gtfs.model.Transfer rhs) {
    Trip fromTrip = tripMapper.map(rhs.getFromTrip());
    Trip toTrip = tripMapper.map(rhs.getToTrip());

    TransferConstraint constraint = mapConstraint(rhs, fromTrip, toTrip);

    // TODO TGR - Create a transfer for this se issue #3369
    int transferTime = rhs.getMinTransferTime();

    // If this transfer do not give any advantages in the routing, then drop it
    if(constraint.isRegularTransfer()) {
      if(transferTime > 0) {
        LOG.info("Transfer skipped, issue #3369: " + rhs);
      }
      else {
        LOG.warn("Transfer skipped - no effect on routing: " + rhs);
      }
      return null;
    }

    TransferPoint fromPoint = mapTransferPoint(rhs.getFromStop(), rhs.getFromRoute(), fromTrip, false);
    TransferPoint toPoint = mapTransferPoint(rhs.getToStop(), rhs.getToRoute(), toTrip, true);

    return new ConstrainedTransfer(null, fromPoint, toPoint, constraint);
  }

  private void setup(boolean run) {
    if(!run) { return; }

    for (Trip trip : tripMapper.getMappedTrips()) {
      tripsByRoute.put(trip.getRoute(), trip);
    }
  }

  private TransferConstraint mapConstraint(Transfer rhs, Trip fromTrip, Trip toTrip) {
    var builder = TransferConstraint.create();

    builder.guaranteed(rhs.getTransferType() == GUARANTEED);
    builder.staySeated(sameBlockId(fromTrip, toTrip));
    builder.priority(mapTypeToPriority(rhs.getTransferType()));

    return builder.build();
  }

  private TransferPoint mapTransferPoint(
          org.onebusaway.gtfs.model.Stop rhsStopOrStation,
          org.onebusaway.gtfs.model.Route rhsRoute,
          Trip trip,
          boolean boardTrip
  ) {
    Route route = routeMapper.map(rhsRoute);
    Station station = null;
    Stop stop = null;

    // A transfer is specified using Stops and/or Station, according to the GTFS specification:
    //
    //    If the stop ID refers to a station that contains multiple stops, this transfer rule
    //    applies to all stops in that station.
    //
    // Source: https://developers.google.com/transit/gtfs/reference/transfers-file

    if (rhsStopOrStation.getLocationType() == 0) {
      stop  = stopMapper.map(rhsStopOrStation);
    }
    else {
      station = stationMapper.map(rhsStopOrStation);
    }
    if(trip != null) {
      int stopPositionInPattern = stopPosition(trip, stop, station, boardTrip);
      return stopPositionInPattern < 0 ? null : new TripTransferPoint(trip, stopPositionInPattern);
    }
    else if(route != null) {
      var trips = tripsByRoute.get(route);
      if(trips.isEmpty()) { throw new IllegalStateException("No trips found for route: " + route); }

      int stopPositionInPattern = stopPosition(route, stop, station, boardTrip);
      return new RouteTransferPoint(route, stopPositionInPattern);
    }
    else if(stop != null) {
      return new StopTransferPoint(stop);
    }
    else if(station != null) {
      return new StationTransferPoint(station);
    }

    throw new IllegalStateException("Should not get here!");
  }


  private int stopPosition(Route route, Stop stop, Station station, boolean boardTrip) {
    var stopPosList = tripsByRoute.get(route).stream()
            .map(t -> stopPosition(t, stop, station, boardTrip))
            .distinct()
            .collect(Collectors.toList());

    if(stopPosList.size() == 1) { return stopPosList.get(0); }

    FIXED_ROUTE_ERROR.error(
        "In GTFS 'transfers.txt' a transfer-point can be a combination of route and stop/station!"
        + "OTP only support this case, if the stop/station have the same stop point in trip-"
        + "pattern for all trips in the route. Route: " + route
    );
    return -1;
  }


  private int stopPosition(Trip trip, Stop stop, Station station, boolean boardTrip) {
    List<StopTime> stopTimes = stopTimesByTrip.get(trip);

    // We can board at the first stop, but not alight.
    final int firstStopPos = boardTrip ? 0 : 1;
    // We can alight at the last stop, but not board, the lastStopPos is exclusive
    final int lastStopPos =  stopTimes.size() - (boardTrip ? 1 : 0);

    Predicate<StopLocation> stopMatches = station != null
            ? (s) -> (s instanceof Stop && ((Stop)s).getParentStation() == station)
            : (s) -> s == stop;

    for (int i = firstStopPos; i < lastStopPos; i++) {
      StopTime stopTime = stopTimes.get(i);
      if(boardTrip && !stopTime.getPickupType().isRoutable()) { continue; }
      if(!boardTrip && !stopTime.getDropOffType().isRoutable()) { continue; }

      if(stopMatches.test(stopTime.getStop())) {
        return i;
      }
    }
    return -1;
  }

  private boolean sameBlockId(Trip a, Trip b) {
    if (a == null || b == null) {
      return false;
    }
    return a.getBlockId() != null && a.getBlockId().equals(b.getBlockId());
  }
}
