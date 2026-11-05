package ch.qc.tools;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
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
 * Detection of blast wins before they happen. Takes server games that ended with a winning blast and the positions 1
 * to --back plies before that blast. Each position is searched at a fixed node budget by every spec, and once by a
 * reference search (the first spec at --ref nodes) that labels it forced when it finds a mate score. A spec detects a
 * forced position when it finds the mate too: a win with the eventual winner to move, a loss with the loser to move.
 * The summary splits detection by who is to move, because the loser-to-move case is what the bot needs to avoid a
 * losing move, and by distance to the end.
 *
 * <pre>
 * --weights FILE (or hand) --nodes N (300000) --ref N (0 = no reference; then a position counts as forced when any
 * spec finds the mate) --specs "A;B" (SearchOptions, "" = the bot's) --back K (15) --size 15x30 (any) --games N
 * --threads T (4) --tt BITS (18) --rows FILE (per-position lines) --blast-within K (1: the game must end with the
 * winner's blast; K: the winner blasted in the last K plies, e.g. a blast that broke a block before the six) a.jsonl ...
 * </pre>
 */
public final class BlastThreats {
  private BlastThreats() {}

  record Pos(String game, int back, boolean winnerToMove, Geometry geo, byte[] cells, int stm, int cdStm, int cdOpp,
      int turn, Board board, int played) {}

  public static void main(String[] args) throws Exception {
    String weights = "hand";
    long nodes = 300_000;
    long ref = 0;
    String specText = "";
    int back = 15;
    String size = null;
    int maxGames = Integer.MAX_VALUE;
    int threads = 4;
    int ttBits = 18;
    String rowsFile = null;
    int within = 1;
    List<String> files = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--weights" -> weights = args[++i];
        case "--nodes" -> nodes = Long.parseLong(args[++i]);
        case "--ref" -> ref = Long.parseLong(args[++i]);
        case "--specs" -> specText = args[++i];
        case "--back" -> back = Integer.parseInt(args[++i]);
        case "--size" -> size = args[++i];
        case "--games" -> maxGames = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--tt" -> ttBits = Integer.parseInt(args[++i]);
        case "--rows" -> rowsFile = args[++i];
        case "--blast-within" -> within = Integer.parseInt(args[++i]);
        default -> files.add(args[i]);
      }
    }
    String[] specs = specText.split(";", -1);
    Map<String, ServerGames.Game> byId = new LinkedHashMap<>();
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          ServerGames.Game g = ServerGames.game(line);
          byId.putIfAbsent(g.key(), g);
        }
      }
    }
    List<Pos> positions = new ArrayList<>();
    int games = 0;
    for (ServerGames.Game g : byId.values()) {
      if (games >= maxGames || g.winner() == 0 || !winnerBlastNearEnd(g, within)) {
        continue;
      }
      ServerGames.Replay rp = ServerGames.replayAny(g, false, 0, 0);
      if (rp.problem != null || (size != null && !size.equals(ServerGames.sizeName(rp.geo)))) {
        continue;
      }
      List<Pos> ps = positions(g, rp.geo, back);
      if (ps.isEmpty()) {
        continue;
      }
      games++;
      positions.addAll(ps);
    }
    System.out.printf(Locale.ROOT, "%d games ending in a winning blast, %d positions, %d specs at %d nodes, ref %d%n",
        games, positions.size(), specs.length, nodes, ref);

    Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    int cols = specs.length + 1;
    int[][] score = new int[positions.size()][cols];
    int[][] depth = new int[positions.size()][cols];
    int[][] move = new int[positions.size()][cols];
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<?>> fut = new ArrayList<>();
    final long fNodes = nodes;
    final long fRef = ref;
    final int fTt = ttBits;
    for (int p = 0; p < positions.size(); p++) {
      final int pi = p;
      fut.add(pool.submit(() -> {
        Pos pos = positions.get(pi);
        Evaluator eval = ef.apply(pos.geo());
        for (int s = 0; s < cols; s++) {
          if (s == specs.length && fRef == 0) {
            break;
          }
          String spec = s == specs.length ? specs[0] : specs[s];
          long budget = s == specs.length ? fRef : fNodes;
          Searcher.Options o = SearchOptions.parse(spec).withNodeRule(false);
          Searcher se = new Searcher(pos.geo(), eval, new TranspositionTable(fTt), o);
          Board b = new Board(pos.geo());
          b.copyFrom(pos.board());
          Searcher.Result r = se.run(b, pos.stm(), pos.cdStm(), pos.cdOpp(), pos.turn(), Clock.nanos(),
              3_600_000_000_000L, budget);
          score[pi][s] = r.score();
          depth[pi][s] = r.depth();
          move[pi][s] = r.move();
        }
        return null;
      }));
    }
    for (Future<?> f : fut) {
      f.get();
    }

    // blunders: the loser to move is not lost (reference), but after the move played the winner has a forced win
    List<Integer> blunders = new ArrayList<>();
    if (fRef > 0) {
      for (int p = 1; p < positions.size(); p++) {
        Pos pos = positions.get(p);
        Pos after = positions.get(p - 1);
        if (!pos.winnerToMove() && after.game().equals(pos.game()) && after.back() == pos.back() - 1
            && after.winnerToMove() && score[p][specs.length] >= -Searcher.MATE_BOUND
            && score[p - 1][specs.length] > Searcher.MATE_BOUND) {
          blunders.add(p);
        }
      }
    }
    // [spec][0 same move as played, 1 other move that also loses, 2 saved]
    int[][] fate = new int[specs.length][3];
    List<Future<?>> fut2 = new ArrayList<>();
    StringBuilder blunderRows = new StringBuilder();
    for (int bi : blunders) {
      for (int s = 0; s < specs.length; s++) {
        final int fs = s;
        final int fp = bi;
        fut2.add(pool.submit(() -> {
          Pos pos = positions.get(fp);
          int m = move[fp][fs];
          int kind;
          if (m == pos.played()) {
            kind = 0;
          } else {
            Board b = new Board(pos.geo());
            b.copyFrom(pos.board());
            int c = Tactics.column(m);
            int cdNext;
            boolean win;
            if (Tactics.isSpecial(m)) {
              win = b.blast(c);
              cdNext = 1;
            } else {
              win = b.make(c, pos.stm());
              cdNext = Math.min(pos.cdStm() + 1, 10);
            }
            if (win) {
              kind = 2;
            } else {
              Searcher se = new Searcher(pos.geo(), ef.apply(pos.geo()), new TranspositionTable(fTt),
                  SearchOptions.parse(specs[0]).withNodeRule(false));
              Searcher.Result r = se.run(b, 3 - pos.stm(), pos.cdOpp(), cdNext, pos.turn() + 1, Clock.nanos(),
                  3_600_000_000_000L, fRef);
              kind = r.score() > Searcher.MATE_BOUND ? 1 : 2;
            }
          }
          synchronized (fate) {
            fate[fs][kind]++;
          }
          return null;
        }));
      }
      Pos pos = positions.get(bi);
      blunderRows.append(String.format(Locale.ROOT, "%s back %d played %s%n", pos.game().substring(0, 8), pos.back(),
          moveName(pos.played())));
    }
    for (Future<?> f : fut2) {
      f.get();
    }
    pool.shutdown();

    StringBuilder rows = new StringBuilder("game\tback\tmover\tspec\tscore\tdepth\n");
    int[] bands = {1, 4, 8, 12, 100};
    // [spec][role][band] detected, and forced counts [role][band]
    int[][][] hit = new int[cols][2][bands.length - 1];
    int[][] forced = new int[2][bands.length - 1];
    for (int p = 0; p < positions.size(); p++) {
      Pos pos = positions.get(p);
      int role = pos.winnerToMove() ? 0 : 1;
      int band = 0;
      while (pos.back() >= bands[band + 1]) {
        band++;
      }
      boolean isForced = false;
      for (int s = 0; s < cols; s++) {
        boolean ref0 = s == specs.length;
        if (ref0 && fRef == 0) {
          continue;
        }
        boolean found = role == 0 ? score[p][s] > Searcher.MATE_BOUND : score[p][s] < -Searcher.MATE_BOUND;
        if (found) {
          hit[s][role][band]++;
        }
        if (fRef > 0 ? ref0 && found : found) {
          isForced = true;
        }
        rows.append(pos.game().substring(0, 8)).append('\t').append(pos.back()).append('\t')
            .append(role == 0 ? 'W' : 'L').append('\t').append(ref0 ? "ref" : Integer.toString(s)).append('\t')
            .append(score[p][s]).append('\t').append(depth[p][s]).append('\n');
      }
      if (isForced) {
        forced[role][band]++;
      }
    }
    if (rowsFile != null) {
      Files.writeString(Path.of(rowsFile), rows, StandardCharsets.UTF_8);
    }
    int[] total = new int[2];
    for (int p = 0; p < positions.size(); p++) {
      total[positions.get(p).winnerToMove() ? 0 : 1]++;
    }
    System.out.println();
    System.out.println("Mates found (wins with the winner to move, losses with the loser to move); forced = "
        + (fRef > 0 ? "found by the reference" : "found by any spec") + ".");
    System.out.println();
    StringBuilder head = new StringBuilder("| spec | mover |");
    StringBuilder line = new StringBuilder("|---|---|");
    for (int k = 0; k + 1 < bands.length; k++) {
      head.append(String.format(Locale.ROOT, " back %d-%s |", bands[k],
          bands[k + 1] >= 100 ? "" : Integer.toString(bands[k + 1] - 1)));
      line.append("---|");
    }
    head.append(" all |");
    line.append("---|");
    System.out.println(head);
    System.out.println(line);
    for (int role = 0; role < 2; role++) {
      StringBuilder r = new StringBuilder("| forced | ").append(role == 0 ? "winner" : "loser").append(" |");
      int sum = 0;
      for (int k = 0; k + 1 < bands.length; k++) {
        r.append(' ').append(forced[role][k]).append(" |");
        sum += forced[role][k];
      }
      System.out.println(r.append(' ').append(sum).append(" of ").append(total[role]).append(" |"));
    }
    for (int s = 0; s < cols; s++) {
      if (s == specs.length && fRef == 0) {
        continue;
      }
      String name = s == specs.length ? "ref" : (specs[s].isEmpty() ? "bot" : specs[s]);
      for (int role = 0; role < 2; role++) {
        StringBuilder r = new StringBuilder("| ").append(name).append(" | ").append(role == 0 ? "winner" : "loser")
            .append(" |");
        int sum = 0;
        int fs = 0;
        for (int k = 0; k + 1 < bands.length; k++) {
          r.append(' ').append(hit[s][role][k]).append(" |");
          sum += hit[s][role][k];
          fs += forced[role][k];
        }
        System.out.println(r.append(String.format(Locale.ROOT, " %d (%.0f%%) |", sum, fs == 0 ? 0.0 : 100.0 * sum / fs)));
      }
    }
    if (fRef > 0) {
      printBlunders(specs, blunders.size(), fate, blunderRows);
    }
  }

  static void printBlunders(String[] specs, int n, int[][] fate, StringBuilder rows) {
    System.out.println();
    System.out.println("Losing moves: " + n + " positions where the loser to move was not lost (reference) but the move"
        + " played gave the winner a forced win.");
    System.out.println();
    System.out.println("| spec | same move | other move, also loses | saved |");
    System.out.println("|---|---|---|---|");
    for (int s = 0; s < specs.length; s++) {
      System.out.printf(Locale.ROOT, "| %s | %d | %d | %d |%n", specs[s].isEmpty() ? "bot" : specs[s], fate[s][0],
          fate[s][1], fate[s][2]);
    }
    System.out.println();
    System.out.print(rows);
  }

  private static String moveName(int m) {
    return Integer.toString(Tactics.column(m), 36) + (Tactics.isSpecial(m) ? "*" : "");
  }

  /** True if the winner made a blast in the last `within` plies of the game (1: the final move is a blast). */
  private static boolean winnerBlastNearEnd(ServerGames.Game g, int within) {
    String s = g.moves();
    List<int[]> plies = new ArrayList<>();
    for (int i = 0; i < s.length();) {
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      plies.add(new int[] {s.charAt(i) == 'F' ? 1 : 2, star ? 1 : 0});
      i += star ? 3 : 2;
    }
    for (int k = plies.size() - 1; k >= 0 && k >= plies.size() - within; k--) {
      if (plies.get(k)[0] == g.winner() && plies.get(k)[1] == 1) {
        return true;
      }
    }
    return false;
  }

  /** Positions 1..back plies before the final (winning) blast, with the counters the bot would have. */
  private static List<Pos> positions(ServerGames.Game g, Geometry geo, int back) {
    List<Pos> all = new ArrayList<>();
    Board b = new Board(geo);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    String s = g.moves();
    int i = 0;
    List<int[]> meta = new ArrayList<>();
    List<Board> boards = new ArrayList<>();
    int lastSide = 0;
    while (i < s.length()) {
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 36);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      if (side != expected) {
        counter[expected]++;
        turn++;
      }
      Board copy = new Board(geo);
      copy.copyFrom(b);
      boards.add(copy);
      meta.add(new int[] {side, counter[side], counter[3 - side], turn, star ? col | Tactics.SPECIAL : col});
      if (star) {
        b.blast(col);
        counter[side] = 0;
      } else {
        b.make(col, side);
      }
      counter[side]++;
      turn++;
      expected = 3 - side;
      lastSide = side;
    }
    if (lastSide != g.winner()) {
      return all;
    }
    int last = boards.size() - 1;
    for (int k = 1; k <= back && last - k >= 0; k++) {
      int[] m = meta.get(last - k);
      all.add(new Pos(g.id(), k, m[0] == g.winner(), geo, null, m[0], m[1], m[2], m[3], boards.get(last - k), m[4]));
    }
    return all;
  }
}
