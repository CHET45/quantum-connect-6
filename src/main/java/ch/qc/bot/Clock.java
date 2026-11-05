package ch.qc.bot;

/**
 * The bot's only clock. Uploaded bots may not touch java.lang.System, so time comes from java.util.Date
 * (millisecond resolution), scaled to nanoseconds so the search budgets keep their units.
 */
public final class Clock {
  private Clock() {}

  public static long nanos() {
    return new java.util.Date().getTime() * 1_000_000L;
  }
}
