package ch.qc.bot.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.rules.NaiveRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Tactical suites T1, T3, T4 and T5 of design 5.4, graded by the naive engine copy (independent of Board): random
 * positions with random special readiness for both sides, every move and every opponent reply tried by brute force.
 * Required of the root tactics: play a winning move when one exists; else a normal move after which the opponent
 * cannot win at once; else such a blast; never a blast otherwise. The search may also choose a safe blast when
 * safe normal moves exist (it sees further). Size with -Pqc.suite.positions=N.
 */
class TacticsSuiteTest {
  private static final int POSITIONS = Integer.getInteger("qc.suite.positions", 6000);
  private static final long SEARCH_NANOS = 5_000_000L;

  interface Chooser {
    int choose(Geometry g, Board b, int me, boolean mySp, boolean oppSp, int ply);
  }

  /** Brute-force grading of one position for the side to move. */
  record Grade(List<Integer> wins, List<Integer> safeNormal, List<Integer> safeBlast, boolean oppThreat,
      boolean ponsTrap, boolean blastDanger, boolean blastWinOnly) {}

  static Grade grade(NaiveRules n, int me, boolean mySp, boolean oppSp) {
    int opp = 3 - me;
    List<Integer> wins = new ArrayList<>();
    List<Integer> safeNormal = new ArrayList<>();
    List<Integer> safeBlast = new ArrayList<>();
    boolean normalWin = false;
    boolean ponsTrap = false;
    boolean blastDanger = false;
    for (int c = 0; c < n.cols; c++) {
      if (n.isFull(c)) {
        continue;
      }
      NaiveRules a = n.copy();
      a.drop(c, me);
      if (a.hasWinner()) {
        wins.add(c);
        normalWin = true;
        continue;
      }
      int how = oppWins(a, opp, oppSp);
      if (how == 0) {
        safeNormal.add(c);
      } else if (how == 1 && !dropWins(n, opp)) {
        ponsTrap = true;
      } else if (how == 2) {
        blastDanger = true;
      }
    }
    boolean blastWin = false;
    if (mySp) {
      for (int c = 0; c < n.cols; c++) {
        if (n.isFull(c)) {
          continue;
        }
        NaiveRules a = n.copy();
        a.special(c, me);
        if (a.hasWinner()) {
          wins.add(c | Tactics.SPECIAL);
          blastWin = true;
        } else if (oppWins(a, opp, oppSp) == 0) {
          safeBlast.add(c | Tactics.SPECIAL);
        }
      }
    }
    boolean oppThreat = dropWins(n, opp);
    return new Grade(wins, safeNormal, safeBlast, oppThreat, ponsTrap, blastDanger && !safeNormal.isEmpty(),
        blastWin && !normalWin);
  }

  /** 0: no immediate win for who; 1: by a drop; 2: only by a blast. */
  private static int oppWins(NaiveRules n, int who, boolean special) {
    if (dropWins(n, who)) {
      return 1;
    }
    if (special) {
      for (int c = 0; c < n.cols; c++) {
        if (!n.isFull(c)) {
          NaiveRules a = n.copy();
          a.special(c, who);
          if (a.hasWinner()) {
            return 2;
          }
        }
      }
    }
    return 0;
  }

  private static boolean dropWins(NaiveRules n, int who) {
    for (int c = 0; c < n.cols; c++) {
      if (!n.isFull(c)) {
        NaiveRules a = n.copy();
        a.drop(c, who);
        if (a.hasWinner()) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void rootTactics() {
    runSuite((g, b, me, mySp, oppSp, ply) -> new Tactics(g).choose(b, me, mySp ? 10 : 0, oppSp ? 10 : 0, Long.MAX_VALUE),
        true, POSITIONS, 511, "root tactics", 50);
  }

  @Test
  void search() {
    TranspositionTable tt = new TranspositionTable(16);
    runSuite((g, b, me, mySp, oppSp, ply) -> {
      Board copy = new Board(g);
      copy.copyFrom(b);
      return new Searcher(g, new HandEval(g), tt).run(copy, me, mySp ? 10 : 0, oppSp ? 10 : 0, ply,
          System.nanoTime(), SEARCH_NANOS).move();
    }, false, POSITIONS, 512, "search 5 ms", 20);
  }

  private void runSuite(Chooser chooser, boolean strict, int positions, long seed, String name, int minPerTag) {
    SplittableRandom rnd = new SplittableRandom(seed);
    Map<String, int[]> tally = new TreeMap<>(); // tag -> {positions, correct}
    List<String> failures = new ArrayList<>();
    int kept = 0;
    int negatives = 0;
    while (kept < positions) {
      int[][] sizes = {{8, 12}, {8, 12}, {8, 12}, {6, 7}, {10, 14}, {7, 9}};
      int[] size = sizes[rnd.nextInt(sizes.length)];
      Geometry g = new Geometry(size[0], size[1]);
      Board b = new Board(g);
      Board scratch = new Board(g);
      int tower = rnd.nextInt(g.cols);
      for (int ply = 0; ply < 3 * g.cells && kept < positions; ply++) {
        int me = 1 + (ply & 1);
        boolean mySp = rnd.nextBoolean();
        boolean oppSp = rnd.nextBoolean();
        if (interesting(b, scratch, g, me, mySp, oppSp) || rnd.nextInt(30) == 0) {
          NaiveRules n = NaiveRules.fromCells(g.rows, g.cols, 6, b.cells());
          Grade gr = grade(n, me, mySp, oppSp);
          int move = chooser.choose(g, b, me, mySp, oppSp, ply);
          kept++;
          check(gr, move, strict, tally, failures, g, b, me, mySp, oppSp);
          if (gr.wins.isEmpty() && mySp) {
            negatives++;
          }
        }
        int c = rnd.nextInt(3) == 0 ? tower : rnd.nextInt(g.cols);
        boolean win;
        if (rnd.nextInt(8) == 0) {
          win = b.blast(c);
        } else {
          if (b.isFull(c)) {
            continue;
          }
          win = b.make(c, me);
        }
        if (win || b.topRowFull()) {
          break;
        }
      }
    }
    StringBuilder sb = new StringBuilder(name + " suites (" + kept + " positions, " + negatives + " blast-negative):");
    tally.forEach((k, v) -> sb.append(String.format(" %s %d/%d;", k, v[1], v[0])));
    System.out.println(sb);
    assertEquals(List.of(), failures.subList(0, Math.min(5, failures.size())), failures.size() + " failures");
    for (String tag : List.of("T1 win in 1", "T1 forced block", "T1 under threat cell", "T3 blast win",
        "T3 negative", "T5 blast danger")) {
      assertTrue(tally.containsKey(tag) && tally.get(tag)[0] >= minPerTag, "too few positions for " + tag);
    }
  }

  private static void check(Grade gr, int move, boolean strict, Map<String, int[]> tally, List<String> failures,
      Geometry g, Board b, int me, boolean mySp, boolean oppSp) {
    boolean ok;
    String rule;
    if (!gr.wins.isEmpty()) {
      ok = gr.wins.contains(move);
      rule = "must win";
    } else if (!gr.safeNormal.isEmpty()) {
      ok = gr.safeNormal.contains(move) || (!strict && gr.safeBlast.contains(move));
      rule = strict ? "must play a safe normal move" : "must play a safe move";
    } else if (!gr.safeBlast.isEmpty()) {
      ok = gr.safeBlast.contains(move);
      rule = "must play a safe blast";
    } else {
      ok = move != Tactics.NONE && !b.isFull(Tactics.column(move)) && (!Tactics.isSpecial(move) || mySp);
      rule = "lost position, any legal move";
    }
    List<String> tags = new ArrayList<>();
    if (!gr.wins.isEmpty() && !gr.blastWinOnly) {
      tags.add("T1 win in 1");
    }
    if (gr.wins.isEmpty() && gr.oppThreat) {
      tags.add("T1 forced block");
    }
    if (gr.wins.isEmpty() && gr.ponsTrap) {
      tags.add("T1 under threat cell");
    }
    if (gr.blastWinOnly) {
      tags.add("T3 blast win");
    }
    if (mySp && gr.wins.isEmpty()) {
      tags.add("T3 negative");
    }
    if (gr.wins.isEmpty() && gr.blastDanger) {
      tags.add("T5 blast danger");
    }
    if (gr.wins.isEmpty() && gr.safeNormal.isEmpty() && !gr.safeBlast.isEmpty()) {
      tags.add("T4 only a blast saves");
    }
    if (tags.isEmpty()) {
      tags.add("quiet");
    }
    for (String tag : tags) {
      int[] v = tally.computeIfAbsent(tag, x -> new int[2]);
      v[0]++;
      if (ok) {
        v[1]++;
      }
    }
    if (!ok) {
      failures.add(String.format("%dx%d me=%d mySp=%b oppSp=%b move=%s: %s (wins %s, safe %s, safe blasts %s) cells %s",
          g.rows, g.cols, me, mySp, oppSp, fmt(move), rule, gr.wins, gr.safeNormal, gr.safeBlast,
          java.util.Arrays.toString(b.cells())));
    }
  }

  private static String fmt(int move) {
    return move == Tactics.NONE ? "none" : Tactics.column(move) + (Tactics.isSpecial(move) ? "*" : "");
  }

  /** Cheap Board-side filter that keeps positions where some tactic can matter. */
  private static boolean interesting(Board b, Board scratch, Geometry g, int me, boolean mySp, boolean oppSp) {
    for (int c = 0; c < g.cols; c++) {
      if (b.isFull(c)) {
        continue;
      }
      int top = g.index(c, b.height(c));
      if (b.threats(1, top) > 0 || b.threats(2, top) > 0) {
        return true;
      }
      if (b.height(c) + 1 < g.rows && (b.threats(1, top + 1) > 0 || b.threats(2, top + 1) > 0)) {
        return true;
      }
      if ((mySp || oppSp) && b.blastMayWin(c)) {
        scratch.copyFrom(b);
        if (scratch.blast(c)) {
          return true;
        }
      }
    }
    return false;
  }
}
