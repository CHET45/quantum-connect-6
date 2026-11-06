package ch.qc.tools;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * How far a bot sees forced results, measured without any evaluation: for each of its moves a full-width search looks
 * for a proven win or loss. Wins: does the bot's move keep a proven win, by distance. Blunders: the root is not proven
 * lost, yet after the bot's move the opponent has a proven win, by distance. Comparing bots on the same board tells
 * whose horizon is further.
 *
 * <pre>SaviTact games.jsonl --sizes 15x11 --bots REGEX --nodes 100000 [--stride 1] [--threads 2]</pre>
 */
public final class SaviTact {
  private static final int COOLDOWN = 10;

  private record Pos(Geometry geo, byte[] cells, int stm, int cdS, int cdO, int turn, int col, boolean blast, String bot) {}

  private SaviTact() {}

  public static void main(String[] args) throws Exception {
    String botsRe = "Savi8|Savi9|beater";
    String sizes = "15x11";
    long nodes = 100000;
    int stride = 1;
    int threads = 2;
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--bots" -> botsRe = args[++i];
        case "--sizes" -> sizes = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--stride" -> stride = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException(args[i]);
      }
    }
    Pattern bots = Pattern.compile(botsRe);
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
      if (check.problem != null) {
        continue;
      }
      collect(geo, g, check.plies, bots, stride, all);
    }
    System.out.printf(Locale.ROOT, "%d positions, %d nodes per search%n", all.size(), nodes);
    Searcher.Options options = SearchOptions.parse("lmr=0,wd=1,blastGenDepth=1");

    Map<String, long[]> tally = new TreeMap<>();
    int[] next = {0};
    Thread[] workers = new Thread[threads];
    long budget = nodes;
    for (int t = 0; t < threads; t++) {
      workers[t] = new Thread(() -> {
        java.util.Map<Geometry, Searcher> engines = new java.util.HashMap<>();
        Map<String, long[]> local = new TreeMap<>();
        long far = Long.MAX_VALUE / 4;
        while (true) {
          int idx;
          synchronized (next) {
            idx = next[0]++;
          }
          if (idx >= all.size()) {
            break;
          }
          Pos p = all.get(idx);
          Searcher s = engines.computeIfAbsent(p.geo, g -> new Searcher(g, new ch.qc.bot.eval.HandEval(g), new TranspositionTable(20), options, new Vcf.Table(16)));
          Board root = new Board(p.geo);
          root.load(p.cells);
          bump(local, p.bot + " | moves", 0);
          Searcher.Result r = s.run(root, p.stm, p.cdS, p.cdO, p.turn, ch.qc.bot.Clock.nanos(), far, budget);
          int score = r.score();
          boolean rootWin = score > Searcher.MATE_BOUND;
          boolean rootLoss = score < -Searcher.MATE_BOUND;
          if (rootLoss) {
            bump(local, p.bot + " | root lost", 0);
            continue;
          }
          // the position after the bot's own move
          Board after = new Board(p.geo);
          after.load(p.cells);
          boolean won = p.blast ? after.blast(p.col) : after.make(p.col, p.stm);
          int childScore;
          if (won) {
            childScore = -Searcher.MATE + 1;
          } else {
            childScore = s.run(after, 3 - p.stm, p.cdO, p.blast ? 1 : p.cdS + 1, p.turn + 1, ch.qc.bot.Clock.nanos(), far, budget).score();
          }
          if (rootWin) {
            int d = Searcher.MATE - score;
            boolean kept = childScore < -Searcher.MATE_BOUND;
            bump(local, p.bot + " | win D=" + two(d), kept ? 1 : 0);
          } else if (childScore > Searcher.MATE_BOUND) {
            int d = Searcher.MATE - childScore;
            bump(local, p.bot + " | blunder D=" + two(d), 1);
          }
        }
        synchronized (tally) {
          local.forEach((k, v) -> {
            long[] x = tally.computeIfAbsent(k, y -> new long[2]);
            x[0] += v[0];
            x[1] += v[1];
          });
        }
      });
      workers[t].start();
    }
    for (Thread t : workers) {
      t.join();
    }
    tally.forEach((k, v) -> System.out.printf(Locale.ROOT, "%-46s %6d of %6d  %5.1f%%%n", k, v[0], v[1], 100.0 * v[0] / Math.max(1, v[1])));
  }

  private static String two(int d) {
    return d < 10 ? "0" + d : "" + d;
  }

  private static void bump(Map<String, long[]> m, String key, int hit) {
    long[] x = m.computeIfAbsent(key, y -> new long[2]);
    x[0] += hit;
    x[1]++;
  }

  private static void collect(Geometry geo, ServerGames.Game game, int total, Pattern bots, int stride, List<Pos> out) {
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
      if (bots.matcher(game.name()[side - 1]).find() && ply % stride == 0) {
        out.add(new Pos(geo, b.cells().clone(), side, counter[side], counter[3 - side], turn, col, star, game.name()[side - 1]));
      }
      boolean win;
      if (star) {
        win = b.blast(col);
        counter[side] = 0;
      } else {
        win = b.make(col, side);
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
}
