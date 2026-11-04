package ch.qc.bot;

/**
 * The bot's only clock. Since 2026-11-04 the organisers allow System.nanoTime, so time is read from it: nanosecond
 * resolution and no allocation per poll. This is the only class that may touch java.lang.System.
 */
public final class Clock {
  private Clock() {}

  public static long nanos() {
    return System.nanoTime();
  }
}
