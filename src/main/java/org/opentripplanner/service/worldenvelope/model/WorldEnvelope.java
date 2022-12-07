package org.opentripplanner.service.worldenvelope.model;

import java.io.Serializable;
import java.util.Optional;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.tostring.ToStringBuilder;

/**
 * This class calculates borders of envelopes that can be also on 180th meridian The same way as it
 * was previously calculated in GraphMetadata constructor
 */
public class WorldEnvelope implements Serializable {

  private final WgsCoordinate lowerLeft;
  private final WgsCoordinate upperRight;
  private final WgsCoordinate meanCenter;
  private final WgsCoordinate transitMedianCenter;

  WorldEnvelope(
    double lowerLeftLatitude,
    double lowerLeftLongitude,
    double upperRightLatitude,
    double upperRightLongitude,
    WgsCoordinate transitMedianCenter
  ) {
    this.transitMedianCenter = transitMedianCenter;
    this.lowerLeft = new WgsCoordinate(lowerLeftLatitude, lowerLeftLongitude);
    this.upperRight = new WgsCoordinate(upperRightLatitude, upperRightLongitude);
    this.meanCenter = calculateMeanCenter(lowerLeft, upperRight);
  }

  public static WorldEnvelopeBuilder of() {
    return new WorldEnvelopeBuilder();
  }

  public WgsCoordinate lowerLeft() {
    return lowerLeft;
  }

  public WgsCoordinate upperRight() {
    return upperRight;
  }

  /**
   * This is the center of the Envelope including both street vertexes and transit stops
   * if they exist.
   */
  public WgsCoordinate meanCenter() {
    return meanCenter;
  }

  /**
   * If transit data exist, then this is the median center of the transit stops. The median
   * is computed independently for the longitude and latitude.
   * <p>
   * If not transit data exist this return `empty`.
   */
  public WgsCoordinate center() {
    return transitMedianCenter().orElse(meanCenter);
  }

  /**
   * Return the transit median center [if it exist] or the mean center.
   */
  public Optional<WgsCoordinate> transitMedianCenter() {
    return Optional.ofNullable(transitMedianCenter);
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(WorldEnvelope.class)
      .addObj("lowerLeft", lowerLeft)
      .addObj("upperRight", upperRight)
      .addObj("meanCenter", meanCenter)
      .addObj("transitMedianCenter", transitMedianCenter)
      .toString();
  }

  private static WgsCoordinate calculateMeanCenter(
    WgsCoordinate lowerLeft,
    WgsCoordinate upperRight
  ) {
    var llLatitude = lowerLeft.latitude();
    var llLongitude = lowerLeft.longitude();
    var urLatitude = upperRight.latitude();
    var urLongitude = upperRight.longitude();

    double centerLatitude = llLatitude + (urLatitude - llLatitude) / 2.0;

    // Split normally at 180 degrees
    double centerLongitude = (llLongitude < urLongitude)
      ? llLongitude + (urLongitude - llLongitude) / 2.0
      : llLongitude + (360 - llLongitude + urLongitude) / 2.0;

    return new WgsCoordinate(centerLatitude, centerLongitude);
  }
}
