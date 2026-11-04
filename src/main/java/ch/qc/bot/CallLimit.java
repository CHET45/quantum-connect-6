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
  /** Above the prior: a sample must be this far above it, and within this share of the first sample. */
  private static final long CLEARLY_ABOVE_NANOS = 130 * MS;
  private static final int AGREE_PERCENT = 15;
  /** Limits a server would plausibly set; a measurement just above one of them is that limit plus some delay. */
  private static final long[] ROUND_MS = {1, 2, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 300, 500, 750, 1000,
      1500, 2000};

  /** Measurements below the prior, whose median counts: a stall lengthens one, a late thread start shortens it. */
  private final long[] samples = new long[3];
  private int count;
  private volatile long nanos = PRIOR_NANOS;
  /** Measured or assumed to hold, so no probe is due any more. */
  private volatile boolean known;
  /** Samples for a limit above the prior; the question is closed for this game once they disagree or a turn is lost. */
  private final long[] upSamples = new long[3];
  private int upCount;
  private boolean upClosed;

  long nanos() {
    return nanos;
  }

  boolean known() {
    return known;
  }

  /**
   * The prior stands: the probe saw no timeout up to the point where a timeout would mean a limit below the prior. That
   * also overrules an earlier short measurement, which then came from a thread that started very late.
   */
  void assumePrior() {
    nanos = PRIOR_NANOS;
    known = true;
  }

  /** The engine gave up on a call after this long, which is below where the probe for a shorter limit ends. */
  void measured(long took) {
    samples[count++] = took;
    nanos = round(median(samples, count));
    known = count == samples.length;
  }

  private static long median(long[] s, int n) {
    if (n == 1) {
      return s[0];
    }
    if (n == 2) {
      return Math.min(s[0], s[1]);
    }
    return Math.max(Math.min(s[0], s[1]), Math.min(Math.max(s[0], s[1]), s[2]));
  }

  /**
   * True while a limit above the prior may still be proven. Such a limit is hypothetical and a lost turn is real, and a
   * stall makes a measurement late, so only three samples that agree and are clearly above the prior raise it.
   */
  boolean upwardOpen() {
    return !upClosed;
  }

  /** The engine gave up on a call after this long, or the probe for a longer limit ended there. */
  void upwardSample(long took) {
    if (upClosed) {
      return;
    }
    if (took < CLEARLY_ABOVE_NANOS || (upCount > 0 && Math.abs(took - upSamples[0]) > upSamples[0] / 100 * AGREE_PERCENT)) {
      upClosed = true;
      return;
    }
    upSamples[upCount++] = took;
    if (upCount == upSamples.length) {
      nanos = round(median(upSamples, upCount));
      upClosed = true;
    }
  }

  /** A turn was lost: stay at or back to the prior for the rest of this game. */
  void closeUpward() {
    upClosed = true;
    if (nanos > PRIOR_NANOS) {
      nanos = PRIOR_NANOS;
    }
  }

  /**
   * The round limit a measurement most likely means: a stall lengthens it a lot, a thread that started late shortens it
   * a little. Far above any round limit it is taken just below what was measured.
   */
  static long round(long took) {
    for (int i = ROUND_MS.length - 1; i >= 0; i--) {
      long r = ROUND_MS[i] * MS;
      if (took >= r - r / 20) {
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
