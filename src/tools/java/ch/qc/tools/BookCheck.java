package ch.qc.tools;

import ch.qc.bot.Book;
import ch.qc.bot.Clock;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * What the book does along the server games of a target bot (default: every Savi). For each game the side playing
 * the target is followed: at each position of the target's opponent the book's column is compared with the column
 * played, up to the first position where they differ or the book has no entry. Each distinct first difference is then
 * scored by a long search of both moves and optionally by rollouts: games between two equal engines with jittered node
 * budgets from the position after each move. Also simulates the book against the target's known replies.
 *
 * <pre>
 * --book FILE --lines suites/book-lines.txt --size 15x11 --weights hand --target Savi --us beater --deep-ms 4000
 * --rollouts N (games per move, 0 = none) --rollout-nodes N --threads N --top N (how many differences get rollouts)
 * </pre>
 */
public final class BookCheck {
  private BookCheck() {}

  private record Line(String nameF, String nameS, int winner, String moves) {}

  private static final class Diff {
    int ply;
    String prefix;
    int played;
    int book;
    int games;
    int wins;
    int losses;
    final List<String> who = new ArrayList<>();
  }

  private static final long NO_TIME_LIMIT = Long.MAX_VALUE / 4;

  public static void main(String[] args) throws Exception {
    Path bookFile = null;
    Path linesFile = Path.of("suites/book-lines.txt");
    String size = "15x11";
    String weights = "hand";
    String target = "Savi";
    String ours = "beater";
    long deepMs = 4000;
    int rollouts = 0;
    long rolloutNodes = 30_000;
    int threads = 2;
    int top = 8;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--book" -> bookFile = Path.of(args[++i]);
        case "--lines" -> linesFile = Path.of(args[++i]);
        case "--size" -> size = args[++i];
        case "--weights" -> weights = args[++i];
        case "--target" -> target = args[++i];
        case "--us" -> ours = args[++i];
        case "--deep-ms" -> deepMs = Long.parseLong(args[++i]);
        case "--rollouts" -> rollouts = Integer.parseInt(args[++i]);
        case "--rollout-nodes" -> rolloutNodes = Long.parseLong(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--top" -> top = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Book book = Book.fromBytes(Files.readAllBytes(bookFile));
    String[] rc = size.split("x");
    Geometry g = new Geometry(Integer.parseInt(rc[0]), Integer.parseInt(rc[1]));
    Evaluator eval = Evaluators.factory(weights).apply(g);
    List<Line> lines = new ArrayList<>();
    for (String l : Files.readAllLines(linesFile, StandardCharsets.UTF_8)) {
      if (l.startsWith(size + " ")) {
        String[] p = l.trim().split(" ");
        if (p.length >= 6) {
          String[] names = p[4].split("\\|");
          lines.add(new Line(names[0], names[1], p[3].equals("F") ? 1 : p[3].equals("S") ? 2 : 0, p[5]));
        }
      }
    }
    System.out.printf(Locale.ROOT, "%s: %d book entries, %d server lines%n", size, book.size(g.rows, g.cols), lines.size());
    Map<String, Diff> diffs = new LinkedHashMap<>();
    int[] covered = new int[60];
    int games = 0;
    int followed = 0;
    int ended = 0;
    long depthSum = 0;
    int[] savedWl = new int[2];
    for (Line line : lines) {
      for (int targetSide = 1; targetSide <= 2; targetSide++) {
        String name = targetSide == 1 ? line.nameF : line.nameS;
        String other = targetSide == 1 ? line.nameS : line.nameF;
        if (!isTarget(name, target) || !other.contains(ours)) {
          continue;
        }
        games++;
        int us = 3 - targetSide;
        Board b = new Board(g);
        int stm = 1;
        boolean diffed = false;
        int reached = 0;
        for (int i = 0; i < line.moves.length(); i++) {
          int col = Character.digit(line.moves.charAt(i), 36);
          if (stm == us) {
            int cdOpp = (i + 1) / 2;
            int bc = book.column(b, i, us, cdOpp);
            if (bc < 0) {
              break;
            }
            reached = i + 1;
            if (bc != col) {
              Diff d = diffs.computeIfAbsent(line.moves.substring(0, i) + ":" + col, k -> new Diff());
              d.ply = i;
              d.prefix = line.moves.substring(0, i);
              d.played = col;
              d.book = bc;
              d.games++;
              d.wins += line.winner == us ? 1 : 0;
              d.losses += line.winner == targetSide ? 1 : 0;
              String who = (us == 1 ? line.nameF : line.nameS) + "-" + name;
              if (!d.who.contains(who)) {
                d.who.add(who);
              }
              diffed = true;
              break;
            }
          }
          if (b.make(col, stm)) {
            break;
          }
          stm = 3 - stm;
        }
        depthSum += reached;
        covered[Math.min(covered.length - 1, reached)]++;
        if (diffed) {
          followed++;
        } else {
          ended++;
        }
      }
    }
    System.out.printf(Locale.ROOT, "%d games with %s: the book differs from the move played in %d, in the other %d it runs out"
            + " first; mean ply reached %.1f%n",
        games, target, followed, ended, games == 0 ? 0 : (double) depthSum / games);
    List<Diff> list = new ArrayList<>(diffs.values());
    list.sort((x, y) -> y.games - x.games);
    System.out.println("| ply | line before | played | book | games | our W-L via played | who | long search played | book |"
        + " book - played | rollouts played | rollouts book |");
    System.out.println("|---|---|---|---|---|---|---|---|---|---|---|---|");
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    long nps = 800_000L * (g.cells <= 200 ? 1 : 1);
    List<double[]> scored = new ArrayList<>();
    try {
      int rank = 0;
      for (Diff d : list) {
        Board b = position(g, d.prefix);
        int stm = d.ply % 2 == 0 ? 1 : 2;
        TranspositionTable tt = new TranspositionTable(22);
        Searcher s = new Searcher(g, eval, tt, Searcher.Options.DEFAULT, new Vcf.Table(17));
        long nodes = nps * deepMs / 1000;
        int sp = score(s, g, b, d.played, stm, d.ply, nodes);
        int sb = score(s, g, b, d.book, stm, d.ply, nodes);
        // once more in the other order: the table favours whichever came second
        sb = (sb + score(s, g, b, d.book, stm, d.ply, nodes)) / 2;
        sp = (sp + score(s, g, b, d.played, stm, d.ply, nodes)) / 2;
        String rp = "";
        String rb = "";
        if (rollouts > 0 && rank < top) {
          double[] a = rollout(g, eval, d.prefix, d.played, stm, rollouts, rolloutNodes, pool, threads);
          double[] c = rollout(g, eval, d.prefix, d.book, stm, rollouts, rolloutNodes, pool, threads);
          rp = String.format(Locale.ROOT, "%.1f%% of %d", a[0], (int) a[1]);
          rb = String.format(Locale.ROOT, "%.1f%% of %d", c[0], (int) c[1]);
          scored.add(new double[] {a[0], c[0], d.games});
        }
        System.out.printf(Locale.ROOT, "| %d | %s | %s | %s | %d | %d-%d | %s | %+d | %+d | %+d | %s | %s |%n", d.ply, d.prefix,
            Character.forDigit(d.played, 36), Character.forDigit(d.book, 36), d.games, d.wins, d.losses,
            String.join(",", d.who.subList(0, Math.min(2, d.who.size()))), sp, sb, sb - sp, rp, rb);
        rank++;
      }
      simulate(g, book, lines, target, us -> {});
    } finally {
      pool.shutdownNow();
    }
    if (!scored.isEmpty()) {
      double wa = 0;
      double wc = 0;
      double wn = 0;
      for (double[] x : scored) {
        wa += x[0] * x[2];
        wc += x[1] * x[2];
        wn += x[2];
      }
      System.out.printf(Locale.ROOT, "rollouts, weighted by games: played %.1f%%, book %.1f%%%n", wa / wn, wc / wn);
    }
  }

  private interface Sink {
    void line(int us);
  }

  /** Our own bots are called Savi beater, so they do not count as Savi. */
  private static boolean isTarget(String name, String target) {
    return name.contains(target) && !name.contains("beater");
  }

  /**
   * Book against the target's replies known from the export: at the target's positions it plays its most frequent move
   * there, from any game of that bot. Stops when the target's reply is unknown or the book has no entry.
   */
  private static void simulate(Geometry g, Book book, List<Line> lines, String target, Sink sink) {
    Map<String, Map<Integer, int[]>> replies = new HashMap<>();
    for (Line line : lines) {
      for (int side = 1; side <= 2; side++) {
        if (!isTarget(side == 1 ? line.nameF : line.nameS, target)) {
          continue;
        }
        for (int i = side - 1; i < line.moves.length(); i += 2) {
          replies.computeIfAbsent(line.moves.substring(0, i), k -> new HashMap<>())
              .computeIfAbsent(Character.digit(line.moves.charAt(i), 36), k -> new int[1])[0]++;
        }
      }
    }
    for (int us = 1; us <= 2; us++) {
      StringBuilder sb = new StringBuilder();
      StringBuilder show = new StringBuilder("book as " + (us == 1 ? "F" : "S") + " against known " + target + " replies:");
      Board b = new Board(g);
      int stm = 1;
      String stop = "";
      for (int i = 0; i < 60; i++) {
        int col;
        if (stm == us) {
          col = book.column(b, i, us, (i + 1) / 2);
          if (col < 0) {
            stop = " (book ends)";
            break;
          }
        } else {
          Map<Integer, int[]> r = replies.get(sb.toString());
          if (r == null) {
            stop = " (reply unknown)";
            break;
          }
          col = -1;
          int bestN = 0;
          for (Map.Entry<Integer, int[]> e : r.entrySet()) {
            if (e.getValue()[0] > bestN) {
              bestN = e.getValue()[0];
              col = e.getKey();
            }
          }
          show.append(" [").append(bestN).append("/");
          int all = 0;
          for (int[] v : r.values()) {
            all += v[0];
          }
          show.append(all).append("]");
        }
        char ch = Character.forDigit(col, 36);
        sb.append(ch);
        show.append(' ').append(stm == 1 ? 'F' : 'S').append(ch);
        if (b.make(col, stm)) {
          stop = " (win)";
          break;
        }
        stm = 3 - stm;
      }
      System.out.println(show + stop + ", " + sb.length() + " plies");
    }
  }

  private static Board position(Geometry g, String prefix) {
    Board b = new Board(g);
    int stm = 1;
    for (int i = 0; i < prefix.length(); i++) {
      b.make(Character.digit(prefix.charAt(i), 36), stm);
      stm = 3 - stm;
    }
    return b;
  }

  private static int score(Searcher s, Geometry g, Board b, int col, int stm, int ply, long nodes) {
    Board c = new Board(g);
    c.copyFrom(b);
    if (c.make(col, stm)) {
      return Searcher.MATE - 1;
    }
    int cdStm = ply / 2;
    int cdOther = (ply + 1) / 2;
    return -s.run(c, 3 - stm, cdOther, Math.min(cdStm + 1, 10), ply + 1, Clock.nanos(), NO_TIME_LIMIT, nodes).score();
  }

  /** Mover's score in percent over games between equal engines from the position after the move, and the game count. */
  private static double[] rollout(Geometry g, Evaluator eval, String prefix, int col, int stm, int games, long nodes,
      ExecutorService pool, int threads) throws Exception {
    int[] opening = new int[prefix.length() + 1];
    for (int i = 0; i < prefix.length(); i++) {
      opening[i] = Character.digit(prefix.charAt(i), 36);
    }
    opening[prefix.length()] = col;
    List<Future<Double>> done = new ArrayList<>();
    for (int i = 0; i < games; i++) {
      long seed = 9_000_001L * (prefix.hashCode() + 31L * col) + i;
      done.add(pool.submit(() -> {
        FastGame.Engine f = BookFast.engine(g, eval, nodes, 0.3, seed, null, null);
        FastGame.Engine s = BookFast.engine(g, eval, nodes, 0.3, seed ^ 0x5DEECE66DL, null, null);
        FastGame.Outcome o = FastGame.play(g, opening, f, s, null);
        return o.winner() == 0 ? 0.5 : o.winner() == stm ? 1.0 : 0.0;
      }));
    }
    double sum = 0;
    for (Future<Double> f : done) {
      sum += f.get();
    }
    return new double[] {100 * sum / games, games};
  }
}
