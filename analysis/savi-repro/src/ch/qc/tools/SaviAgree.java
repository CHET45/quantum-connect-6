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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Share of a bot's real server moves that a fixed-node search plays too, by evaluation, node budget and game phase.
 * Moves forced by a one-move win or a single block are counted apart, so they do not inflate the figure.
 *
 * <pre>
 * SaviAgree games.jsonl --bots REGEX --sizes 15x11 --evals hand,v=generic-vwd.qcw --nodes 3000,30000
 *     [--spec lmr=0] [--stride 1] [--min-ply 4] [--mod 5 --rem 0] [--threads 2]
 * </pre>
 */
public final class SaviAgree {
  private static final int COOLDOWN = 10;

  private record Pos(Geometry geo, byte[] cells, int stm, int cdS, int cdO, int turn, int col, boolean blast, int ply,
      int total, String bot, String gameKey) {}

  private SaviAgree() {}

  public static void main(String[] args) throws Exception {
    String botsRe = "Savi";
    String excludeRe = "beater";
    String sizes = null;
    String evals = "hand";
    String nodesArg = "3000";
    String spec = "";
    int stride = 1;
    int minPly = 4;
    int mod = 0;
    int rem = 0;
    int threads = 2;
    boolean useBook = false;
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--book" -> useBook = true;
        case "--bots" -> botsRe = args[++i];
        case "--exclude" -> excludeRe = args[++i];
        case "--sizes" -> sizes = args[++i];
        case "--evals" -> evals = args[++i];
        case "--nodes" -> nodesArg = args[++i];
        case "--spec" -> spec = args[++i];
        case "--stride" -> stride = Integer.parseInt(args[++i]);
        case "--min-ply" -> minPly = Integer.parseInt(args[++i]);
        case "--mod" -> mod = Integer.parseInt(args[++i]);
        case "--rem" -> rem = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
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
      if (g.rows() <= 0 || g.cols() <= 0) {
        continue;
      }
      String size = g.rows() + "x" + g.cols();
      if (sizes != null && !List.of(sizes.split(",")).contains(size)) {
        continue;
      }
      Geometry geo = ServerGames.geometry(g.rows(), g.cols());
      ServerGames.Replay check = ServerGames.replay(geo, g, false);
      if (check.problem != null) {
        continue;
      }
      if (mod > 0 && Math.floorMod(g.id().hashCode(), mod) != rem) {
        if (useBook) {
          List<Pos> train = new ArrayList<>();
          collect(geo, g, check.plies, bots, exclude, 1, 0, train);
          for (Pos p : train) {
            bookAdd(p);
          }
        }
        continue;
      }
      collect(geo, g, check.plies, bots, exclude, stride, minPly, all);
    }
    System.out.printf(Locale.ROOT, "%d positions%n", all.size());

    Searcher.Options options = spec.isEmpty() ? Searcher.Options.DEFAULT : SearchOptions.parse(spec);
    for (String evalArg : evals.split(",")) {
      String[] kv = evalArg.split("=", 2);
      String label = kv[0];
      String file = kv.length > 1 ? kv[1] : kv[0];
      Function<Geometry, Evaluator> factory = Evaluators.factory(file);
      for (String n : nodesArg.split(",")) {
        run(all, label, factory, Long.parseLong(n), options, threads);
      }
    }
  }

  private static void collect(Geometry geo, ServerGames.Game game, int total, Pattern bots, Pattern exclude, int stride,
      int minPly, List<Pos> out) {
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
      if (bots.matcher(name).find() && !exclude.matcher(name).find() && ply >= minPly && ply % stride == 0) {
        out.add(new Pos(geo, b.cells().clone(), side, counter[side], counter[3 - side], turn, col, star, ply, total, name,
            game.id()));
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

  /** 0 = free choice, 1 = a one-move win is on the board, 2 = a single block is forced. */
  private static int forcedKind(Board b, int stm, int cdS) {
    int opp = 3 - stm;
    int blocks = 0;
    for (int c = 0; c < b.g.cols; c++) {
      if (b.isFull(c)) {
        continue;
      }
      int cell = c * b.g.rows + b.height(c);
      if (b.threats(stm, cell) > 0) {
        return 1;
      }
      if (b.threats(opp, cell) > 0) {
        blocks++;
      }
    }
    if (cdS >= COOLDOWN && b.anyBlastWins()) {
      return 1;
    }
    return blocks > 0 ? 2 : 0;
  }

  private static void run(List<Pos> all, String label, Function<Geometry, Evaluator> factory, long nodes,
      Searcher.Options options, int threads) throws InterruptedException {
    Map<String, long[]> tally = new TreeMap<>();
    int[] next = {0};
    Thread[] workers = new Thread[threads];
    long t0 = ch.qc.bot.Clock.nanos();
    for (int w = 0; w < threads; w++) {
      workers[w] = new Thread(() -> {
        Map<Geometry, Searcher> engines = new LinkedHashMap<>();
        Map<Geometry, Board> boards = new LinkedHashMap<>();
        Map<String, long[]> local = new TreeMap<>();
        while (true) {
          int idx;
          synchronized (next) {
            idx = next[0]++;
          }
          if (idx >= all.size()) {
            break;
          }
          Pos p = all.get(idx);
          Searcher engine = engines.computeIfAbsent(p.geo, g -> new Searcher(g, factory.apply(g), new TranspositionTable(18), options, new Vcf.Table(16)));
          Board b = boards.computeIfAbsent(p.geo, Board::new);
          b.load(p.cells);
          int kind = forcedKind(b, p.stm, p.cdS);
          int move = engine.run(b, p.stm, p.cdS, p.cdO, p.turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, nodes).move();
          boolean special = Tactics.isSpecial(move) && p.cdS >= COOLDOWN;
          boolean same = Tactics.column(move) == p.col && special == p.blast;
          int[] known = bookMove(p);
          if (known != null) {
            same = known[0] == p.col && (known[1] == 1) == p.blast;
            add(local, p.geo.rows + "x" + p.geo.cols + " ALL | book hits", same);
          }
          int phase = Math.min(3, 4 * p.ply / Math.max(1, p.total));
          String size = p.geo.rows + "x" + p.geo.cols;
          String[] kinds = {"free", "win", "block"};
          add(local, size + " " + p.bot + " | " + kinds[kind], same);
          add(local, size + " " + p.bot + " | all", same);
          add(local, size + " ALL | " + kinds[kind], same);
          add(local, size + " ALL | all", same);
          if (kind == 0) {
            add(local, size + " ALL | free phase " + phase, same);
            if (p.cdS >= COOLDOWN) {
              add(local, size + " ALL | ready: bot " + (p.blast ? "blast" : "drop") + ", engine " + (special ? "blast" : "drop"), same);
            }
          }
        }
        synchronized (tally) {
          local.forEach((k, v) -> {
            long[] t = tally.computeIfAbsent(k, x -> new long[2]);
            t[0] += v[0];
            t[1] += v[1];
          });
        }
      });
      workers[w].start();
    }
    for (Thread t : workers) {
      t.join();
    }
    System.out.printf(Locale.ROOT, "%n== eval %s, %d nodes, %.0f s%n", label, nodes, (ch.qc.bot.Clock.nanos() - t0) / 1e9);
    tally.forEach((k, v) -> System.out.printf(Locale.ROOT, "%-58s %5d / %5d  %5.1f%%%n", k, v[0], v[1], 100.0 * v[0] / Math.max(1, v[1])));
    System.out.flush();
  }

  private static final Map<String, Map<Integer, Integer>> BOOK = new java.util.HashMap<>();

  private static String key(Pos p, boolean mirror) {
    int rows = p.geo.rows;
    int cols = p.geo.cols;
    StringBuilder sb = new StringBuilder(rows * cols + 4);
    for (int c = 0; c < cols; c++) {
      int src = mirror ? cols - 1 - c : c;
      for (int y = 0; y < rows; y++) {
        sb.append((char) ('0' + p.cells[src * rows + y]));
      }
    }
    return sb.append(p.stm).append(p.cdS >= COOLDOWN ? 'r' : 'n').toString();
  }

  /** Reply under the canonical (mirror-folded) position: col * 2 + blast. */
  private static void bookAdd(Pos p) {
    String a = key(p, false);
    String b = key(p, true);
    boolean mirror = b.compareTo(a) < 0;
    int col = mirror ? p.geo.cols - 1 - p.col : p.col;
    BOOK.computeIfAbsent(mirror ? b : a, x -> new java.util.HashMap<>()).merge(col * 2 + (p.blast ? 1 : 0), 1, Integer::sum);
  }

  /** The most frequent known reply as {column, blast}, or null. */
  private static int[] bookMove(Pos p) {
    if (BOOK.isEmpty()) {
      return null;
    }
    String a = key(p, false);
    String b = key(p, true);
    boolean mirror = b.compareTo(a) < 0;
    Map<Integer, Integer> m = BOOK.get(mirror ? b : a);
    if (m == null) {
      return null;
    }
    int best = -1;
    int count = 0;
    for (Map.Entry<Integer, Integer> e : m.entrySet()) {
      if (e.getValue() > count) {
        best = e.getKey();
        count = e.getValue();
      }
    }
    int col = best / 2;
    return new int[] {mirror ? p.geo.cols - 1 - col : col, best & 1};
  }

  private static void add(Map<String, long[]> m, String key, boolean same) {
    long[] t = m.computeIfAbsent(key, x -> new long[2]);
    t[0] += same ? 1 : 0;
    t[1]++;
  }
}
