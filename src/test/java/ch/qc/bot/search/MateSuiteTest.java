package ch.qc.bot.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Suite T2 of design 5.4: positions where the side to move forces a win in 3 or 5 plies (double and stacked
 * threats, also through blasts) but not in 1, proven by an exhaustive full-width solver on Board (which the
 * conformance and differential tests tie to the engine). The search must find a first move that keeps a forced
 * win (within the same plies, or a longer one confirmed by the independent proof checker of {@link VcfTest}) within
 * 80 ms in at least 80 percent of the positions. Size with -Pqc.mate.positions=N.
 */
class MateSuiteTest {
  private static final int POSITIONS = Integer.getInteger("qc.mate.positions", 300);
  private static final long BUDGET = 80_000_000L;

  /** Can colour stm, to move, force a line within the given odd number of plies? */
  static boolean wins(Board b, int stm, int cdStm, int cdOpp, int plies) {
    if (immediate(b, stm, cdStm)) {
      return true;
    }
    if (plies < 3) {
      return false;
    }
    Geometry g = b.g;
    Board a = new Board(g);
    for (int m = 0; m < 2 * g.cols; m++) {
      int c = m % g.cols;
      boolean special = m >= g.cols;
      if (b.isFull(c) || (special && cdStm < 10)) {
        continue;
      }
      a.copyFrom(b);
      if (special) {
        a.blast(c);
      } else {
        a.make(c, stm);
      }
      if (!a.topRowFull() && opponentLosesAll(a, stm, special ? 1 : Math.min(10, cdStm + 1), cdOpp, plies - 1)) {
        return true;
      }
    }
    return false;
  }

  /** After stm's move (opponent to move): every opponent reply leaves stm a forced win in plies - 1. */
  static boolean opponentLosesAll(Board a, int stm, int cdStm, int cdOpp, int plies) {
    int opp = 3 - stm;
    if (immediate(a, opp, cdOpp)) {
      return false;
    }
    Geometry g = a.g;
    Board r = new Board(g);
    boolean any = false;
    for (int m = 0; m < 2 * g.cols; m++) {
      int c = m % g.cols;
      boolean special = m >= g.cols;
      if (a.isFull(c) || (special && cdOpp < 10)) {
        continue;
      }
      any = true;
      r.copyFrom(a);
      if (special) {
        r.blast(c);
      } else {
        r.make(c, opp);
      }
      if (r.topRowFull() || !wins(r, stm, cdStm, special ? 1 : Math.min(10, cdOpp + 1), plies - 1)) {
        return false;
      }
    }
    return any;
  }

  static boolean immediate(Board b, int stm, int cd) {
    Geometry g = b.g;
    for (int c = 0; c < g.cols; c++) {
      if (!b.isFull(c) && b.threats(stm, g.index(c, b.height(c))) > 0) {
        return true;
      }
    }
    if (cd >= 10) {
      Board p = new Board(g);
      for (int c = 0; c < g.cols; c++) {
        if (b.blastMayWin(c)) {
          p.copyFrom(b);
          if (p.blast(c)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Test
  void forcedWinsInThreeAndFive() {
    SplittableRandom rnd = new SplittableRandom(777);
    TranspositionTable tt = new TranspositionTable(18);
    int[] found = new int[6];
    int[] total = new int[6];
    long maxNanos = 0;
    while (total[3] + total[5] < POSITIONS) {
      Geometry g = new Geometry(8, 12);
      Board b = new Board(g);
      int tower = rnd.nextInt(g.cols);
      // specials are rare in the suite: the exhaustive solver is 24-wide when both are ready
      boolean specials = rnd.nextInt(4) == 0;
      for (int ply = 0; ply < 80; ply++) {
        int me = 1 + (ply & 1);
        int cdMe = specials ? 10 : 0;
        int cdOpp = specials && rnd.nextBoolean() ? 10 : 0;
        if (ply >= 8 && !immediate(b, me, cdMe) && !immediate(b, 3 - me, cdOpp)) {
          int plies = wins(b, me, cdMe, cdOpp, 3) ? 3 : total[5] < POSITIONS / 2 && wins(b, me, cdMe, cdOpp, 5) ? 5 : 0;
          if (plies > 0) {
            total[plies]++;
            Board copy = new Board(g);
            copy.copyFrom(b);
            long t0 = System.nanoTime();
            int move = new Searcher(g, new HandEval(g), tt).run(copy, me, cdMe, cdOpp, ply, t0, BUDGET).move();
            maxNanos = Math.max(maxNanos, System.nanoTime() - t0);
            if (keepsWin(b, me, cdMe, cdOpp, move, plies)) {
              found[plies]++;
            }
          }
        }
        int c = rnd.nextInt(3) == 0 ? tower : rnd.nextInt(g.cols);
        if (b.isFull(c)) {
          continue;
        }
        if (b.make(c, me) || b.topRowFull()) {
          break;
        }
      }
    }
    double rate = (found[3] + found[5]) / (double) (total[3] + total[5]);
    System.out.printf("T2: win in 3 found %d/%d, win in 5 found %d/%d, overall %.1f%%, max search %.1f ms%n",
        found[3], total[3], found[5], total[5], 100 * rate, maxNanos / 1e6);
    assertTrue(total[5] > 0 && total[3] > 0, "suite generation produced no positions of one kind");
    assertTrue(rate >= 0.8, "T2 solve rate " + rate);
  }

  private static boolean keepsWin(Board b, int me, int cdMe, int cdOpp, int move, int plies) {
    if (move == Tactics.NONE) {
      return false;
    }
    Board a = new Board(b.g);
    a.copyFrom(b);
    int c = Tactics.column(move);
    boolean special = Tactics.isSpecial(move);
    if (a.isFull(c) || (special && cdMe < 10)) {
      return false;
    }
    if (special ? a.blast(c) : a.make(c, me)) {
      return true;
    }
    int cdNext = special ? 1 : Math.min(10, cdMe + 1);
    // a longer forced win (the root threat search returns the first proof it finds) also counts
    return !a.topRowFull() && (opponentLosesAll(a, me, cdNext, cdOpp, plies - 1)
        || VcfTest.Checker.everyDefenceLoses(a, me, cdNext, cdOpp, 5, 1));
  }
}
