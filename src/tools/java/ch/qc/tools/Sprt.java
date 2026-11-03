package ch.qc.tools;

/**
 * Pentanomial GSPRT over colour-swapped game pairs (design 5.3). Each pair scores x in {0, 0.25, 0.5, 0.75, 1} (the
 * mean of its two game scores); with N pairs, mean mu and variance sigma2 of x:
 *
 * <pre>
 * LLR = N (s(elo1) - s(elo0)) (2 mu - s(elo0) - s(elo1)) / (2 sigma2),   s(e) = 1 / (1 + 10^(-e / 400))
 * </pre>
 *
 * H1 is accepted at LLR >= ln((1 - beta) / alpha), H0 at LLR <= ln(beta / (1 - alpha)). No verdict is given before
 * {@link #MIN_PAIRS} pairs: with a handful of pairs the sample variance is near zero and the normal approximation
 * crosses a bound by chance (simulated false-positive rate 34 percent without the minimum).
 */
public final class Sprt {
  public enum Verdict { CONTINUE, H0, H1 }

  public static final int MIN_PAIRS = 50;

  private final double elo0;
  private final double elo1;
  private final double lower;
  private final double upper;
  /** counts[k]: pairs with total score k / 2 over two games, k = 0..4 (0, 0.5, 1, 1.5, 2 points). */
  private final long[] counts = new long[5];

  public Sprt(double elo0, double elo1, double alpha, double beta) {
    this.elo0 = elo0;
    this.elo1 = elo1;
    this.lower = Math.log(beta / (1 - alpha));
    this.upper = Math.log((1 - beta) / alpha);
  }

  public Sprt(double elo0, double elo1) {
    this(elo0, elo1, 0.05, 0.05);
  }

  /** Adds one pair; halfPoints is the pair's total in half points, 0 (two losses) to 4 (two wins). */
  public void add(int halfPoints) {
    counts[halfPoints]++;
  }

  public long pairs() {
    long n = 0;
    for (long c : counts) {
      n += c;
    }
    return n;
  }

  public long[] counts() {
    return counts.clone();
  }

  public double mean() {
    long n = pairs();
    if (n == 0) {
      return 0.5;
    }
    double sum = 0;
    for (int k = 0; k < 5; k++) {
      sum += counts[k] * (k / 4.0);
    }
    return sum / n;
  }

  public double variance() {
    long n = pairs();
    if (n < 2) {
      return 0;
    }
    double mu = mean();
    double ss = 0;
    for (int k = 0; k < 5; k++) {
      double d = k / 4.0 - mu;
      ss += counts[k] * d * d;
    }
    return ss / n;
  }

  public double llr() {
    long n = pairs();
    // a floor on the variance keeps tiny or one-sided samples from producing huge LLRs
    double var = Math.max(variance(), 1e-3);
    double s0 = score(elo0);
    double s1 = score(elo1);
    return n * (s1 - s0) * (2 * mean() - s0 - s1) / (2 * var);
  }

  public Verdict verdict() {
    if (pairs() < MIN_PAIRS) {
      return Verdict.CONTINUE;
    }
    double l = llr();
    return l >= upper ? Verdict.H1 : l <= lower ? Verdict.H0 : Verdict.CONTINUE;
  }

  public double lowerBound() {
    return lower;
  }

  public double upperBound() {
    return upper;
  }

  /** Elo estimate and 95 percent interval {low, estimate, high} from the pair scores. */
  public double[] eloInterval() {
    long n = pairs();
    double mu = mean();
    double half = n == 0 ? 0.5 : 1.96 * Math.sqrt(variance() / n);
    return new double[] {elo(mu - half), elo(mu), elo(mu + half)};
  }

  public String summary() {
    double[] e = eloInterval();
    long[] c = counts;
    return String.format(java.util.Locale.ROOT,
        "pairs %d [LL %d, LD %d, DD/WL %d, WD %d, WW %d], score %.1f%%, Elo %+.1f [%+.1f, %+.1f], "
            + "LLR %.2f (%.2f, %.2f) for [%.0f, %.0f]: %s",
        pairs(), c[0], c[1], c[2], c[3], c[4], 100 * mean(), e[1], e[0], e[2], llr(), lower, upper, elo0, elo1,
        verdict());
  }

  public static double score(double elo) {
    return 1 / (1 + Math.pow(10, -elo / 400));
  }

  public static double elo(double score) {
    double s = Math.min(1 - 1e-9, Math.max(1e-9, score));
    return -400 * Math.log10(1 / s - 1);
  }
}
