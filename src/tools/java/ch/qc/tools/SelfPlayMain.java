package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.FeatureSpec;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Self-play data generation (design 4.2) in the fixed-node mode: every position the engine searches is recorded
 * with its root value and depth and, after the game, the result and the plies to the end ({@link Qcp} format, one
 * shard per worker). Diversity is injected because the engine is deterministic: 75 percent of games start after
 * 2-8 random plies, 25 percent after 20-60 random plies with a random blast (probability 0.15) whenever the mover's
 * special is ready, kept only if a short search scores the start within +-600 units; during play a random safe drop
 * is chosen with probability 0.08 in the first 20 engine plies and a random safe blast with probability 0.1 whenever
 * the mover's special is ready. Labels are search values, so these choices do not corrupt them. Positions with a
 * proven result within 3 plies are not recorded; games stop at 400 turns as adjudicated draws.
 *
 * <p>Opponent pool (design 4.2): with --mix SELF,PREV,HAND the engine plays itself, the previous generation
 * (--prev, a .qcw file or "hand") or the hand-table anchor in those proportions; against another engine only the
 * positions where the current engine moves are recorded, so every label comes from the current engine's search.
 *
 * <pre>
 * --out data/gen000 --positions 1500000 --nodes 12000 --threads 18 --seed 1 [--weights eval.qcw] --generation 0
 *     [--prev eval.qcw|hand] [--mix 0.6,0.2,0.2]
 * </pre>
 */
public final class SelfPlayMain {
  private static final int MAX_TURNS = 400;

  /** Position after the random start: board, counters, side to move and turn index. */
  record Start(Board board, int[] counter, int toMove, int turn) {}

  public static void main(String[] args) throws Exception {
    String out = "data/gen000";
    long positions = 1_500_000;
    long nodes = 12_000;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    long seed = 1;
    String weights = null;
    String prev = "hand";
    double[] mix = {1, 0, 0};
    int generation = 0;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--out" -> out = args[++i];
        case "--positions" -> positions = Long.parseLong(args[++i]);
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--seed" -> seed = Long.parseLong(args[++i]);
        case "--weights" -> weights = args[++i];
        case "--generation" -> generation = Integer.parseInt(args[++i]);
        case "--prev" -> prev = args[++i];
        case "--mix" -> {
          String[] m = args[++i].split(",");
          mix = new double[] {Double.parseDouble(m[0]), Double.parseDouble(m[1]), Double.parseDouble(m[2])};
        }
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Geometry g = new Geometry(8, 12);
    FeatureSpec spec = new FeatureSpec(g);
    Function<Geometry, Evaluator> evalFactory = Evaluators.factory(weights);
    Function<Geometry, Evaluator> prevFactory = Evaluators.factory(prev);
    Function<Geometry, Evaluator> handFactory = Evaluators.factory("hand");
    double[] cum = {mix[0] / (mix[0] + mix[1] + mix[2]), (mix[0] + mix[1]) / (mix[0] + mix[1] + mix[2])};
    Path dir = Path.of(out);
    Files.createDirectories(dir);
    AtomicLong written = new AtomicLong();
    AtomicInteger games = new AtomicInteger();
    AtomicLong[] reasons = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};
    long target = positions;
    long t0 = System.nanoTime();
    List<Thread> workers = new ArrayList<>();
    final long n = nodes;
    final int gen = generation;
    List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
    for (int w = 0; w < threads; w++) {
      int worker = w;
      SplittableRandom rnd = new SplittableRandom(seed * 7919 + w);
      Path shard = dir.resolve(String.format("shard-%02d.qcp", w));
      Thread t = new Thread(() -> {
        try (DataOutputStream os = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(shard), 1 << 16))) {
          Qcp.writeHeader(os, g.rows, g.cols, g.k, spec.specHash, gen);
          Evaluator eval = evalFactory.apply(g);
          Evaluator prevEval = prevFactory.apply(g);
          Evaluator handEval = handFactory.apply(g);
          Tactics tactics = new Tactics(g);
          long nextReport = 50_000;
          while (written.get() < target) {
            int id = games.incrementAndGet();
            List<Qcp.Rec> recs = new ArrayList<>();
            double u = rnd.nextDouble();
            Evaluator opponent = u < cum[0] ? eval : u < cum[1] ? prevEval : handEval;
            int mine = rnd.nextBoolean() ? 1 : 2;
            Evaluator[] byColour = {null, mine == 1 ? eval : opponent, mine == 2 ? eval : opponent};
            boolean[] record = {false, mine == 1 || opponent == eval, mine == 2 || opponent == eval};
            String reason = playGame(g, byColour, record, tactics, n, rnd, recs);
            for (Qcp.Rec r : recs) {
              r.write(os, id);
            }
            long total = written.addAndGet(recs.size());
            reasons[switch (reason) {
              case "six" -> 0;
              case "blast" -> 1;
              case "full" -> 2;
              default -> 3;
            }].incrementAndGet();
            if (worker == 0 && total >= nextReport) {
              nextReport = total + 50_000;
              double min = (System.nanoTime() - t0) / 6e10;
              System.out.printf(java.util.Locale.ROOT, "%.1f min: %d games, %d positions (%.0f per hour)%n", min,
                  games.get(), total, total / min * 60);
            }
          }
        } catch (Throwable e) {
          errors.add(e);
        }
      }, "selfplay-" + w);
      t.start();
      workers.add(t);
    }
    for (Thread t : workers) {
      t.join();
    }
    if (!errors.isEmpty()) {
      throw new IllegalStateException("self-play worker failed", errors.get(0));
    }
    double min = (System.nanoTime() - t0) / 6e10;
    String manifest = String.format(java.util.Locale.ROOT,
        "{\"generation\":%d,\"positions\":%d,\"games\":%d,\"nodes\":%d,\"threads\":%d,\"seed\":%d,"
            + "\"weights\":%s,\"prev\":\"%s\",\"mix\":[%s,%s,%s],\"specHash\":\"%s\",\"minutes\":%.1f,"
            + "\"six\":%d,\"blast\":%d,\"full\":%d,\"capped\":%d}",
        generation, written.get(), games.get(), nodes, threads, seed,
        weights == null ? "null" : "\"" + weights.replace('\\', '/') + "\"", prev.replace('\\', '/'), mix[0], mix[1],
        mix[2], Long.toHexString(spec.specHash), min,
        reasons[0].get(), reasons[1].get(), reasons[2].get(), reasons[3].get());
    Files.writeString(dir.resolve("manifest.json"), manifest, StandardCharsets.UTF_8);
    System.out.println(manifest);
  }

  /**
   * Plays one game from a random start with the given evaluation per colour and collects the records of the colours
   * marked in record; returns how it ended (six, blast, full, cap).
   */
  static String playGame(Geometry g, Evaluator[] byColour, boolean[] record, Tactics tactics, long nodes,
      SplittableRandom rnd, List<Qcp.Rec> recs) {
    Searcher[] engines = {null, newEngine(g, byColour[1]), newEngine(g, byColour[2])};
    Start s = null;
    while (s == null) {
      s = randomStart(g, engines[record[1] ? 1 : 2], rnd);
    }
    Board b = s.board;
    int[] counter = s.counter;
    int toMove = s.toMove;
    int turn = s.turn;
    int start = turn;
    Board view = new Board(g);
    Board probe = new Board(g);
    int flagsNext = 0;
    String reason;
    int winner = 0;
    while (true) {
      if (b.topRowFull()) {
        reason = "full";
        break;
      }
      if (turn >= MAX_TURNS) {
        reason = "cap";
        break;
      }
      int me = toMove;
      int opp = 3 - me;
      view.copyFrom(b);
      Searcher.Result r = engines[me].run(view, me, counter[me], counter[opp], turn, System.nanoTime(),
          Long.MAX_VALUE / 4, nodes);
      int move = r.move();
      if (move == Tactics.NONE) {
        reason = "full";
        break;
      }
      int score = r.score();
      boolean proven = Math.abs(score) > Searcher.MATE_BOUND;
      if (record[me] && !(proven && Searcher.MATE - Math.abs(score) <= 3)) {
        recs.add(new Qcp.Rec(b, me, counter[me], counter[opp], turn, Math.max(-32000, Math.min(32000, score)),
            r.depth(), proven ? Integer.signum(score) : 0, flagsNext));
      }
      flagsNext = 0;
      boolean ready = counter[me] >= 10;
      boolean oppReady = counter[opp] >= 10;
      if (!proven && turn - start < 20 && rnd.nextDouble() < 0.08) {
        int m = randomSafe(g, b, me, oppReady, false, tactics, probe, rnd);
        if (m != Tactics.NONE) {
          move = m;
          flagsNext = Qcp.FLAG_AFTER_RANDOM;
        }
      } else if (!proven && ready && rnd.nextDouble() < 0.1) {
        int m = randomSafe(g, b, me, oppReady, true, tactics, probe, rnd);
        if (m != Tactics.NONE) {
          move = m;
          flagsNext = Qcp.FLAG_AFTER_RANDOM;
        }
      }
      int col = Tactics.column(move);
      boolean special = Tactics.isSpecial(move) && ready;
      boolean win;
      if (special) {
        win = !b.isFull(col) && b.blast(col);
        counter[me] = 0;
      } else {
        win = b.make(col, me);
      }
      counter[me]++;
      turn++;
      if (win) {
        winner = me;
        reason = special ? "blast" : "six";
        break;
      }
      toMove = opp;
    }
    for (Qcp.Rec rec : recs) {
      rec.z = winner == 0 ? 0 : winner == rec.stm ? 1 : -1;
      rec.pliesToEnd = reason.equals("cap") ? 0xFFFF : turn - rec.turn;
      if (reason.equals("cap")) {
        rec.extraFlags |= Qcp.FLAG_ADJUDICATED;
      }
    }
    return reason;
  }

  /** A random start, or null if it made a line, filled a column or is too one-sided. */
  static Start randomStart(Geometry g, Searcher judge, SplittableRandom rnd) {
    boolean mid = rnd.nextInt(4) == 0;
    int plies = mid ? 20 + rnd.nextInt(41) : 2 + rnd.nextInt(7);
    Board b = new Board(g);
    int[] counter = new int[3];
    int toMove = 1;
    for (int i = 0; i < plies; i++) {
      int c = rnd.nextInt(g.cols);
      if (b.height(c) >= g.rows - 1) {
        return null;
      }
      boolean win;
      if (mid && counter[toMove] >= 10 && rnd.nextDouble() < 0.15) {
        win = b.blast(c);
        counter[toMove] = 0;
      } else {
        win = b.make(c, toMove);
      }
      if (win) {
        return null;
      }
      counter[toMove]++;
      toMove = 3 - toMove;
    }
    if (mid) {
      Board view = new Board(g);
      view.copyFrom(b);
      int v = judge.run(view, toMove, counter[toMove], counter[3 - toMove], plies, System.nanoTime(),
          Long.MAX_VALUE / 4, 3_000).score();
      if (Math.abs(v) >= 600) {
        return null;
      }
    }
    return new Start(b, counter, toMove, plies);
  }

  /** A uniformly random drop (or blast) after which the opponent has no immediate win, or NONE. */
  static int randomSafe(Geometry g, Board b, int me, boolean oppReady, boolean blast, Tactics tactics, Board probe,
      SplittableRandom rnd) {
    int[] options = new int[g.cols];
    int n = 0;
    for (int c = 0; c < g.cols; c++) {
      if (b.isFull(c)) {
        continue;
      }
      probe.copyFrom(b);
      boolean win = blast ? probe.blast(c) : probe.make(c, me);
      if (win || !tactics.winsNow(probe, 3 - me, oppReady)) {
        options[n++] = c;
      }
    }
    if (n == 0) {
      return Tactics.NONE;
    }
    int c = options[rnd.nextInt(n)];
    return blast ? c | Tactics.SPECIAL : c;
  }

  static Searcher newEngine(Geometry g, Evaluator eval) {
    return new Searcher(g, eval, new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(15));
  }

  private SelfPlayMain() {}
}
