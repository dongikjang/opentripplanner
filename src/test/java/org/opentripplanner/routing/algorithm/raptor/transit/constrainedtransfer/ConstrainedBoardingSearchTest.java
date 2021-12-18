package org.opentripplanner.routing.algorithm.raptor.transit.constrainedtransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.model.transfer.TransferConstraint.REGULAR_TRANSFER;
import static org.opentripplanner.routing.algorithm.raptor.transit.request.TestTransitCaseData.id;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.TransitMode;
import org.opentripplanner.model.transfer.ConstrainedTransfer;
import org.opentripplanner.model.transfer.RouteTransferPoint;
import org.opentripplanner.model.transfer.StationTransferPoint;
import org.opentripplanner.model.transfer.StopTransferPoint;
import org.opentripplanner.model.transfer.TransferConstraint;
import org.opentripplanner.model.transfer.TripTransferPoint;
import org.opentripplanner.routing.algorithm.raptor.transit.StopIndexForRaptor;
import org.opentripplanner.routing.algorithm.raptor.transit.TransitTuningParameters;
import org.opentripplanner.routing.algorithm.raptor.transit.TripPatternWithRaptorStopIndexes;
import org.opentripplanner.routing.algorithm.raptor.transit.request.TestRouteData;
import org.opentripplanner.routing.algorithm.raptor.transit.request.TestTransitCaseData;


public class ConstrainedBoardingSearchTest implements TestTransitCaseData {

    private static final FeedScopedId ID = id("ID");
    private static final TransferConstraint GUARANTEED_CONSTRAINT =
            TransferConstraint.create().guaranteed().build();
    private static final TransferConstraint NOT_ALLOWED_CONSTRAINT =
            TransferConstraint.create().notAllowed().build();
    private static final StopTransferPoint STOP_B_TX_POINT = new StopTransferPoint(STOP_B);
    private static final StopTransferPoint STOP_C_TX_POINT = new StopTransferPoint(STOP_C);

    private static final int TRIP_1_INDEX = 0;
    private static final int TRIP_2_INDEX = 1;
    public static final StationTransferPoint STATION_B_TX_POINT =
            new StationTransferPoint(STATION_B);

    private TestRouteData route1;
    private TestRouteData route2;
    private TripPatternWithRaptorStopIndexes pattern1;
    private TripPatternWithRaptorStopIndexes pattern2;
    private StopIndexForRaptor stopIndex;

    /**
     * Create transit data with 2 routes with a trip each.
     * <pre>
     *                              STOPS
     *                     A      B      C      D
     * Route R1
     *   - Trip R1-1:    10:00  10:10  10:20
     *   - Trip R1-2:    10:05  10:15  10:25
     * Route R2
     *   - Trip R2-1:           10:15  10:30  10:40
     *   - Trip R2-2:           10:20  10:35  10:45
     * </pre>
     * <ul>
     *     <li>
     *         The transfer at stop B is tight between trip R1-2 and R2-1. There is no time between
     *         the arrival and departure, and it is only possible to transfer if the transfer is
     *         stay-seated or guaranteed. For other types of constrained transfers we should board
     *         the next trip 'R2-2'.
     *     </li>
     *     <li>
     *         The transfer at stop C allow regular transfers between trip R1-2 and R2-1.
     *     </li>
     *     <li>
     *         R1-1 is the fallback in the reverse search in the same way as R2-2 is the fallback
     *         int the forward search.
     *     </li>
     * </ul>
     * The
     *
     */
    @BeforeEach
    void setup() {
        route1 = new TestRouteData(
                "R1", TransitMode.RAIL,
                List.of(STOP_A, STOP_B, STOP_C),
                "10:00 10:10 10:20",
                "10:05 10:15 10:25"
        );

        route2 = new TestRouteData(
                "R2", TransitMode.BUS,
                List.of(STOP_B, STOP_C, STOP_D),
                "10:15 10:30 10:40",
                "10:20 10:35 10:45"
        );

        this.pattern1 = route1.getRaptorTripPattern();
        this.pattern2 = route2.getRaptorTripPattern();
        this.stopIndex = new StopIndexForRaptor(
                List.of(RAPTOR_STOP_INDEX),
                TransitTuningParameters.FOR_TEST
        );
    }

    @Test
    void transferExist() {
        int fromStopPos = route1.stopPosition(STOP_C);
        int toStopPos = route2.stopPosition(STOP_C);

        var txAllowed = new ConstrainedTransfer(
                ID, STOP_C_TX_POINT, STOP_C_TX_POINT, GUARANTEED_CONSTRAINT
        );
        generateTransfersForPatterns(List.of(txAllowed));

        // Forward
        var subject = route2.getRaptorTripPattern().constrainedTransferForwardSearch();
        assertTrue(subject.transferExist(toStopPos));

        // Reverse
        subject = route1.getRaptorTripPattern().constrainedTransferReverseSearch();
        assertTrue(subject.transferExist(fromStopPos));
    }

    @Test
    void findGuaranteedTransferWithZeroConnectionTimeWithStation() {
        var txGuaranteedTrip2Trip = new ConstrainedTransfer(
                ID, STATION_B_TX_POINT, STATION_B_TX_POINT, GUARANTEED_CONSTRAINT
        );
        findGuaranteedTransferWithZeroConnectionTime(List.of(txGuaranteedTrip2Trip));
    }

    @Test
    void findGuaranteedTransferWithZeroConnectionTimeWithStop() {
        var txGuaranteedTrip2Trip = new ConstrainedTransfer(
                ID, STOP_B_TX_POINT, STOP_B_TX_POINT, GUARANTEED_CONSTRAINT
        );
        findGuaranteedTransferWithZeroConnectionTime(List.of(txGuaranteedTrip2Trip));
    }

    @Test
    void findGuaranteedTransferWithZeroConnectionTimeWithRouteTransfers() {
        int sourceStopPos = route1.stopPosition(STOP_B);
        int targetStopPos = route2.stopPosition(STOP_B);
        var route1TxPoint = new RouteTransferPoint(route1.getRoute(), sourceStopPos);
        var route2TxPoint = new RouteTransferPoint(route2.getRoute(), targetStopPos);

        var txGuaranteedTrip2Trip = new ConstrainedTransfer(
                ID, route1TxPoint, route2TxPoint, GUARANTEED_CONSTRAINT
        );
        findGuaranteedTransferWithZeroConnectionTime(List.of(txGuaranteedTrip2Trip));
    }

    @Test
    void findGuaranteedTransferWithZeroConnectionTimeWithTripTransfers() {
        int sourceStopPos = route1.stopPosition(STOP_B);
        int targetStopPos = route2.stopPosition(STOP_B);
        var trip1TxPoint = new TripTransferPoint(route1.lastTrip().trip(), sourceStopPos);
        var trip2TxPoint = new TripTransferPoint(route2.firstTrip().trip(), targetStopPos);

        var txGuaranteedTrip2Trip = new ConstrainedTransfer(
                ID, trip1TxPoint, trip2TxPoint, GUARANTEED_CONSTRAINT
        );
        findGuaranteedTransferWithZeroConnectionTime(List.of(txGuaranteedTrip2Trip));
    }

    @Test
    void findGuaranteedTransferWithMostSpecificTransfers() {
        int sourceStopPos = route1.stopPosition(STOP_B);
        int targetStopPos = route2.stopPosition(STOP_B);
        var trip1TxPoint = new TripTransferPoint(route1.lastTrip().trip(), sourceStopPos);
        var route1TxPoint = new RouteTransferPoint(route1.getRoute(), sourceStopPos);
        var trip2TxPoint = new TripTransferPoint(route2.firstTrip().trip(), targetStopPos);


        var transfers =  List.of(
                new ConstrainedTransfer(ID, STOP_B_TX_POINT, trip2TxPoint, NOT_ALLOWED_CONSTRAINT),
                new ConstrainedTransfer(ID, trip1TxPoint, STOP_B_TX_POINT, GUARANTEED_CONSTRAINT),
                new ConstrainedTransfer(ID, route1TxPoint, STOP_B_TX_POINT, NOT_ALLOWED_CONSTRAINT)
        );
        findGuaranteedTransferWithZeroConnectionTime(transfers);
    }

    @Test
    void findNextTransferWhenFirstTransferIsNotAllowed() {
        int sourceStopPos = route1.stopPosition(STOP_C);
        int targetStopPos = route2.stopPosition(STOP_C);
        var trip1TxPoint = new TripTransferPoint(route1.lastTrip().trip(), sourceStopPos);
        var trip2TxPoint = new TripTransferPoint(route2.firstTrip().trip(), targetStopPos);

        var txNotAllowed = new ConstrainedTransfer(
                ID, trip1TxPoint, trip2TxPoint, NOT_ALLOWED_CONSTRAINT
        );

        testTransferSearch(
                STOP_C, List.of(txNotAllowed), TRIP_2_INDEX, TRIP_1_INDEX, REGULAR_TRANSFER
        );
    }

    @Test
    void blockTransferWhenNotAllowedApplyToAllTrips() {
        ConstrainedTransfer transfer = new ConstrainedTransfer(
                ID, STOP_C_TX_POINT, STOP_C_TX_POINT, NOT_ALLOWED_CONSTRAINT
        );
        testTransferSearch(
                STOP_C, List.of(transfer), TRIP_1_INDEX, TRIP_2_INDEX, NOT_ALLOWED_CONSTRAINT
        );
    }

    /**
     * The most specific transfer passed in should be a guaranteed transfer
     * at stop B
     */
    private void findGuaranteedTransferWithZeroConnectionTime(
            List<ConstrainedTransfer> constrainedTransfers
    ) {
        testTransferSearch(
                STOP_B, constrainedTransfers, TRIP_1_INDEX, TRIP_2_INDEX, GUARANTEED_CONSTRAINT
        );
    }

    void testTransferSearch(
            Stop transferStop,
            List<ConstrainedTransfer> constraints,
            int expTripIndexFwdSearch,
            int expTripIndexRevSearch,
            TransferConstraint expConstraint
    ) {
        testTransferSearchForward(transferStop, constraints, expTripIndexFwdSearch, expConstraint);
        testTransferSearchReverse(transferStop, constraints, expTripIndexRevSearch, expConstraint);
    }

    void testTransferSearchForward(
            Stop transferStop,
            List<ConstrainedTransfer> txList,
            int expectedTripIndex,
            TransferConstraint expectedConstraint
    ) {
        generateTransfersForPatterns(txList);
        var subject = pattern2.constrainedTransferForwardSearch();

        int targetStopPos = route2.stopPosition(transferStop);
        int stopIndex = stopIndex(transferStop);
        int sourceArrivalTime = route1.lastTrip().getStopTime(transferStop).getArrivalTime();

        // Check that transfer exist
        assertTrue(subject.transferExist(targetStopPos));

        var boarding = subject.find(
                route2.getTimetable(),
                route1.lastTrip().getTripSchedule(),
                stopIndex,
                sourceArrivalTime
        );

        assertNotNull(boarding);
        assertEquals(expectedConstraint, boarding.getTransferConstraint());
        assertEquals(stopIndex , boarding.getBoardStopIndex());
        assertEquals(targetStopPos, boarding.getStopPositionInPattern());
        assertEquals(expectedTripIndex, boarding.getTripIndex());
    }

    void testTransferSearchReverse(
            Stop transferStop,
            List<ConstrainedTransfer> txList,
            int expectedTripIndex,
            TransferConstraint expectedConstraint
    ) {
        generateTransfersForPatterns(txList);
        var subject = pattern1.constrainedTransferReverseSearch();
        int targetStopPos = route1.stopPosition(transferStop);

        int stopIndex = stopIndex(transferStop);
        int sourceArrivalTime = route2.firstTrip().getStopTime(transferStop).getDepartureTime();

        // Check that transfer exist
        assertTrue(subject.transferExist(targetStopPos));

        var boarding = subject.find(
                route1.getTimetable(),
                route2.firstTrip().getTripSchedule(),
                stopIndex,
                sourceArrivalTime
        );

        assertNotNull(boarding);
        assertEquals(expectedConstraint, boarding.getTransferConstraint());
        assertEquals(stopIndex , boarding.getBoardStopIndex());
        assertEquals(targetStopPos, boarding.getStopPositionInPattern());
        assertEquals(expectedTripIndex, boarding.getTripIndex());
    }

    private void generateTransfersForPatterns(Collection<ConstrainedTransfer> txList) {
        new TransferIndexGenerator(txList, List.of(pattern1, pattern2), stopIndex)
                .generateTransfers();
    }
}
