package ch.qc.bot.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Suite T7 of design 5.4: the exact threat search never claims a win that is not there. Short proofs are checked
 * by the exhaustive solver of {@link MateSuiteTest}; long proofs by an independent checker in which the defender
 * tries every legal move (all drops, all blasts including the no-op blast on a full column) instead of the
 * replies Vcf enumerates. Size with -Pqc.vcf.positions=N.
 */
class VcfTest {
  private static final int POSITIONS = Integer.getInteger("qc.vcf.positions", 100_000);
  private static final long NO_LIMIT = Long.MAX_VALUE / 4;

  record Pos(Board b, int stm, int cdStm, int cdOpp, int turn) {}

  /** Random mid-game positions with random cooldown counters; a third of them with a threat for the side to move. */
  static List<Pos> positions(int n, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    List<Pos> out = new ArrayList<>();
    int[][] sizes = {{8, 12}, {8, 12}, {8, 12}, {6, 7}, {9, 9}, {10, 14}};
    while (out.size() < n) {
      int[] size = sizes[rnd.nextInt(sizes.length)];
      Geometry g = new Geometry(size[0], size[1]);
      Board b = new Board(g);
      int tower = rnd.nextInt(g.cols);
      for (int ply = 0; ply < 2 * g.cells && out.size() < n; ply++) {
        int me = 1 + (ply & 1);
        if (ply >= 6 && rnd.nextInt(3) == 0) {
          int[] cds = {0, 5, 9, 10};
          Board copy = new Board(g);
          copy.copyFrom(b);
          out.add(new Pos(copy, me, cds[rnd.nextInt(4)], cds[rnd.nextInt(4)], ply));
        }
        int c = rnd.nextInt(3) == 0 ? tower : rnd.nextInt(g.cols);
        boolean win;
        if (rnd.nextInt(12) == 0) {
          win = b.blast(c);
        } else if (b.isFull(c)) {
          continue;
        } else {
          win = b.make(c, me);
        }
        if (win || b.topRowFull()) {
          break;
        }
      }
    }
    return out;
  }

  @Test
  void shortProofsAgreeWithTheSolver() {
    Vcf.Table table = new Vcf.Table(18);
    int claims = 0;
    int solverWins = 0;
    for (Pos p : positions(POSITIONS, 71)) {
      Vcf v = new Vcf(p.b.g, table, true);
      int move = v.attack(p.b, p.stm, p.cdStm, p.cdOpp, 1, p.turn, Long.MAX_VALUE, NO_LIMIT);
      boolean solver = MateSuiteTest.wins(p.b, p.stm, p.cdStm, p.cdOpp, 3);
      if (move != Tactics.NONE) {
        claims++;
        assertTrue(solver, "false proof at " + describe(p));
        assertTrue(firstMoveWins(p, move, 3), "proof move does not win at " + describe(p));
      }
      if (solver) {
        solverWins++;
      }
    }
    System.out.printf("T7 short: %d positions, %d VCF proofs, %d solver wins within 3 plies%n", POSITIONS, claims,
        solverWins);
    assertTrue(claims > 100, "too few proofs to be meaningful");
  }

  @Test
  void longProofsSurviveEveryDefence() {
    Vcf.Table table = new Vcf.Table(18);
    int claims = 0;
    int deep = 0;
    int n = POSITIONS / 2;
    for (Pos p : positions(n, 72)) {
      Vcf v = new Vcf(p.b.g, table, true);
      int move = v.attack(p.b, p.stm, p.cdStm, p.cdOpp, 5, p.turn, Long.MAX_VALUE, 200_000);
      if (move == Tactics.NONE) {
        continue;
      }
      claims++;
      int moves = 0;
      while (moves <= 5 && !Checker.wins(p.b, p.stm, p.cdStm, p.cdOpp, moves, p.turn)) {
        moves++;
      }
      assertTrue(moves <= 5, "false proof at " + describe(p));
      if (moves >= 2) {
        deep++;
      }
    }
    System.out.printf("T7 long: %d positions, %d VCF proofs, %d need two or more forcing drops%n", n, claims, deep);
    assertTrue(deep > 20, "too few deep proofs to be meaningful");
  }

  @Test
  void resultsDoNotDependOnTheTable() {
    Vcf.Table shared = new Vcf.Table(16);
    for (Pos p : positions(5_000, 73)) {
      int a = new Vcf(p.b.g, shared, true).attack(p.b, p.stm, p.cdStm, p.cdOpp, 3, p.turn, Long.MAX_VALUE, NO_LIMIT);
      int b = new Vcf(p.b.g, new Vcf.Table(10), true)
          .attack(p.b, p.stm, p.cdStm, p.cdOpp, 3, p.turn, Long.MAX_VALUE, NO_LIMIT);
      assertEquals(a == Tactics.NONE, b == Tactics.NONE, "table changed the verdict at " + describe(p));
    }
  }

  private static boolean firstMoveWins(Pos p, int move, int plies) {
    Board a = new Board(p.b.g);
    a.copyFrom(p.b);
    int c = Tactics.column(move);
    boolean special = Tactics.isSpecial(move);
    if (a.isFull(c) || (special && p.cdStm < 10)) {
      return false;
    }
    if (special ? a.blast(c) : a.make(c, p.stm)) {
      return true;
    }
    return !a.topRowFull()
        && MateSuiteTest.opponentLosesAll(a, p.stm, special ? 1 : Math.min(10, p.cdStm + 1), p.cdOpp, plies - 1);
  }

  private static String describe(Pos p) {
    return p.b.g.rows + "x" + p.b.g.cols + " stm=" + p.stm + " cd=" + p.cdStm + "/" + p.cdOpp + " cells="
        + java.util.Arrays.toString(p.b.cells());
  }

  /**
   * Independent proof checker: the attacker may use any drop that leaves it a playable threat (written separately
   * from Vcf), the defender tries every legal move. True if the attacker wins with at most d forcing drops.
   */
  static final class Checker {
    static boolean wins(Board b, int a, int cdA, int cdD, int d, int turn) {
      if (turn >= 1000) {
        return false;
      }
      if (MateSuiteTest.immediate(b, a, cdA)) {
        return true;
      }
      if (d == 0) {
        return false;
      }
      Geometry g = b.g;
      Board x = new Board(g);
      for (int c = 0; c < g.cols; c++) {
        if (b.isFull(c)) {
          continue;
        }
        x.copyFrom(b);
        if (x.make(c, a)) {
          return true;
        }
        if (x.topRowFull() || !hasPlayableThreat(x, a)) {
          continue;
        }
        if (everyDefenceLoses(x, a, Math.min(10, cdA + 1), cdD, d - 1, turn + 1)) {
          return true;
        }
      }
      return false;
    }

    static boolean everyDefenceLoses(Board p, int a, int cdA, int cdD, int d, int turn) {
      if (turn >= 1000) {
        return false;
      }
      int def = 3 - a;
      Geometry g = p.g;
      Board r = new Board(g);
      boolean any = false;
      for (int m = 0; m < 2 * g.cols; m++) {
        int c = m % g.cols;
        boolean special = m >= g.cols;
        if (special && cdD < 10) {
          continue;
        }
        if (!special && p.isFull(c)) {
          continue;
        }
        any = true;
        r.copyFrom(p);
        boolean defWins = special ? r.blast(c) : r.make(c, def);
        if (defWins || r.topRowFull()) {
          return false;
        }
        if (!wins(r, a, cdA, special ? 1 : Math.min(10, cdD + 1), d, turn + 1)) {
          return false;
        }
      }
      return any;
    }

    private static boolean hasPlayableThreat(Board b, int a) {
      for (int c = 0; c < b.g.cols; c++) {
        if (!b.isFull(c) && b.threats(a, b.g.index(c, b.height(c))) > 0) {
          return true;
        }
      }
      return false;
    }
  }
}
