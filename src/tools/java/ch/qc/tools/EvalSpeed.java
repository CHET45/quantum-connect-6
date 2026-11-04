package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Nodes per second of a search with each evaluation on the same positions of one board size, in alternating rounds on
 * one thread so that load changes hit all of them alike.
 *
 * <pre>
 * EvalSpeed --rows R --cols C --weights hand --weights a.qcw [--positions 60] [--nodes 20000] [--rounds 3]
 * </pre>
 */
public final class EvalSpeed {
  private EvalSpeed() {}

  private record Pos(Board board, int stm, int cdStm, int cdOpp, int turn) {}

  public static void main(String[] args) throws Exception {
    int rows = 15;
    int cols = 11;
    int positions = 60;
    long nodes = 20_000;
    int rounds = 3;
    List<String> weights = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--rows" -> rows = Integer.parseInt(args[++i]);
        case "--cols" -> cols = Integer.parseInt(args[++i]);
        case "--weights" -> weights.add(args[++i]);
        case "--positions" -> positions = Integer.parseInt(args[++i]);
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--rounds" -> rounds = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Geometry g = new Geometry(rows, cols);
    List<Pos> list = sample(g, positions);
    List<Evaluator> evals = new ArrayList<>();
    for (String w : weights) {
      evals.add(Evaluators.factory(w).apply(g));
    }
    long[][] total = new long[evals.size()][2];
    for (int r = 0; r < rounds + 1; r++) {
      for (int e = 0; e < evals.size(); e++) {
        Searcher s = new Searcher(g, evals.get(e), new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16));
        long sumNodes = 0;
        long sumNanos = 0;
        for (Pos p : list) {
          Board view = new Board(g);
          view.copyFrom(p.board());
          long t0 = System.nanoTime();
          Searcher.Result res = s.run(view, p.stm(), p.cdStm(), p.cdOpp(), p.turn(), ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, nodes);
          sumNanos += System.nanoTime() - t0;
          sumNodes += res.nodes();
        }
        // the first round warms the JIT and does not count
        if (r > 0) {
          total[e][0] += sumNodes;
          total[e][1] += sumNanos;
        }
      }
    }
    double base = 0;
    for (int e = 0; e < evals.size(); e++) {
      double nps = total[e][0] / (total[e][1] / 1e9);
      if (e == 0) {
        base = nps;
      }
      System.out.printf(Locale.ROOT, "%dx%d %-40s %8.0f nodes/s  x%.2f%n", rows, cols, shortName(weights.get(e)), nps, nps / base);
    }
  }

  private static String shortName(String w) {
    return w.length() <= 40 ? w : w.substring(w.length() - 40);
  }

  /** Positions from quick games of the first plies up to about half the board. */
  private static List<Pos> sample(Geometry g, int count) {
    List<Pos> out = new ArrayList<>();
    SplittableRandom rnd = new SplittableRandom(7);
    Evaluator hand = Evaluators.factory("hand").apply(g);
    Searcher s = new Searcher(g, hand, new TranspositionTable(16), Searcher.Options.DEFAULT, new Vcf.Table(14));
    while (out.size() < count) {
      Board b = new Board(g);
      int[] counter = new int[3];
      int stm = 1;
      int turn = 0;
      int limit = 6 + rnd.nextInt(Math.max(1, g.cells / 2));
      boolean over = false;
      while (!over && turn < limit) {
        Board view = new Board(g);
        view.copyFrom(b);
        int move = turn < 6 ? rnd.nextInt(g.cols) : s.run(view, stm, counter[stm], counter[3 - stm], turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, 300).move();
        if (move == Tactics.NONE) {
          over = true;
          break;
        }
        int col = Tactics.column(move);
        if (b.isFull(col)) {
          over = true;
          break;
        }
        boolean special = Tactics.isSpecial(move) && counter[stm] >= 10;
        boolean win = special ? b.blast(col) : b.make(col, stm);
        counter[stm] = special ? 1 : counter[stm] + 1;
        turn++;
        stm = 3 - stm;
        over = win;
      }
      if (!over) {
        Board copy = new Board(g);
        copy.copyFrom(b);
        out.add(new Pos(copy, stm, counter[stm], counter[3 - stm], turn));
      }
    }
    return out;
  }
}
