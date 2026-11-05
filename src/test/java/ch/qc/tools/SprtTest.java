package ch.qc.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Validation of the pentanomial SPRT by simulation (design 5.3, M4 definition of done). */
class SprtTest {
  private static final int RUNS = Integer.getInteger("qc.sprt.runs", 400);
  /** The design's production cap; the validation itself runs uncapped so it tests the SPRT, not the cap. */
  private static final int CAP = 4_000;
  private static final int UNCAPPED = 50_000;
  private static final double DRAWS = 0.4;

  /** Runs one SPRT on simulated pairs of independent games at the given true Elo; returns the verdict. */
  private static Sprt.Verdict simulate(double trueElo, double elo0, double elo1, int cap, SplittableRandom rnd) {
    double s = Sprt.score(trueElo);
    double win = s - DRAWS / 2;
    Sprt sprt = new Sprt(elo0, elo1);
    for (int pair = 0; pair < cap; pair++) {
      int half = 0;
      for (int g = 0; g < 2; g++) {
        double u = rnd.nextDouble();
        half += u < win ? 2 : u < win + DRAWS ? 1 : 0;
      }
      sprt.add(half);
      Sprt.Verdict v = sprt.verdict();
      if (v != Sprt.Verdict.CONTINUE) {
        return v;
      }
    }
    return Sprt.Verdict.H0; // no verdict at the cap counts as a rejection
  }

  private static double rateH1(double trueElo, int cap, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    int h1 = 0;
    for (int i = 0; i < RUNS; i++) {
      if (simulate(trueElo, 0, 10, cap, rnd) == Sprt.Verdict.H1) {
        h1++;
      }
    }
    return h1 / (double) RUNS;
  }

  @Test
  void errorRatesMatchTheBounds() {
    double atElo0 = rateH1(0, UNCAPPED, 1);
    double atElo1 = rateH1(10, UNCAPPED, 2);
    double atMinus5 = rateH1(-5, UNCAPPED, 3);
    double atPlus20 = rateH1(20, UNCAPPED, 4);
    double cappedElo1 = rateH1(10, CAP, 5);
    System.out.printf(java.util.Locale.ROOT,
        "SPRT [0, 10], %d runs each: P(H1) at -5 Elo %.3f, at 0 %.3f, at 10 %.3f, at 20 %.3f;"
            + " at 10 with the %d-pair cap %.3f%n",
        RUNS, atMinus5, atElo0, atElo1, atPlus20, CAP, cappedElo1);
    assertTrue(atElo0 <= 0.08, "false positive rate at elo0: " + atElo0);
    assertTrue(atElo1 >= 0.88, "power at elo1: " + atElo1);
    assertTrue(atMinus5 < atElo0 && atPlus20 > atElo1, "operating characteristic is not monotone");
  }

  @Test
  void llrByHand() {
    Sprt s = new Sprt(0, 10);
    // 10 pairs: 2 x WW, 5 x one win one draw-equivalent, 3 x LL style split
    for (int i = 0; i < 2; i++) {
      s.add(4);
    }
    for (int i = 0; i < 5; i++) {
      s.add(2);
    }
    for (int i = 0; i < 3; i++) {
      s.add(1);
    }
    double mu = (2 * 1.0 + 5 * 0.5 + 3 * 0.25) / 10;
    double var = (2 * Math.pow(1 - mu, 2) + 5 * Math.pow(0.5 - mu, 2) + 3 * Math.pow(0.25 - mu, 2)) / 10;
    double s0 = 0.5;
    double s1 = 1 / (1 + Math.pow(10, -10 / 400.0));
    assertEquals(10 * (s1 - s0) * (2 * mu - s0 - s1) / (2 * var), s.llr(), 1e-12);
    assertEquals(Math.log(0.95 / 0.05), s.upperBound(), 1e-12);
  }
}
