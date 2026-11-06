package ch.qc.tools;

import ch.qc.bot.eval.HandEval;
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
import java.util.regex.Pattern;

/**
 * Finds a bot's effective node budget on the server: replays its real moves and measures how many of them its own
 * engine (its jar first on the class path) repeats at each fixed node budget. The budget of the peak is the search the
 * bot really got. Uses only API present in every jar so far.
 *
 * <pre>java -cp BOT.jar;bin/all;core.jar ch.qc.tools.SaviCalib games.jsonl NAME_REGEX 15x11 1000,3000,10000 [THREADS]</pre>
 */
public final class SaviCalib {
  private static final int COOLDOWN = 10;

  private record Pos(Geometry geo, byte[] cells, int stm, int cdS, int cdO, int turn, int col, boolean blast) {}

  private SaviCalib() {}

  public static void main(String[] a) throws Exception {
    Pattern bots = Pattern.compile(a[1]);
    int threads = a.length > 4 ? Integer.parseInt(a[4]) : 2;
    List<Pos> all = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(a[0]), StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      ServerGames.Game g = ServerGames.game(line);
      if (g.rows() <= 0 || !(g.rows() + "x" + g.cols()).equals(a[2])) {
        continue;
      }
      Geometry geo = ServerGames.geometry(g.rows(), g.cols());
      ServerGames.Replay check = ServerGames.replay(geo, g, false);
      if (check.problem == null) {
        collect(geo, g, check.plies, bots, all);
      }
    }
    for (String n : a[3].split(",")) {
      // "t45" is a real 45 ms stop on this machine, a plain number a node budget
      boolean timed = n.startsWith("t");
      long nodes = timed ? Long.MAX_VALUE : Long.parseLong(n);
      long hard = timed ? Long.parseLong(n.substring(1)) * 1_000_000L : Long.MAX_VALUE / 4;
      long[] tally = new long[2];
      long[] depth = new long[2];
      int[] next = {0};
      Thread[] workers = new Thread[threads];
      long t0 = System.nanoTime();
      for (int t = 0; t < threads; t++) {
        workers[t] = new Thread(() -> {
          java.util.Map<Geometry, Searcher> engines = new java.util.HashMap<>();
          while (true) {
            int idx;
            synchronized (next) {
              idx = next[0]++;
            }
            if (idx >= all.size()) {
              return;
            }
            Pos p = all.get(idx);
            Searcher s = engines.computeIfAbsent(p.geo, g -> new Searcher(g, new HandEval(g), new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16)));
            Board b = new Board(p.geo);
            b.load(p.cells);
            Searcher.Result r = s.run(b, p.stm, p.cdS, p.cdO, p.turn, ch.qc.bot.Clock.nanos(), hard, nodes);
            int move = r.move();
            boolean special = Tactics.isSpecial(move) && p.cdS >= COOLDOWN;
            boolean same = Tactics.column(move) == p.col && special == p.blast;
            synchronized (tally) {
              tally[0] += same ? 1 : 0;
              tally[1]++;
              depth[0] += r.depth();
              depth[1] += r.nodes();
            }
          }
        });
        workers[t].start();
      }
      for (Thread t : workers) {
        t.join();
      }
      System.out.printf(Locale.ROOT, "%8s budget: %5.1f%% of %d moves, mean depth %.2f, mean nodes %.0f, %.0f s%n", n, 100.0 * tally[0] / Math.max(1, tally[1]), tally[1], (double) depth[0] / Math.max(1, tally[1]), (double) depth[1] / Math.max(1, tally[1]), (System.nanoTime() - t0) / 1e9);
    }
  }

  private static void collect(Geometry geo, ServerGames.Game game, int total, Pattern bots, List<Pos> out) {
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
      if (bots.matcher(game.name()[side - 1]).find() && ply >= 4) {
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
}
