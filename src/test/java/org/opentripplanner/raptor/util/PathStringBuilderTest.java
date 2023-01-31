package org.opentripplanner.raptor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor._data.transit.TestAccessEgress;
import org.opentripplanner.raptor.api.path.PathStringBuilder;
import org.opentripplanner.raptor.api.path.RaptorStopNameResolver;

public class PathStringBuilderTest {

  private static final RaptorStopNameResolver STOP_NAME_RESOLVER = RaptorStopNameResolver.nullSafe(
    null
  );
  private static final String MODE = "BUS";
  private static final int T_10_46_05 = time(10, 46, 5);
  private static final int T_10_55 = time(10, 55, 0);
  private static final int D_5_12 = time(0, 5, 12);

  private final PathStringBuilder subject = new PathStringBuilder(STOP_NAME_RESOLVER);

  @Test
  public void walkSomeMinutesAndSeconds() {
    assertEquals("Walk 5m12s", subject.walk(D_5_12).toString());
  }

  @Test
  public void walkSomeSeconds() {
    assertEquals("Walk 17s", subject.walk(17).toString());
  }

  @Test
  public void walkThenRent() {
    assertEquals(
      "Walk 17s ~ oslo:1 Rental 2m",
      subject.walk(17).pickupRental("oslo:1", 120).toString()
    );
  }

  @Test
  public void transit() {
    assertEquals("BUS 10:46:05 10:55", subject.transit(MODE, T_10_46_05, T_10_55).toString());
  }

  @Test
  public void stop() {
    assertEquals("5000", subject.stop(5000).toString());
  }

  @Test
  public void flexZeroLength() {
    assertEquals("Flex 0s 0x", subject.flex(0, 0).toString());
  }

  @Test
  public void flexNormalCase() {
    assertEquals("Flex 5m12s 2x", subject.flex(D_5_12, 2).toString());
  }

  @Test
  public void summary() {
    int START_TIME = time(12, 35, 0);
    int END_TIME = time(13, 45, 0);
    assertEquals(
      "[12:35 13:45 1h10m 1tx $1.23]",
      subject.summary(START_TIME, END_TIME, 1, 123).toString()
    );
  }

  @Test
  public void summaryGeneralizedCostOnly() {
    assertEquals("[$0.01]", subject.summary(1).toString());
  }

  @Test
  public void path() {
    int egressDuration = 3600 + 37 * 60 + 7;
    assertEquals(
      "Walk 37s ~ 227 ~ BUS 10:46:05 10:55 ~ 112 ~ Walk 1h37m7s [10:44 12:33 1h49m 0tx $567]",
      subject
        .walk(37)
        .stop(227)
        .transit(MODE, T_10_46_05, T_10_55)
        .stop(112)
        .walk(egressDuration)
        .summary(time(10, 44, 0), time(12, 33, 0), 0, 56700)
        .toString()
    );
  }

  @Test
  public void pathWithoutAccessAndEgress() {
    assertEquals(
      "227 ~ BUS 10:46:05 10:55 ~ 112 [10:46:05 10:55 8m55s 0tx $60 3pz]",
      subject
        .accessEgress(TestAccessEgress.walk(227, 0, 0))
        .stop(227)
        .transit(MODE, T_10_46_05, T_10_55)
        .stop(112)
        .accessEgress(TestAccessEgress.walk(112, 0, 0))
        .summary(T_10_46_05, T_10_55, 0, 6000, b -> b.text("3pz"))
        .toString()
    );
  }

  /* privet methods */

  private static int time(int hour, int min, int sec) {
    return 3600 * hour + 60 * min + sec;
  }
}
