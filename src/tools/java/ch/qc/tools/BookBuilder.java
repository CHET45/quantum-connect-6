package ch.qc.tools;

import ch.qc.bot.Book;
import ch.qc.bot.Clock;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;

/**
 * Builds the opening book by drop-out expansion from the empty board. Every analysed position gets a score for every
 * drop (a short search of each child, then a long one of the best few), so a choice is never one search's whim: the
 * tree grows best first along the moves we would play and the replies an opponent might play (those strong bots really
 * played on the server cost almost nothing, other replies cost by rank and by how much worse they score), scores back
 * up by minimax, and when a backed-up score overturns a choice the alternative is expanded in turn. Mirror positions
 * share a node. Our choice also carries a small bonus or penalty from the results of strong-against-strong server games
 * through that move, so a line known to have lost 0-10 is left unless the search clearly prefers it. Blasts stay out
 * of the book: a position where the search prefers a blast gets no entry, and the bot's own search decides there.
 *
 * <pre>
 * --weights SPEC    comma list of .qcw files or directories holding eval*.qcw, or "hand". As in the bot, a table
 *                   trained for the size wins, then a generic one, then the hand evaluation. Default "weights".
 * --minutes M       wall-clock cap for all sizes (default 30)
 * --threads T       worker threads (default 2)
 * --out FILE        book blob (default weights/book.qcb); FILE.txt gets the human-readable listing
 * --lines FILE      server lines from tools/make-book-lines.py (default suites/book-lines.txt)
 * --sizes LIST      board sizes in build order (default 15x11,8x12,15x22)
 * --share LIST      share of the time per size (default 0.5,0.25,0.25)
 * --max-ply LIST    deepest position with an entry per size (default 30,24,24)
 * --deep-ms N       long search per candidate, converted to a node count at the start (default 1500)
 * --scan-ms N       short search per legal drop (default 60)
 * --k N             candidates searched long (default 3)
 * --cache DIR       per-position analysis cache, reused only for the same weights and search classes (default: next to
 *                   FILE); --fresh ignores what is there
 * </pre>
 */
public final class BookBuilder {
  private static final int MATE_BOUND = Searcher.MATE_BOUND;
  /** Weight of the server record of a move in our choice: this many score units at a 100% record (0.25 logit). */
  private static final int RECORD_WEIGHT = 64;
  /** An alternative of ours is followed while within this much of the best. */
  private static final int OUR_MARGIN = 51;
  /** A reply nobody strong played is followed only with rank below this and within this much of the best. */
  private static final int OPP_RANKS = 3;
  private static final int OPP_MARGIN = 128;
  private static final int SCORE_UNIT = 26;
  private static final double COST_CHOSEN = 0.1;
  /** A reply strong bots played: often, or once or twice. */
  private static final double COST_SERVER = 0.1;
  private static final double COST_SERVER_RARE = 0.25;
  private static final double COST_OPP_BEST = 0.5;
  private static final double COST_OPP = 1.0;
  private static final double COST_OUR_ALT = 1.5;

  private BookBuilder() {}

  /** One analysed position, canonical orientation (the smaller of the two hashes). */
  static final class Node {
    final long key;
    final int ply;
    final byte[] cells;
    final boolean symmetric;
    boolean analysed;
    /** Long searches for k candidates; otherwise only for the best (a position only the opponent moves from). */
    boolean full;
    boolean wantFull;
    int[] cols = new int[0];
    int[] scores = new int[0];
    boolean[] deep = new boolean[0];
    /** The node's root search prefers a blast (this is then not a book position). */
    boolean blastBest;
    int blastScore;
    /** Column of a proven win found by the root search, or -1. */
    int provenWin = -1;
    int depth;
    long[] childKey;
    int value;
    int choice = -1;

    Node(long key, int ply, byte[] cells, boolean symmetric) {
      this.key = key;
      this.ply = ply;
      this.cells = cells;
      this.symmetric = symmetric;
    }
  }

  /** What strong bots played from one position: per canonical column plays and the mover's wins and losses. */
  static final class Seen {
    final Map<Integer, int[]> byCol = new HashMap<>();

    int[] at(int col) {
      return byCol.computeIfAbsent(col, c -> new int[3]);
    }

    int plays(int col) {
      int[] r = byCol.get(col);
      return r == null ? 0 : r[0];
    }

    int bonus(int col) {
      int[] r = byCol.get(col);
      return r == null ? 0 : RECORD_WEIGHT * (r[1] - r[2]) / (r[1] + r[2] + 2);
    }
  }

  private record Canon(long key, boolean flip, boolean symmetric) {}

  private static Canon canon(Board b) {
    long a = Book.hash(b, false);
    long m = Book.hash(b, true);
    boolean flip = Long.compareUnsigned(m, a) < 0;
    return new Canon(flip ? m : a, flip, a == m);
  }

  private static byte[] cellsOf(Board b, boolean flip) {
    Geometry g = b.g;
    byte[] c = new byte[g.cells];
    for (int x = 0; x < g.cols; x++) {
      int to = flip ? g.cols - 1 - x : x;
      for (int y = 0; y < b.height(x); y++) {
        c[g.index(to, y)] = (byte) b.cell(x, y);
      }
    }
    return c;
  }

  /** Column in the canonical orientation; for a symmetric position the left half. */
  private static int canonCol(int col, Canon c, int cols) {
    int m = cols - 1 - col;
    return c.symmetric ? Math.min(col, m) : c.flip ? m : col;
  }

  private static final class Worker {
    final Searcher searcher;
    final Board board;
    final Board child;

    Worker(Geometry g, Evaluator eval) {
      searcher = new Searcher(g, eval, new TranspositionTable(22), Searcher.Options.DEFAULT, new Vcf.Table(17));
      board = new Board(g);
      child = new Board(g);
    }
  }

  private static final class SizeRun {
    final Geometry g;
    final int maxPly;
    final Map<Long, Node> nodes = new HashMap<>();
    final Map<Long, Seen> seen = new HashMap<>();
    final Map<Long, String> cache = new HashMap<>();
    Node root;
    long deepNodes;
    long scanNodes;
    int k;
    int expanded;
    int cachedUsed;
    PrintWriter cacheOut;

    SizeRun(Geometry g, int maxPly) {
      this.g = g;
      this.maxPly = maxPly;
    }
  }

  public static void main(String[] args) throws Exception {
    String weights = "weights";
    double minutes = 30;
    int threads = 2;
    Path out = Path.of("weights/book.qcb");
    Path lines = Path.of("suites/book-lines.txt");
    String sizes = "15x11,8x12,15x22";
    String share = "0.5,0.25,0.25";
    String maxPly = "30,24,24";
    long deepMs = 1500;
    long scanMs = 60;
    int k = 3;
    Path cacheDir = null;
    boolean fresh = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--weights" -> weights = args[++i];
        case "--minutes" -> minutes = Double.parseDouble(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--out" -> out = Path.of(args[++i]);
        case "--lines" -> lines = Path.of(args[++i]);
        case "--sizes" -> sizes = args[++i];
        case "--share" -> share = args[++i];
        case "--max-ply" -> maxPly = args[++i];
        case "--deep-ms" -> deepMs = Long.parseLong(args[++i]);
        case "--scan-ms" -> scanMs = Long.parseLong(args[++i]);
        case "--k" -> k = Integer.parseInt(args[++i]);
        case "--cache" -> cacheDir = Path.of(args[++i]);
        case "--fresh" -> fresh = true;
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    if (cacheDir == null) {
      cacheDir = out.toAbsolutePath().getParent().resolve("book-cache");
    }
    Files.createDirectories(cacheDir);
    int[][] sizeList = MatchMain.parseSizes(sizes);
    double[] shares = Arrays.stream(share.split(",")).mapToDouble(Double::parseDouble).toArray();
    int[] plies = Arrays.stream(maxPly.split(",")).mapToInt(Integer::parseInt).toArray();
    double shareSum = Arrays.stream(shares).sum();
    long start = System.nanoTime();
    long totalNanos = (long) (minutes * 60e9);
    long used = 0;
    double shareLeft = shareSum;
    String searchTag = searchClassesTag();
    List<SizeRun> runs = new ArrayList<>();
    for (int s = 0; s < sizeList.length; s++) {
      Geometry g = new Geometry(sizeList[s][0], sizeList[s][1]);
      runs.add(new SizeRun(g, plies[s]));
    }
    for (int s = 0; s < sizeList.length; s++) {
      SizeRun run = runs.get(s);
      Geometry g = run.g;
      String name = g.rows + "x" + g.cols;
      Evaluator eval = resolveWeights(weights, g);
      // unused time of a size passes to the next ones
      long elapsed = System.nanoTime() - start;
      long slice = (long) ((totalNanos - elapsed) * shares[s] / shareLeft);
      shareLeft -= shares[s];
      String tag = evalTag(weights, g) + "-" + searchTag + "-d" + deepMs + "-s" + scanMs + "-k" + k;
      Path cacheFile = cacheDir.resolve("cache-" + name + "-" + tag + ".txt");
      if (!fresh && Files.exists(cacheFile)) {
        for (String line : Files.readAllLines(cacheFile, StandardCharsets.UTF_8)) {
          int sp = line.indexOf(' ');
          if (sp > 0) {
            run.cache.put(Long.parseUnsignedLong(line.substring(0, sp), 16), line);
          }
        }
      }
      run.cacheOut = new PrintWriter(Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
      System.out.printf(Locale.ROOT, "%s: eval %s, %.1f min, %d cached positions%n", name, eval.getClass().getSimpleName(),
          slice / 6e10, run.cache.size());
      long nps = calibrate(g, eval);
      run.deepNodes = nps * deepMs / 1000;
      run.scanNodes = nps * scanMs / 1000;
      run.k = k;
      System.out.printf(Locale.ROOT, "%s: %d knodes/s, long search %d knodes, short %d knodes%n", name, nps / 1000,
          run.deepNodes / 1000, run.scanNodes / 1000);
      readLines(lines, run);
      build(run, eval, threads, System.nanoTime() + slice, out, runs);
      run.cacheOut.close();
      int[] perPly = new int[run.maxPly + 1];
      for (Node n : entries(run)) {
        perPly[n.ply]++;
      }
      System.out.printf(Locale.ROOT, "%s: %d analysed (%d from the cache), %d entries, per ply %s%n", name, run.expanded,
          run.cachedUsed, entries(run).size(), Arrays.toString(perPly));
      printMainLines(run);
      writeBook(out, runs);
    }
    System.out.printf(Locale.ROOT, "done in %.1f min, book %s, %d bytes%n", (System.nanoTime() - start) / 6e10, out,
        Files.size(out));
  }

  /** The weights this size would get in the bot: a table trained for the size, then a generic one, then hand. */
  private static Evaluator resolveWeights(String spec, Geometry g) throws IOException {
    Path f = weightsFile(spec, g);
    return f == null ? new HandEval(g) : WeightFiles.load(f, g);
  }

  private static String evalTag(String spec, Geometry g) throws IOException {
    Path f = weightsFile(spec, g);
    if (f == null) {
      return "hand";
    }
    CRC32 crc = new CRC32();
    crc.update(Files.readAllBytes(f));
    return Long.toHexString(crc.getValue());
  }

  private static Path weightsFile(String spec, Geometry g) throws IOException {
    List<Path> files = new ArrayList<>();
    for (String part : spec.split(",")) {
      part = part.trim();
      if (part.isEmpty() || part.equals("hand")) {
        continue;
      }
      Path p = Path.of(part);
      if (Files.isDirectory(p)) {
        try (var dir = Files.newDirectoryStream(p, "eval*.qcw")) {
          for (Path x : dir) {
            files.add(x);
          }
        }
      } else {
        files.add(p);
      }
    }
    Path generic = null;
    for (Path f : files) {
      var blob = WeightFiles.parse(Files.readAllBytes(f));
      if (blob.generic()) {
        generic = generic == null ? f : generic;
      } else if (blob.rows == g.rows && blob.cols == g.cols) {
        return f;
      }
    }
    return generic;
  }

  /** Changes when the search code changes, so a stale analysis cache is not reused. */
  private static String searchClassesTag() throws IOException {
    CRC32 crc = new CRC32();
    for (String name : List.of("Searcher", "Tactics", "Vcf")) {
      var in = Searcher.class.getResourceAsStream("/ch/qc/bot/search/" + name + ".class");
      if (in != null) {
        try (in) {
          crc.update(in.readAllBytes());
        }
      }
    }
    return Long.toHexString(crc.getValue());
  }

  private static long calibrate(Geometry g, Evaluator eval) {
    Worker w = new Worker(g, eval);
    Board b = w.board;
    b.clear();
    int mid = g.cols / 2;
    int stm = 1;
    for (int i = 0; i < 6; i++) {
      b.make(mid + (i % 3) - 1, stm);
      stm = 3 - stm;
    }
    // the first second warms the JIT and does not count
    w.searcher.run(b, stm, 3, 3, 6, Clock.nanos(), 1_000_000_000L);
    long t0 = System.nanoTime();
    Searcher.Result r = w.searcher.run(b, stm, 3, 3, 6, Clock.nanos(), 1_000_000_000L);
    return Math.max(50_000, r.nodes() * 1_000_000_000L / Math.max(1, System.nanoTime() - t0));
  }

  private static void readLines(Path file, SizeRun run) throws IOException {
    if (!Files.exists(file)) {
      return;
    }
    Geometry g = run.g;
    String size = g.rows + "x" + g.cols;
    Board b = new Board(g);
    int games = 0;
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (line.startsWith("#") || !line.startsWith(size + " ")) {
        continue;
      }
      String[] p = line.trim().split(" ");
      if (p.length < 6) {
        continue;
      }
      boolean[] strong = {p[1].equals("1"), p[2].equals("1")};
      int winner = p[3].equals("F") ? 1 : p[3].equals("S") ? 2 : 0;
      boolean bothStrong = strong[0] && strong[1];
      b.clear();
      int stm = 1;
      games++;
      for (int i = 0; i < p[5].length() && i <= run.maxPly; i++) {
        int col = Character.digit(p[5].charAt(i), 36);
        if (col < 0 || col >= g.cols || b.isFull(col)) {
          break;
        }
        Canon c = canon(b);
        int[] r = run.seen.computeIfAbsent(c.key, x -> new Seen()).at(canonCol(col, c, g.cols));
        r[0] += strong[stm - 1] ? 1 : 0;
        if (bothStrong && winner != 0) {
          r[winner == stm ? 1 : 2]++;
        }
        if (b.make(col, stm)) {
          break;
        }
        stm = 3 - stm;
      }
    }
    System.out.printf(Locale.ROOT, "%s: %d server games, %d positions%n", size, games, run.seen.size());
  }

  private static Node node(SizeRun run, Board b, int ply) {
    Canon c = canon(b);
    Node n = run.nodes.get(c.key);
    if (n == null) {
      n = new Node(c.key, ply, cellsOf(b, c.flip), c.symmetric);
      run.nodes.put(c.key, n);
    }
    return n;
  }

  private static void build(SizeRun run, Evaluator eval, int threads, long deadline, Path out, List<SizeRun> runs)
      throws Exception {
    Geometry g = run.g;
    Board empty = new Board(g);
    run.root = node(run, empty, 0);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    Worker[] workers = new Worker[threads];
    for (int i = 0; i < threads; i++) {
      workers[i] = new Worker(g, eval);
    }
    long lastWrite = System.nanoTime();
    int rounds = 0;
    try {
      while (System.nanoTime() < deadline) {
        List<Node> frontier = frontier(run);
        if (frontier.isEmpty()) {
          System.out.println("frontier empty");
          break;
        }
        // cached positions cost nothing, so they do not use up a worker
        List<Node> todo = new ArrayList<>();
        boolean fromCache = false;
        for (Node n : frontier) {
          if (restore(run, n)) {
            fromCache = true;
          } else if (todo.size() < threads) {
            todo.add(n);
          }
        }
        if (fromCache && todo.size() < threads) {
          backup(run);
          continue;
        }
        List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < todo.size(); i++) {
          Node n = todo.get(i);
          Worker w = workers[i];
          done.add(pool.submit(() -> analyse(run, n, w)));
        }
        for (Future<?> f : done) {
          f.get();
        }
        for (Node n : todo) {
          run.expanded++;
          run.cacheOut.println(serialise(n));
        }
        backup(run);
        rounds++;
        if (rounds % 10 == 0) {
          Node r = run.root;
          System.out.printf(Locale.ROOT, "%s: %d analysed, frontier %d, root value %+d, %.1f min left%n",
              g.rows + "x" + g.cols, run.expanded, frontier.size(), r.value, (deadline - System.nanoTime()) / 6e10);
        }
        if (System.nanoTime() - lastWrite > 300_000_000_000L) {
          writeBook(out, runs);
          lastWrite = System.nanoTime();
        }
      }
    } finally {
      pool.shutdownNow();
    }
    backup(run);
  }

  private static boolean restore(SizeRun run, Node n) {
    String line = run.cache.get(n.key);
    if (line == null) {
      return false;
    }
    if (n.analysed) {
      return false;
    }
    String[] p = line.split(" ");
    int flags = Integer.parseInt(p[2]);
    n.blastBest = (flags & 1) != 0;
    n.full = (flags & 2) == 0;
    n.blastScore = Integer.parseInt(p[3]);
    n.provenWin = Integer.parseInt(p[4]);
    n.depth = Integer.parseInt(p[5]);
    int m = p.length - 6;
    n.cols = new int[m];
    n.scores = new int[m];
    n.deep = new boolean[m];
    for (int i = 0; i < m; i++) {
      String[] q = p[6 + i].split(":");
      n.cols[i] = Integer.parseInt(q[0]);
      n.scores[i] = Integer.parseInt(q[1]);
      n.deep[i] = q[2].equals("1");
    }
    finish(run, n);
    run.cachedUsed++;
    run.expanded++;
    return true;
  }

  private static String serialise(Node n) {
    StringBuilder sb = new StringBuilder();
    sb.append(Long.toHexString(n.key)).append(' ').append(n.ply).append(' ').append((n.blastBest ? 1 : 0) | (n.full ? 0 : 2)).append(' ')
        .append(n.blastScore).append(' ').append(n.provenWin).append(' ').append(n.depth);
    for (int i = 0; i < n.cols.length; i++) {
      sb.append(' ').append(n.cols[i]).append(':').append(n.scores[i]).append(':').append(n.deep[i] ? 1 : 0);
    }
    return sb.toString();
  }

  /** Links the node to its children and marks it analysed. */
  private static void finish(SizeRun run, Node n) {
    Geometry g = run.g;
    Board b = new Board(g);
    b.load(n.cells);
    int stm = n.ply % 2 == 0 ? 1 : 2;
    n.childKey = new long[n.cols.length];
    for (int i = 0; i < n.cols.length; i++) {
      boolean win = b.make(n.cols[i], stm);
      if (win) {
        n.childKey[i] = 0;
      } else {
        synchronized (run.nodes) {
          n.childKey[i] = node(run, b, n.ply + 1).key;
        }
      }
      b.unmake(n.cols[i]);
    }
    n.analysed = true;
  }

  /** Engine counter of the side to move and of the other side at this ply, with no blast and no skipped turn. */
  private static int counterStm(int ply) {
    return ply / 2;
  }

  private static int counterOther(int ply) {
    return (ply + 1) / 2;
  }

  private static final long NO_TIME_LIMIT = Long.MAX_VALUE / 4;

  private static void analyse(SizeRun run, Node n, Worker w) {
    Geometry g = run.g;
    Board b = w.board;
    b.load(n.cells);
    int stm = n.ply % 2 == 0 ? 1 : 2;
    int cdStm = counterStm(n.ply);
    int cdOther = counterOther(n.ply);
    int[] cols;
    int[] scores;
    boolean[] deep;
    int m;
    Searcher.Result root = null;
    if (n.analysed) {
      // upgrade of a position so far searched only for the opponent's best move
      cols = n.cols;
      scores = n.scores;
      deep = n.deep;
      m = cols.length;
    } else {
      cols = new int[g.cols];
      m = 0;
      for (int c = 0; c < g.cols; c++) {
        if (!b.isFull(c) && !(n.symmetric && c > g.cols - 1 - c)) {
          cols[m++] = c;
        }
      }
      cols = Arrays.copyOf(cols, m);
      scores = new int[m];
      deep = new boolean[m];
      // the root search finds a win by threats, a blast and the opponent's threats; it also fills the table
      long rootNodes = cdStm >= 10 ? run.deepNodes : run.scanNodes * 4;
      root = w.searcher.run(b, stm, cdStm, cdOther, n.ply, Clock.nanos(), NO_TIME_LIMIT, rootNodes);
      n.depth = root.depth();
      if (root.move() != Tactics.NONE && root.score() > MATE_BOUND && !Tactics.isSpecial(root.move())) {
        n.provenWin = Tactics.column(root.move());
      }
      for (int i = 0; i < m; i++) {
        scores[i] = childScore(w, g, b, cols[i], stm, cdStm, cdOther, n.ply, run.scanNodes);
      }
    }
    int k = n.wantFull ? run.k : 1;
    Integer[] order = new Integer[m];
    for (int i = 0; i < m; i++) {
      order[i] = i;
    }
    final int[] s0 = scores;
    Arrays.sort(order, Comparator.comparingInt((Integer i) -> -s0[i]));
    // long search of the best few; a move that climbs to the top afterwards gets one too
    for (int i = 0; i < Math.min(k, m); i++) {
      int x = order[i];
      if (deep[x]) {
        continue;
      }
      if (Math.abs(scores[x]) <= MATE_BOUND) {
        scores[x] = childScore(w, g, b, cols[x], stm, cdStm, cdOther, n.ply, run.deepNodes);
      }
      deep[x] = true;
    }
    // a move strong bots really played gets a long search too unless it scores far below the best
    Seen seen = run.seen.get(n.key);
    if (seen != null && n.wantFull) {
      int top = -Searcher.MATE;
      for (int i = 0; i < m; i++) {
        top = Math.max(top, scores[i]);
      }
      for (int i = 0; i < m; i++) {
        if (!deep[i] && seen.plays(cols[i]) > 0 && scores[i] >= top - OUR_MARGIN) {
          if (Math.abs(scores[i]) <= MATE_BOUND) {
            scores[i] = childScore(w, g, b, cols[i], stm, cdStm, cdOther, n.ply, run.deepNodes);
          }
          deep[i] = true;
        }
      }
    }
    for (int round = 0; round < 3; round++) {
      int best = -1;
      for (int i = 0; i < m; i++) {
        if (best < 0 || scores[i] > scores[best]) {
          best = i;
        }
      }
      if (best < 0 || deep[best]) {
        break;
      }
      if (Math.abs(scores[best]) <= MATE_BOUND) {
        scores[best] = childScore(w, g, b, cols[best], stm, cdStm, cdOther, n.ply, run.deepNodes);
      }
      deep[best] = true;
    }
    int bestDrop = -Searcher.MATE;
    for (int i = 0; i < m; i++) {
      bestDrop = Math.max(bestDrop, scores[i]);
    }
    if (root != null && root.move() != Tactics.NONE && Tactics.isSpecial(root.move())
        && root.score() >= bestDrop - SCORE_UNIT) {
      n.blastBest = true;
      n.blastScore = root.score();
    }
    n.cols = cols;
    n.scores = scores;
    n.deep = deep;
    n.full = n.wantFull;
    finish(run, n);
  }

  private static int childScore(Worker w, Geometry g, Board b, int col, int stm, int cdStm, int cdOther, int ply,
      long nodes) {
    boolean win = b.make(col, stm);
    int score;
    if (win) {
      score = Searcher.MATE - 1;
    } else if (b.topRowFull()) {
      score = 0;
    } else {
      w.child.copyFrom(b);
      Searcher.Result r = w.searcher.run(w.child, 3 - stm, cdOther, Math.min(cdStm + 1, 10), ply + 1, Clock.nanos(),
          NO_TIME_LIMIT, nodes);
      score = -r.score();
    }
    b.unmake(col);
    return score;
  }

  /** Move scores of a node after backing up: an analysed child's value replaces the search score. */
  private static int moveValue(SizeRun run, Node n, int i) {
    if (n.childKey[i] != 0) {
      Node c = run.nodes.get(n.childKey[i]);
      if (c != null && c.analysed) {
        return -c.value;
      }
    }
    return n.scores[i];
  }

  private static boolean selectable(SizeRun run, Node n, int i) {
    if (n.deep[i]) {
      return true;
    }
    Node c = n.childKey[i] == 0 ? null : run.nodes.get(n.childKey[i]);
    return c != null && c.analysed;
  }

  /** Minimax over the analysed nodes, deepest first, and our choice at each. */
  private static void backup(SizeRun run) {
    List<Node> all = new ArrayList<>();
    for (Node n : run.nodes.values()) {
      if (n.analysed) {
        all.add(n);
      }
    }
    all.sort(Comparator.comparingInt((Node n) -> -n.ply));
    for (Node n : all) {
      Seen seen = run.seen.get(n.key);
      int value = -Searcher.MATE;
      int choice = -1;
      int chosenRank = Integer.MIN_VALUE;
      for (int i = 0; i < n.cols.length; i++) {
        int v = moveValue(run, n, i);
        value = Math.max(value, v);
        if (!selectable(run, n, i)) {
          continue;
        }
        int rank = v + (seen == null || Math.abs(v) > MATE_BOUND ? 0 : seen.bonus(n.cols[i]));
        if (rank > chosenRank) {
          chosenRank = rank;
          choice = i;
        }
      }
      if (n.blastBest) {
        value = Math.max(value, n.blastScore);
      }
      n.value = value;
      n.choice = choice;
      if (n.provenWin >= 0) {
        for (int i = 0; i < n.cols.length; i++) {
          if (n.cols[i] == n.provenWin
              || (n.symmetric && n.cols[i] == g(run).cols - 1 - n.provenWin)) {
            n.choice = i;
          }
        }
      }
    }
  }

  private static Geometry g(SizeRun run) {
    return run.g;
  }

  /** The unanalysed nodes reachable under the current choices, cheapest first. */
  private static List<Node> frontier(SizeRun run) {
    Map<Long, Double> costs = new HashMap<>();
    if (!run.root.analysed) {
      run.root.wantFull = true;
      return List.of(run.root);
    }
    for (int tree = 0; tree < 2; tree++) {
      walk(run, run.root, tree, 0, new HashMap<>(), costs);
    }
    List<Node> list = new ArrayList<>();
    for (Long k : costs.keySet()) {
      list.add(run.nodes.get(k));
    }
    list.sort(Comparator.comparingDouble((Node n) -> costs.get(n.key)));
    return list;
  }

  private static void walk(SizeRun run, Node n, int tree, double cost, Map<Long, Double> visited, Map<Long, Double> frontier) {
    boolean ours = n.ply % 2 == tree;
    if (!n.analysed || (ours && !n.full)) {
      n.wantFull |= ours;
      frontier.merge(n.key, cost, Math::min);
      return;
    }
    Double before = visited.get(n.key);
    if (before != null && before <= cost) {
      return;
    }
    visited.put(n.key, cost);
    if (n.ply >= run.maxPly || n.blastBest || n.provenWin >= 0 || Math.abs(n.value) > MATE_BOUND) {
      return;
    }
    int m = n.cols.length;
    Integer[] order = new Integer[m];
    int[] v = new int[m];
    for (int i = 0; i < m; i++) {
      order[i] = i;
      v[i] = moveValue(run, n, i);
    }
    Arrays.sort(order, Comparator.comparingInt((Integer i) -> -v[i]));
    Seen seen = run.seen.get(n.key);
    for (int r = 0; r < m; r++) {
      int i = order[r];
      if (n.childKey[i] == 0) {
        continue;
      }
      Node child = run.nodes.get(n.childKey[i]);
      // the last ply matters only if we move there
      if (child == null || child.ply > run.maxPly || (child.ply == run.maxPly && child.ply % 2 != tree)) {
        continue;
      }
      double d = (n.value - v[i]) / (double) SCORE_UNIT;
      double step;
      if (ours) {
        if (i == n.choice) {
          step = COST_CHOSEN;
        } else if (r <= 1 && n.value - v[i] <= OUR_MARGIN) {
          step = COST_OUR_ALT + 2 * d;
        } else {
          continue;
        }
      } else if (seen != null && seen.plays(n.cols[i]) > 0) {
        step = seen.plays(n.cols[i]) >= 3 ? COST_SERVER : COST_SERVER_RARE;
      } else if (r == 0) {
        step = COST_OPP_BEST;
      } else if (r < OPP_RANKS && n.value - v[i] <= OPP_MARGIN) {
        step = COST_OPP + d;
      } else {
        continue;
      }
      walk(run, child, tree, cost + step, visited, frontier);
    }
  }

  private static List<Node> entries(SizeRun run) {
    List<Node> list = new ArrayList<>();
    for (Node n : run.nodes.values()) {
      if (n.analysed && n.full && n.choice >= 0 && !n.blastBest && n.ply <= run.maxPly && n.value >= -MATE_BOUND) {
        list.add(n);
      }
    }
    return list;
  }

  private static void printMainLines(SizeRun run) {
    StringBuilder sb = new StringBuilder(run.g.rows + "x" + run.g.cols + " main line:");
    Board b = new Board(run.g);
    int stm = 1;
    while (true) {
      Canon c = canon(b);
      Node n = run.nodes.get(c.key);
      if (n == null || !n.analysed || n.choice < 0) {
        break;
      }
      int col = c.flip ? run.g.cols - 1 - n.cols[n.choice] : n.cols[n.choice];
      sb.append(' ').append(stm == 1 ? 'F' : 'S').append(Character.forDigit(col, 36));
      sb.append('(').append(moveValue(run, n, n.choice)).append(')');
      if (b.make(col, stm)) {
        break;
      }
      stm = 3 - stm;
    }
    System.out.println(sb);
  }

  private static void writeBook(Path out, List<SizeRun> runs) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream d = new DataOutputStream(bytes);
    d.writeBytes("QCB1");
    List<SizeRun> done = new ArrayList<>();
    for (SizeRun r : runs) {
      if (r.root != null) {
        done.add(r);
      }
    }
    d.writeByte(done.size());
    StringBuilder text = new StringBuilder("# size ply key column value choice-rank candidates col:score:deep[:plays]\n");
    for (SizeRun r : done) {
      TreeMap<Long, Node> sorted = new TreeMap<>();
      int maxDiscs = 0;
      for (Node n : entries(r)) {
        sorted.put(n.key >>> 24, n);
        maxDiscs = Math.max(maxDiscs, n.ply);
      }
      d.writeByte(r.g.rows);
      d.writeByte(r.g.cols);
      d.writeByte(maxDiscs);
      d.writeInt(sorted.size());
      for (Map.Entry<Long, Node> e : sorted.entrySet()) {
        long k = e.getKey();
        for (int b = 4; b >= 0; b--) {
          d.writeByte((int) (k >>> (8 * b)));
        }
        Node n = e.getValue();
        d.writeByte(n.cols[n.choice]);
        Seen seen = r.seen.get(n.key);
        text.append(r.g.rows).append('x').append(r.g.cols).append(' ').append(n.ply).append(' ')
            .append(Long.toHexString(n.key)).append(' ').append(n.cols[n.choice]).append(' ').append(n.value);
        for (int i = 0; i < n.cols.length; i++) {
          if (n.deep[i] || (seen != null && seen.plays(n.cols[i]) > 0)) {
            text.append(' ').append(n.cols[i]).append(':').append(moveValue(r, n, i)).append(':')
                .append(n.deep[i] ? 1 : 0);
            if (seen != null && seen.plays(n.cols[i]) > 0) {
              int[] rec = seen.byCol.get(n.cols[i]);
              text.append(":n").append(rec[0]).append("+").append(rec[1]).append("-").append(rec[2]);
            }
          }
        }
        text.append('\n');
      }
    }
    d.flush();
    Path parent = out.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path tmp = parent.resolve(out.getFileName() + ".tmp");
    Files.write(tmp, bytes.toByteArray());
    Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(Path.of(out + ".txt"), text, StandardCharsets.UTF_8);
  }
}
