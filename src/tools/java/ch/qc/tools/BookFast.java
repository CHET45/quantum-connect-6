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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-node games from the empty board between the same engine with and without a book. Each move's node budget is
 * drawn from nodes * [1 - jitter, 1 + jitter] per game seed, because identical deterministic engines would otherwise
 * play one game per colour. No clocks, so it can run at Idle priority without the real-time lock. A book move uses a
 * third of the nodes for its proven-result check, as the bot does; the time this saves in a real game is not measured
 * here. Output lines have the {@link BookMatch} format.
 *
 * <pre>
 * --book FILE --size 15x11 --weights SPEC (a .qcw file or "hand") --nodes N --jitter 0.3 --pairs N --threads N
 * --seed S --out FILE --b-book FILE (a book for B as well, to compare two books) --b-weights SPEC (another evaluation
 * for B); --book none plays A without a book, the baseline against such a B; --b-imitates NAME B plays what that bot
 * played on the server (suites/book-lines.txt, or --lines FILE) wherever it has been in the position, and searches elsewhere
 * </pre>
 */
public final class BookFast {
  private BookFast() {}

  private static final long NO_TIME_LIMIT = Long.MAX_VALUE / 4;

  /** Moves a named bot played from each position in the server lines: canonical position key, canonical column, count. */
  static Map<Long, Map<Integer, int[]>> targetMoves(Path lines, Geometry g, String target) throws java.io.IOException {
    Map<Long, Map<Integer, int[]>> moves = new HashMap<>();
    String size = g.rows + "x" + g.cols;
    Board b = new Board(g);
    for (String l : Files.readAllLines(lines, StandardCharsets.UTF_8)) {
      if (!l.startsWith(size + " ")) {
        continue;
      }
      String[] p = l.trim().split(" ");
      if (p.length < 6) {
        continue;
      }
      String[] names = p[4].split("[|]");
      for (int side = 1; side <= 2; side++) {
        String name = names[side - 1];
        if (!name.contains(target) || name.contains("beater")) {
          continue;
        }
        b.clear();
        int stm = 1;
        for (int i = 0; i < p[5].length(); i++) {
          int col = Character.digit(p[5].charAt(i), 36);
          if (stm == side) {
            long a = Book.hash(b, false);
            long m = Book.hash(b, true);
            boolean flip = Long.compareUnsigned(m, a) < 0;
            int c = a == m ? Math.min(col, g.cols - 1 - col) : flip ? g.cols - 1 - col : col;
            moves.computeIfAbsent(flip ? m : a, k -> new HashMap<>()).computeIfAbsent(c, k -> new int[1])[0]++;
          }
          if (b.make(col, stm)) {
            break;
          }
          stm = 3 - stm;
        }
      }
    }
    return moves;
  }

  /** The named bot's move while the position is one it was seen in (chosen by how often it played each), else the engine. */
  static FastGame.Engine imitating(FastGame.Engine engine, Geometry g, Map<Long, Map<Integer, int[]>> moves, long seed,
      AtomicLong imitated) {
    SplittableRandom rnd = new SplittableRandom(seed ^ 0x1234567L);
    return (Board b, int me, int cdMe, int cdOpp, int turn) -> {
      if (turn == b.discs()) {
        long a = Book.hash(b, false);
        long m = Book.hash(b, true);
        boolean flip = Long.compareUnsigned(m, a) < 0;
        Map<Integer, int[]> known = moves.get(flip ? m : a);
        if (known != null) {
          int total = 0;
          for (int[] n : known.values()) {
            total += n[0];
          }
          int pick = rnd.nextInt(total);
          for (Map.Entry<Integer, int[]> e : known.entrySet()) {
            pick -= e.getValue()[0];
            if (pick < 0) {
              int col = flip ? g.cols - 1 - e.getKey() : e.getKey();
              if (!b.isFull(col)) {
                imitated.incrementAndGet();
                return col;
              }
              break;
            }
          }
        }
      }
      return engine.move(b, me, cdMe, cdOpp, turn);
    };
  }

  static FastGame.Engine engine(Geometry g, Evaluator eval, long nodes, double jitter, long seed, Book book,
      AtomicLong hits) {
    Searcher s = new Searcher(g, eval, new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16));
    SplittableRandom rnd = new SplittableRandom(seed);
    return (Board b, int me, int cdMe, int cdOpp, int turn) -> {
      long n = (long) (nodes * (1 + jitter * (2 * rnd.nextDouble() - 1)));
      int column = book == null ? -1 : book.column(b, turn, me, cdOpp);
      Searcher.Result r = s.run(b, me, cdMe, cdOpp, turn, Clock.nanos(), NO_TIME_LIMIT, column >= 0 ? n / 3 : n);
      if (column >= 0 && r.move() != Tactics.NONE && Math.abs(r.score()) <= Searcher.MATE_BOUND) {
        hits.incrementAndGet();
        return column;
      }
      return r.move();
    };
  }

  public static void main(String[] args) throws Exception {
    Path bookFile = null;
    Path bookFileB = null;
    String size = "15x11";
    String weights = "hand";
    String weightsB = null;
    String target = null;
    Path lines = Path.of("suites/book-lines.txt");
    long nodes = 60_000;
    double jitter = 0.3;
    int pairs = 100;
    int threads = 2;
    long seed = 1;
    Path out = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--book" -> bookFile = Path.of(args[++i]);
        case "--b-book" -> bookFileB = Path.of(args[++i]);
        case "--size" -> size = args[++i];
        case "--weights" -> weights = args[++i];
        case "--b-weights" -> weightsB = args[++i];
        case "--b-imitates" -> target = args[++i];
        case "--lines" -> lines = Path.of(args[++i]);
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--jitter" -> jitter = Double.parseDouble(args[++i]);
        case "--pairs" -> pairs = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--out" -> out = Path.of(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    // "none" plays both sides without a book: the baseline against a different opponent
    Book bookA = bookFile.toString().equals("none") ? null : Book.fromBytes(Files.readAllBytes(bookFile));
    Book bookB = bookFileB == null ? null : Book.fromBytes(Files.readAllBytes(bookFileB));
    String[] rc = size.split("x");
    Geometry g = new Geometry(Integer.parseInt(rc[0]), Integer.parseInt(rc[1]));
    Evaluator eval = Evaluators.factory(weights).apply(g);
    Evaluator evalB = weightsB == null ? eval : Evaluators.factory(weightsB).apply(g);
    Map<Long, Map<Integer, int[]>> imitate = target == null ? null : targetMoves(lines, g, target);
    AtomicLong imitated = new AtomicLong();
    if (out != null && out.toAbsolutePath().getParent() != null) {
      Files.createDirectories(out.toAbsolutePath().getParent());
    }
    PrintWriter w = out == null ? null : new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8), true);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicLong hits = new AtomicLong();
    Set<String> unique = new HashSet<>();
    double score = 0;
    int games = 0;
    long t0 = System.nanoTime();
    try {
      CompletionService<String[]> done = new ExecutorCompletionService<>(pool);
      long n = nodes;
      double j = jitter;
      long sd = seed;
      for (int p = 0; p < pairs; p++) {
        int pair = p;
        done.submit(() -> new String[] {game(g, eval, evalB, n, j, sd, pair, true, bookA, bookB, hits, imitate, imitated),
            game(g, eval, evalB, n, j, sd, pair, false, bookA, bookB, hits, imitate, imitated)});
      }
      for (int p = 0; p < pairs; p++) {
        for (String line : done.take().get()) {
          if (w != null) {
            w.println(line);
          }
          games++;
          score += line.contains("\"scoreA\":1.0") ? 1 : line.contains("\"scoreA\":0.5") ? 0.5 : 0;
          unique.add(line.substring(line.indexOf("\"moves\"")));
        }
      }
    } finally {
      pool.shutdownNow();
      if (w != null) {
        w.close();
      }
    }
    System.out.printf(Locale.ROOT, "%s, %d nodes, book %s: %d games, %d unique, A scores %.1f%%, %.1f book moves per game,"
            + " %.1f imitated moves per game, %.1f min%n",
        size, nodes, bookFile, games, unique.size(), games == 0 ? 0 : 100 * score / games,
        games == 0 ? 0 : (double) hits.get() / games, games == 0 ? 0 : (double) imitated.get() / games,
        (System.nanoTime() - t0) / 6e10);
  }

  private static String game(Geometry g, Evaluator eval, Evaluator evalB, long nodes, double jitter, long seed, int pair,
      boolean aFirst, Book bookA, Book bookB, AtomicLong hits, Map<Long, Map<Integer, int[]>> imitate, AtomicLong imitated) {
    long s = seed * 1_000_003L + pair * 2L + (aFirst ? 0 : 1);
    FastGame.Engine a = engine(g, eval, nodes, jitter, s, bookA, hits);
    FastGame.Engine b = engine(g, evalB, nodes, jitter, s ^ 0x5DEECE66DL, bookB, new AtomicLong());
    if (imitate != null) {
      b = imitating(b, g, imitate, s, imitated);
    }
    StringBuilder moves = new StringBuilder();
    int[] plies = new int[1];
    FastGame.Outcome o = FastGame.play(g, new int[0], aFirst ? a : b, aFirst ? b : a,
        (Board board, int stm, int cdStm, int cdOpp, int turn, int move) -> {
          moves.append(stm == 1 ? 'F' : 'S');
          moves.append(Character.forDigit(move == Tactics.NONE ? 0 : Tactics.column(move), 36));
          if (move != Tactics.NONE && Tactics.isSpecial(move)) {
            moves.append('*');
          }
          plies[0]++;
        });
    int aColour = aFirst ? 1 : 2;
    double scoreA = o.winner() == 0 ? 0.5 : o.winner() == aColour ? 1 : 0;
    return String.format(Locale.ROOT,
        "{\"pair\":%d,\"rows\":%d,\"cols\":%d,\"aFirst\":%b,\"scoreA\":%.1f,\"reason\":\"%s\",\"plies\":%d,"
            + "\"timeoutsA\":0,\"timeoutsB\":0,\"moves\":\"%s\"}",
        pair, g.rows, g.cols, aFirst, scoreA, o.reason(), plies[0], moves);
  }
}
