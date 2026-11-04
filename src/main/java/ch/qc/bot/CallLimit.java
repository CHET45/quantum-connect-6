package ch.qc.bot;

/**
 * The time limit of one engine call, as far as the bot knows it. The bot cannot read it (the engine's constant is
 * inlined at compile time, and reflection is forbidden), so it assumes {@link #PRIOR_NANOS} until it has measured
 * otherwise. Every budget of the bot is written as its value at the prior limit and comes through {@link #scaled}.
 *
 * <p>What makes a call late does not grow with the limit: a thread start, a lost scheduler slice, a late clock poll. So
 * a budget keeps its distance from the limit in absolute time rather than as a fraction, with a floor for short limits,
 * where the distance would leave nothing, and a slow growth for long ones.
 */
final class CallLimit {
  static final long PRIOR_NANOS = 100_000_000L;
  private static final long MS = 1_000_000L;
  /** Limits a server would plausibly set; a measurement just above one of them is that limit plus some delay. */
  private static final long[] ROUND_MS = {1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 300, 500, 750, 1000,
      1500, 2000};

  private volatile long nanos = PRIOR_NANOS;
  /** Measured below the prior, or measured or assumed to hold, so no probe is due any more. */
  private volatile boolean known;

  long nanos() {
    return nanos;
  }

  boolean known() {
    return known;
  }

  /** The prior stands: the probe saw no timeout up to the point where a timeout would mean a limit below the prior. */
  void assumePrior() {
    known = true;
  }

  /** The engine gave up on a call after this long. */
  void measured(long took) {
    nanos = round(took);
    known = true;
  }

  /** A call ran this long without the engine giving up. */
  void atLeast(long took) {
    if (took > nanos) {
      nanos = took;
    }
    known = true;
  }

  /** Back to the prior after a lost turn with a raised limit: the raise may have come from a late measurement. */
  void distrustRaise() {
    if (nanos > PRIOR_NANOS) {
      nanos = PRIOR_NANOS;
    }
  }

  /** The largest round limit at most a measurement that delays can only lengthen, or just below it if none is near. */
  static long round(long took) {
    for (int i = ROUND_MS.length - 1; i >= 0; i--) {
      long r = ROUND_MS[i] * MS;
      if (took >= r) {
        return took < r / 10 * 13 ? r : took / 10 * 9;
      }
    }
    return Math.max(MS, took);
  }

  /** A budget given by its value at the prior limit, at the current limit, at least floorPercent of the limit. */
  long scaled(long atPrior, int floorPercent) {
    return scaled(atPrior, floorPercent, nanos);
  }

  static long scaled(long atPrior, int floorPercent, long limit) {
    long margin = PRIOR_NANOS - atPrior;
    long distance = Math.max(margin, margin / 4 * (limit / PRIOR_NANOS) + margin / 4 * (limit % PRIOR_NANOS) / PRIOR_NANOS);
    return Math.max(limit / 100 * floorPercent, limit - distance);
  }
}
