package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * External validation: replays real server games (matches.jsonl, see {@link ServerGames}) and measures how well each
 * evaluation predicts the winner from the positions of those games. Per board size, game group and phase (quarter of
 * the game's plies) it prints the AUC of "the side to move wins" and the share of positions whose sign agrees with the
 * result. Every game has the same total weight in a cell, so long games do not dominate. Group "all" is every game,
 * group "strong" the games in which both bots match --strong. Games shorter than --min-plies are left out, as are
 * games that do not replay on their recorded size.
 *
 * <p>With --nodes N the value is a fixed-node search instead of the static evaluation; --stride S then keeps every
 * S-th position of a game.
 *
 * <pre>
 * ServerEval FILE.jsonl [--weights NAME=eval.qcw ...] [--strong REGEX] [--min-plies 16] [--nodes N] [--stride S]
 *     [--sizes 15x11,8x12] [--dump DIR --dump-weights W --dump-nodes N [--dump-holdout 5]]
 * </pre>
 *
 * <p>With --dump every position of the selected games goes to DIR/ROWSxCOLS/shard-00.qcp, labelled with a fixed-node
 * search of --dump-weights ("hand" for the hand tables) and the real result; games whose hash modulo --dump-holdout is
 * 0 are left out and only those are measured afterwards.
 */
public final class ServerEval {
  private static final int COOLDOWN = 10;
  private static final int PHASES = 4;

  private record Sample(double score, boolean win, double weight) {}

  private ServerEval() {}

  public static void main(String[] args) throws Exception {
    List<String> weightArgs = new ArrayList<>();
    String strong = "Savi|beater";
    int minPlies = 16;
    long nodes = 0;
    int stride = 1;
    Set<String> only = null;
    String dump = null;
    String dumpWeights = "hand";
    long dumpNodes = 4000;
    int holdout = 0;
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--weights" -> weightArgs.add(args[++i]);
        case "--strong" -> strong = args[++i];
        case "--min-plies" -> minPlies = Integer.parseInt(args[++i]);
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--stride" -> stride = Integer.parseInt(args[++i]);
        case "--sizes" -> only = Set.of(args[++i].split(","));
        case "--dump" -> dump = args[++i];
        case "--dump-weights" -> dumpWeights = args[++i];
        case "--dump-nodes" -> dumpNodes = Long.parseLong(args[++i]);
        case "--dump-holdout" -> holdout = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    Pattern strongNames = Pattern.compile(strong);
    Map<String, String> weights = new LinkedHashMap<>();
    weights.put("hand", "hand");
    for (String w : weightArgs) {
      String[] kv = w.split("=", 2);
      weights.put(kv[0], kv[1]);
    }
    Map<String, java.util.function.Function<Geometry, Evaluator>> factories = new LinkedHashMap<>();
    weights.forEach((name, file) -> factories.put(name, Evaluators.factory(file)));

    Set<String> seen = new LinkedHashSet<>();
    List<ServerGames.Game> games = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      ServerGames.Game g = ServerGames.game(line);
      if (g.rows() > 0 && g.cols() > 0 && seen.add(g.key())
          && (only == null || only.contains(g.rows() + "x" + g.cols()))) {
        games.add(g);
      }
    }
    Dumper dumper = dump == null ? null : new Dumper(Path.of(dump), Evaluators.factory(dumpWeights), dumpNodes);
    // cell key: size|group|phase|evaluator
    Map<String, List<Sample>> cells = new TreeMap<>();
    int used = 0;
    int bad = 0;
    int heldOut = 0;
    for (ServerGames.Game game : games) {
      Geometry geo = ServerGames.geometry(game.rows(), game.cols());
      ServerGames.Replay check = ServerGames.replay(geo, game, false);
      if (check.problem != null) {
        bad++;
        continue;
      }
      if (check.plies < minPlies) {
        continue;
      }
      boolean isStrong = strongNames.matcher(game.name()[0]).find() && strongNames.matcher(game.name()[1]).find();
      boolean held = holdout > 0 && Math.floorMod(game.key().hashCode(), holdout) == 0;
      if (dumper != null && !held) {
        replay(geo, game, check.plies, 1, (b, stm, cdS, cdO, ply, turn) -> dumper.add(geo, b, stm, cdS, cdO, turn, game.winner(), ply));
        used++;
        continue;
      }
      heldOut += held ? 1 : 0;
      used++;
      for (Map.Entry<String, java.util.function.Function<Geometry, Evaluator>> e : factories.entrySet()) {
        Evaluator ev = e.getValue().apply(geo);
        Searcher engine = nodes > 0 ? new Searcher(geo, ev, new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16)) : null;
        long budget = nodes;
        double[] counts = new double[PHASES];
        List<double[]> rows = new ArrayList<>();
        int total = check.plies;
        replay(geo, game, total, stride, (b, stm, cdS, cdO, ply, turn) -> {
          int phase = Math.min(PHASES - 1, PHASES * ply / total);
          double value;
          if (engine != null) {
            Board view = new Board(geo);
            view.copyFrom(b);
            value = engine.run(view, stm, cdS, cdO, turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, budget).score();
          } else {
            value = ev.evaluate(b, stm, cdS, cdO);
          }
          counts[phase]++;
          rows.add(new double[] {phase, value, stm == game.winner() ? 1 : 0});
        });
        String size = game.rows() + "x" + game.cols();
        for (double[] r : rows) {
          int phase = (int) r[0];
          Sample s = new Sample(r[1], r[2] > 0, 1.0 / counts[phase]);
          add(cells, size + "|all|" + phase + "|" + e.getKey(), s);
          add(cells, size + "|all|*|" + e.getKey(), s);
          if (isStrong) {
            add(cells, size + "|strong|" + phase + "|" + e.getKey(), s);
            add(cells, size + "|strong|*|" + e.getKey(), s);
          }
        }
      }
    }
    if (dumper != null) {
      dumper.close();
    }
    System.out.printf(Locale.ROOT, "%d unique games, %d used (%d held out), %d do not replay%n", games.size(), used, heldOut, bad);
    String last = "";
    for (Map.Entry<String, List<Sample>> e : cells.entrySet()) {
      String[] k = e.getKey().split("\\|");
      String head = k[0] + " " + k[1] + " phase " + k[2];
      if (!head.equals(last)) {
        System.out.printf(Locale.ROOT, "%n%s (%d positions)%n", head, e.getValue().size());
        last = head;
      }
      double[] r = measure(e.getValue());
      System.out.printf(Locale.ROOT, "  %-16s AUC %.4f  sign right %.3f%n", k[3], r[0], r[1]);
    }
  }

  private static void add(Map<String, List<Sample>> cells, String key, Sample s) {
    cells.computeIfAbsent(key, x -> new ArrayList<>()).add(s);
  }

  /** Weighted AUC (ties half) and the weighted share of non-zero values with the right sign. */
  static double[] measure(List<Sample> list) {
    Sample[] a = list.toArray(new Sample[0]);
    Arrays.sort(a, (x, y) -> Double.compare(x.score(), y.score()));
    double lossBelow = 0;
    double winW = 0;
    double lossW = 0;
    double pairs = 0;
    double right = 0;
    double signed = 0;
    for (int i = 0; i < a.length;) {
      int j = i;
      double w = 0;
      double l = 0;
      while (j < a.length && a[j].score() == a[i].score()) {
        if (a[j].win()) {
          w += a[j].weight();
        } else {
          l += a[j].weight();
        }
        j++;
      }
      pairs += w * (lossBelow + l / 2);
      lossBelow += l;
      winW += w;
      lossW += l;
      if (a[i].score() != 0) {
        signed += w + l;
        right += a[i].score() > 0 ? w : l;
      }
      i = j;
    }
    return new double[] {winW * lossW == 0 ? 0.5 : pairs / (winW * lossW), signed == 0 ? 0.5 : right / signed};
  }

  /** Visitor of the positions before each own move of a game. */
  private interface Visitor {
    void at(Board b, int stm, int cdStm, int cdOpp, int ply, int turn);
  }

  /** Replays a game that is known to be consistent, skipped turns counted as in {@link ServerGames#replay}. */
  private static void replay(Geometry g, ServerGames.Game game, int total, int stride, Visitor v) {
    Board b = new Board(g);
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
      if (ply % stride == 0) {
        v.at(b, side, counter[side], counter[3 - side], ply, turn);
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

  /** Writes server positions as .qcp records with a search value of the given evaluation and the real result. */
  private static final class Dumper {
    private final Path root;
    private final java.util.function.Function<Geometry, Evaluator> factory;
    private final long nodes;
    private final Map<String, java.io.DataOutputStream> out = new LinkedHashMap<>();
    private final Map<String, Searcher> engines = new LinkedHashMap<>();
    private int gameId;
    private int lastPly = Integer.MAX_VALUE;
    private long written;

    Dumper(Path root, java.util.function.Function<Geometry, Evaluator> factory, long nodes) {
      this.root = root;
      this.factory = factory;
      this.nodes = nodes;
    }

    void add(Geometry g, Board b, int stm, int cdS, int cdO, int turn, int winner, int ply) {
      try {
        if (ply <= lastPly) {
          gameId++;
        }
        lastPly = ply;
        String key = g.rows + "x" + g.cols;
        java.io.DataOutputStream os = out.get(key);
        if (os == null) {
          Path dir = root.resolve(key);
          Files.createDirectories(dir);
          os = new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(dir.resolve("shard-00.qcp")), 1 << 16));
          Qcp.writeHeader(os, g.rows, g.cols, g.k, new ch.qc.bot.eval.FeatureSpec(g).specHash, 0);
          out.put(key, os);
        }
        Searcher engine = engines.computeIfAbsent(key, x -> new Searcher(g, factory.apply(g), new TranspositionTable(18), Searcher.Options.DEFAULT, new Vcf.Table(16)));
        Board view = new Board(g);
        view.copyFrom(b);
        Searcher.Result r = engine.run(view, stm, cdS, cdO, turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, nodes);
        int score = r.score();
        boolean proven = Math.abs(score) > Searcher.MATE_BOUND;
        if (proven && Searcher.MATE - Math.abs(score) <= 3) {
          return;
        }
        Qcp.Rec rec = new Qcp.Rec(b, stm, cdS, cdO, turn, Math.max(-32000, Math.min(32000, score)), r.depth(), proven ? Integer.signum(score) : 0, 0);
        rec.z = stm == winner ? 1 : -1;
        rec.pliesToEnd = 0;
        rec.write(os, gameId);
        written++;
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }

    void close() throws java.io.IOException {
      for (java.io.DataOutputStream os : out.values()) {
        os.close();
      }
      System.out.printf(Locale.ROOT, "%d positions dumped to %s%n", written, root);
    }
  }
}
