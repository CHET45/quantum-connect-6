package ch.qc.tools;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.FeatureSpec;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Training records (.qcp, as SelfPlayMain writes them) from decided server games: every position before a move, with
 * the value of a fixed-node search for the side to move, the game's result for it and the plies to the end. The last
 * --hard-back plies of a game, where most games are decided by a double threat, get the larger --hard-nodes budget.
 * One directory per board size, one shard per size.
 *
 * <pre>
 * --out DIR --weights FILE --nodes N (20000) --hard-nodes N (200000) --hard-back K (14) --threads T (3) --gen G (900)
 * --sizes 15x30,8x12 (all) a.jsonl ...
 * </pre>
 */
public final class ServerQcp {
  private ServerQcp() {}

  record P(Board board, int stm, int cdMover, int cdOther, int turn, int pliesToEnd, int z, boolean hard) {}

  public static void main(String[] args) throws Exception {
    String out = null;
    String weights = "hand";
    long nodes = 20_000;
    long hardNodes = 200_000;
    int hardBack = 14;
    int threads = 3;
    int gen = 900;
    String sizes = null;
    List<String> files = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--out" -> out = args[++i];
        case "--weights" -> weights = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--hard-nodes" -> hardNodes = Long.parseLong(args[++i]);
        case "--hard-back" -> hardBack = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--gen" -> gen = Integer.parseInt(args[++i]);
        case "--sizes" -> sizes = args[++i];
        default -> files.add(args[i]);
      }
    }
    Map<String, ServerGames.Game> byKey = new LinkedHashMap<>();
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        try {
          ServerGames.Game g = ServerGames.game(line);
          byKey.putIfAbsent(g.key(), g);
        } catch (RuntimeException e) {
          // a record without a winner or moves
        }
      }
    }
    Map<String, List<ServerGames.Game>> bySize = new LinkedHashMap<>();
    for (ServerGames.Game g : byKey.values()) {
      String s = g.rows() + "x" + g.cols();
      if (g.rows() > 0 && g.cols() > 0 && (sizes == null || ("," + sizes + ",").contains("," + s + ","))) {
        bySize.computeIfAbsent(s, k -> new ArrayList<>()).add(g);
      }
    }
    Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    long t0 = System.nanoTime();
    try {
      for (Map.Entry<String, List<ServerGames.Game>> e : bySize.entrySet()) {
        List<ServerGames.Game> games = e.getValue();
        Geometry geo = new Geometry(games.get(0).rows(), games.get(0).cols());
        FeatureSpec spec = new FeatureSpec(geo);
        Path dir = Path.of(out, e.getKey());
        Files.createDirectories(dir);
        long[] counts = new long[3];
        try (DataOutputStream os = new DataOutputStream(new BufferedOutputStream(
            Files.newOutputStream(dir.resolve("shard-00.qcp")), 1 << 16))) {
          Qcp.writeHeader(os, geo.rows, geo.cols, geo.k, spec.specHash, gen);
          List<Future<List<Qcp.Rec>>> fut = new ArrayList<>();
          final long n = nodes;
          final long hn = hardNodes;
          final int hb = hardBack;
          for (ServerGames.Game g : games) {
            fut.add(pool.submit(() -> label(g, geo, ef.apply(geo), n, hn, hb)));
          }
          int id = 0;
          for (Future<List<Qcp.Rec>> f : fut) {
            List<Qcp.Rec> recs = f.get();
            id++;
            if (recs.isEmpty()) {
              continue;
            }
            counts[0]++;
            for (Qcp.Rec r : recs) {
              r.write(os, id);
              counts[1]++;
            }
          }
        }
        String manifest = String.format(Locale.ROOT,
            "{\"rows\":%d,\"cols\":%d,\"games\":%d,\"positions\":%d,\"nodes\":%d,\"hardNodes\":%d,\"hardBack\":%d,"
                + "\"weights\":\"%s\",\"specHash\":\"%s\",\"source\":\"server exports\"}%n",
            geo.rows, geo.cols, counts[0], counts[1], nodes, hardNodes, hardBack, weights.replace("\\", "/"),
            Long.toHexString(spec.specHash));
        Files.writeString(dir.resolve("manifest.json"), manifest, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "%s: %d games, %d positions, %.1f min%n", e.getKey(), counts[0], counts[1],
            (System.nanoTime() - t0) / 6e10);
      }
    } finally {
      pool.shutdown();
    }
  }

  /** All positions of one decided game with search values; empty if the game does not replay. */
  static List<Qcp.Rec> label(ServerGames.Game g, Geometry geo, Evaluator eval, long nodes, long hardNodes,
      int hardBack) {
    List<Qcp.Rec> recs = new ArrayList<>();
    List<int[]> toks = BlastThreats.tokens(g.moves());
    if (toks == null || toks.isEmpty()) {
      return recs;
    }
    Board b = new Board(geo);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    List<P> ps = new ArrayList<>();
    int winner = 0;
    for (int t = 0; t < toks.size(); t++) {
      int side = toks.get(t)[0];
      int col = toks.get(t)[1];
      boolean star = toks.get(t)[2] == 1;
      if (col < 0 || col >= geo.cols || b.isFull(col)) {
        return recs;
      }
      if (side != expected) {
        counter[expected]++;
        turn++;
      }
      Board copy = new Board(geo);
      copy.copyFrom(b);
      ps.add(new P(copy, side, counter[side], counter[3 - side], turn, 0, 0, false));
      boolean win;
      if (star) {
        win = b.blast(col);
        counter[side] = 0;
      } else {
        win = b.make(col, side);
      }
      counter[side]++;
      turn++;
      expected = 3 - side;
      if (win) {
        if (t != toks.size() - 1) {
          return recs;
        }
        winner = side;
      }
    }
    if (winner == 0 || winner != g.winner()) {
      return recs;
    }
    Searcher s = new Searcher(geo, eval, new TranspositionTable(18), Searcher.Options.DEFAULT.withNodeRule(false));
    for (int i = 0; i < ps.size(); i++) {
      P p = ps.get(i);
      boolean hard = ps.size() - i <= hardBack;
      Board work = new Board(geo);
      work.copyFrom(p.board());
      Searcher.Result r = s.run(work, p.stm(), Math.min(p.cdMover(), 10), Math.min(p.cdOther(), 10), p.turn(),
          Clock.nanos(), 3_600_000_000_000L, hard ? hardNodes : nodes);
      int score = r.score();
      int proven = score > Searcher.MATE_BOUND ? 1 : score < -Searcher.MATE_BOUND ? -1 : 0;
      Qcp.Rec rec = new Qcp.Rec(p.board(), p.stm(), p.cdMover(), p.cdOther(), p.turn(),
          Math.max(-32000, Math.min(32000, score)), r.depth(), proven, 0);
      rec.z = p.stm() == winner ? 1 : -1;
      rec.pliesToEnd = turn - p.turn();
      recs.add(rec);
    }
    return recs;
  }
}
