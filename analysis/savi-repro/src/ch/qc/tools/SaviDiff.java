package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * What a bot prefers when it disagrees with a search: for each free position where the search plays something else,
 * the difference of simple features of the two positions after the moves (the bot's move minus the search's move),
 * from the mover's view. Mean, t statistic and the share with the sign of the mean, largest |t| first.
 *
 * <pre>SaviDiff games.jsonl --bots REGEX --exclude REGEX --sizes 15x11 --eval hand --nodes 150000 --spec lmr=0 --threads 2</pre>
 */
public final class SaviDiff {
  private static final int COOLDOWN = 10;

  private record Pos(Geometry geo, byte[] cells, int stm, int cdS, int cdO, int turn, int col, boolean blast) {}

  private SaviDiff() {}

  public static void main(String[] args) throws Exception {
    String botsRe = "Savi8|Savi9";
    String excludeRe = "beater";
    String sizes = "15x11";
    String evalName = "hand";
    long nodes = 150000;
    String spec = "lmr=0";
    int threads = 2;
    int top = 25;
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--bots" -> botsRe = args[++i];
        case "--exclude" -> excludeRe = args[++i];
        case "--sizes" -> sizes = args[++i];
        case "--eval" -> evalName = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--spec" -> spec = args[++i];
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--top" -> top = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException(args[i]);
      }
    }
    Pattern bots = Pattern.compile(botsRe);
    Pattern exclude = Pattern.compile(excludeRe);
    List<Pos> all = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      ServerGames.Game g = ServerGames.game(line);
      if (g.rows() <= 0 || !List.of(sizes.split(",")).contains(g.rows() + "x" + g.cols())) {
        continue;
      }
      Geometry geo = ServerGames.geometry(g.rows(), g.cols());
      ServerGames.Replay check = ServerGames.replay(geo, g, false);
      if (check.problem == null) {
        collect(geo, g, check.plies, bots, exclude, all);
      }
    }
    Searcher.Options options = SearchOptions.parse(spec);
    Function<Geometry, Evaluator> factory = Evaluators.factory(evalName);
    int n = SaviEval.N;
    double[] sum = new double[n];
    double[] sq = new double[n];
    double[] plus = new double[n];
    double[] nz = new double[n];
    long[] counts = new long[2];
    int[] next = {0};
    long budget = nodes;
    Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      workers[t] = new Thread(() -> {
        java.util.Map<Geometry, Searcher> engines = new java.util.HashMap<>();
        java.util.Map<Geometry, SaviEval> evs = new java.util.HashMap<>();
        float[] fa = new float[n];
        float[] fb = new float[n];
        while (true) {
          int idx;
          synchronized (next) {
            idx = next[0]++;
          }
          if (idx >= all.size()) {
            return;
          }
          Pos p = all.get(idx);
          Searcher s = engines.computeIfAbsent(p.geo, g -> new Searcher(g, factory.apply(g), new TranspositionTable(18), options, new Vcf.Table(16)));
          SaviEval ev = evs.computeIfAbsent(p.geo, g -> new SaviEval(g, new ch.qc.bot.eval.HandEval(g), new double[n]));
          Board b = new Board(p.geo);
          b.load(p.cells);
          int move = s.run(b, p.stm, p.cdS, p.cdO, p.turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, budget).move();
          boolean special = Tactics.isSpecial(move) && p.cdS >= COOLDOWN;
          int col = Tactics.column(move);
          boolean same = col == p.col && special == p.blast;
          synchronized (counts) {
            counts[0]++;
            counts[1] += same ? 0 : 1;
          }
          if (same || col < 0) {
            continue;
          }
          if (!after(p, p.col, p.blast, ev, fa) || !after(p, col, special, ev, fb)) {
            continue;
          }
          synchronized (sum) {
            for (int i = 0; i < n; i++) {
              double d = fa[i] - fb[i];
              sum[i] += d;
              sq[i] += d * d;
              plus[i] += d > 0 ? 1 : 0;
              nz[i] += d != 0 ? 1 : 0;
            }
          }
        }
      });
      workers[t].start();
    }
    for (Thread t : workers) {
      t.join();
    }
    long m = counts[1];
    System.out.printf(Locale.ROOT, "%d free positions, search differs in %d (%.1f%%)%n", counts[0], m, 100.0 * m / Math.max(1, counts[0]));
    double[] tv = new double[n];
    Integer[] order = new Integer[n];
    for (int i = 0; i < n; i++) {
      order[i] = i;
      double mean = sum[i] / Math.max(1, m);
      double var = sq[i] / Math.max(1, m) - mean * mean;
      tv[i] = var <= 0 ? 0 : mean / Math.sqrt(var / Math.max(1, m));
    }
    java.util.Arrays.sort(order, (x, y) -> Double.compare(Math.abs(tv[y]), Math.abs(tv[x])));
    System.out.println("   mean        t   same sign  nonzero  feature (from the mover's view: bot's move minus search's move)");
    for (int k = 0; k < top; k++) {
      int i = order[k];
      double mean = sum[i] / Math.max(1, m);
      double sign = mean >= 0 ? plus[i] : nz[i] - plus[i];
      System.out.printf(Locale.ROOT, "%7.3f %8.2f   %5.1f%%    %5.1f%%   %s%n", mean, tv[i], 100 * sign / Math.max(1, nz[i]), 100 * nz[i] / Math.max(1, m), SaviEval.name(i));
    }
  }

  /** Features of the position after the move, from the mover's view. */
  private static boolean after(Pos p, int col, boolean blast, SaviEval ev, float[] out) {
    Board b = new Board(p.geo);
    b.load(p.cells);
    if (col < 0 || col >= p.geo.cols || b.isFull(col)) {
      return false;
    }
    if (blast ? b.blast(col) : b.make(col, p.stm)) {
      return false;
    }
    // the side to move is now the opponent, so "own" in the feature vector means the opponent: swap to the mover's view
    float[] tmp = new float[SaviEval.N];
    ev.features(b, 3 - p.stm, p.cdO, blast ? 1 : p.cdS + 1, tmp);
    SaviEval.moverView(tmp, out);
    return true;
  }

  private static void collect(Geometry geo, ServerGames.Game game, int total, Pattern bots, Pattern exclude, List<Pos> out) {
    Board b = new Board(geo);
    int[] counter = new int[3];
    String s = game.moves();
    int expected = 1;
    int turn = 0;
    int ply = 0;
    int i = 0;
    while (i < s.length()) {
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 36);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      if (side != expected) {
        counter[expected]++;
        turn++;
        expected = side;
      }
      String name = game.name()[side - 1];
      if (bots.matcher(name).find() && !exclude.matcher(name).find() && ply >= 4 && free(b, side, counter[side])) {
        out.add(new Pos(geo, b.cells().clone(), side, counter[side], counter[3 - side], turn, col, star));
      }
      boolean win = star ? b.blast(col) : b.make(col, side);
      if (star) {
        counter[side] = 0;
      }
      counter[side]++;
      turn++;
      ply++;
      expected = 3 - side;
      if (win || ply >= total) {
        return;
      }
    }
  }

  private static boolean free(Board b, int stm, int cdS) {
    for (int c = 0; c < b.g.cols; c++) {
      if (!b.isFull(c)) {
        int cell = c * b.g.rows + b.height(c);
        if (b.threats(stm, cell) > 0 || b.threats(3 - stm, cell) > 0) {
          return false;
        }
      }
    }
    return !(cdS >= COOLDOWN && b.anyBlastWins());
  }
}
