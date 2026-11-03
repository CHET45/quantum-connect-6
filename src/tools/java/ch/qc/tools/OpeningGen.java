package ch.qc.tools;

import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Balanced opening pool (design 5.2): 70 percent of the lines have 4-10 random normal plies, 30 percent 20-30
 * plies (both specials ready or about to be); a line is kept if no six was made, no column is full and a search
 * of the final position scores within +-150 units (0.6 logit) without a proven result. Lines are written as
 * comma-separated columns, one per line, in the order they were accepted.
 *
 * <pre>
 * --rows 8 --cols 12 --count 2000 --ms 250 --threads 6 --seed 1 --out suites/openings-8x12.txt
 * </pre>
 */
public final class OpeningGen {
  private static final int BALANCED = 150;

  public static void main(String[] args) throws Exception {
    int rows = 8;
    int cols = 12;
    int count = 2000;
    long ms = 250;
    int threads = 6;
    long seed = 1;
    String out = "suites/openings-8x12.txt";
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--rows" -> rows = Integer.parseInt(args[++i]);
        case "--cols" -> cols = Integer.parseInt(args[++i]);
        case "--count" -> count = Integer.parseInt(args[++i]);
        case "--ms" -> ms = Long.parseLong(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--out" -> out = args[++i];
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Geometry g = new Geometry(rows, cols);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    Set<String> accepted = new LinkedHashSet<>();
    int tried = 0;
    int shortLines = 0;
    long t0 = System.nanoTime();
    long nanos = ms * 1_000_000L;
    try {
      SplittableRandom root = new SplittableRandom(seed);
      while (accepted.size() < count) {
        List<Future<String>> batch = new ArrayList<>();
        for (int i = 0; i < 4 * threads; i++) {
          SplittableRandom rnd = root.split();
          batch.add(pool.submit(() -> candidate(g, rnd, nanos)));
        }
        for (Future<String> f : batch) {
          tried++;
          String line = f.get();
          if (line != null && accepted.size() < count && accepted.add(line)) {
            if (line.split(",").length <= 10) {
              shortLines++;
            }
          }
        }
      }
    } finally {
      pool.shutdownNow();
    }
    Path p = Path.of(out);
    if (p.getParent() != null) {
      Files.createDirectories(p.getParent());
    }
    try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
      w.printf(java.util.Locale.ROOT, "# %dx%d balanced openings: |score| < %d units after a %d ms search, seed %d%n",
          rows, cols, BALANCED, ms, seed);
      accepted.forEach(w::println);
    }
    System.out.printf(java.util.Locale.ROOT, "%d openings (%d short, %d long) from %d candidates in %.1f min -> %s%n",
        accepted.size(), shortLines, accepted.size() - shortLines, tried, (System.nanoTime() - t0) / 6e10, out);
  }

  /** One random line and its verdict: the line, or null if it is unbalanced or broken. */
  private static String candidate(Geometry g, SplittableRandom rnd, long nanos) {
    int plies = rnd.nextInt(10) < 7 ? 4 + rnd.nextInt(7) : 20 + rnd.nextInt(11);
    Board b = new Board(g);
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < plies; i++) {
      int c = rnd.nextInt(g.cols);
      if (b.height(c) >= g.rows - 1 || b.make(c, 1 + (i & 1))) {
        return null;
      }
      line.append(i == 0 ? "" : ",").append(c);
    }
    int stm = 1 + (plies & 1);
    // without specials during the opening each side's counter equals its number of turns
    int cdStm = plies / 2;
    int cdOpp = (plies + 1) / 2;
    Searcher s = new Searcher(g, new HandEval(g), new TranspositionTable(16));
    Searcher.Result r = s.run(b, stm, cdStm, cdOpp, plies, System.nanoTime(), nanos);
    if (r.move() == Tactics.NONE || Math.abs(r.score()) >= BALANCED) {
      return null;
    }
    return line.toString();
  }

  private OpeningGen() {}
}
