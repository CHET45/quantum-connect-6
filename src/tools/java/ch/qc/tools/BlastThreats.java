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
      int turn, Board board, int played, int threats) {}

  /** Tokens of a server move string: {side 1 or 2, column, 1 if a blast}; null if a token is malformed. */
  static List<int[]> tokens(String s) {
    List<int[]> out = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
      char side = s.charAt(i);
      if ((side != 'F' && side != 'S') || i + 1 >= s.length()) {
        return null;
      }
      int col;
      if (s.charAt(i + 1) == '[') {
        int end = s.indexOf(']', i + 2);
        if (end < 0) {
          return null;
        }
        col = Integer.parseInt(s.substring(i + 2, end));
        i = end + 1;
      } else {
        col = Character.digit(s.charAt(i + 1), 36);
        if (col < 0) {
          return null;
        }
        i += 2;
      }
      boolean star = i < s.length() && s.charAt(i) == '*';
      if (star) {
        i++;
      }
      out.add(new int[] {side == 'F' ? 1 : 2, col, star ? 1 : 0});
    }
    return out;
  }

  /** Columns where the colour wins by dropping now. */
  static int playableWins(Board b, Geometry g, int colour) {
    int k = 0;
    for (int c = b.nextThreatColumn(0); c >= 0; c = b.nextThreatColumn(c + 1)) {
      if (b.threats(colour, g.index(c, b.height(c))) > 0) {
        k++;
      }
    }
    return k;
  }

  private static final String DIRS = "HV/\\";

  /**
   * Shape of an unstoppable threat of the colour, made by a drop on cell moveCell: "fork" (two playable wins) or "stack"
   * (a playable win with one above), the directions of the lines through the winning cells (H, V, / and \), and
   * "+move" when the drop itself lies in every one of those lines.
   */
  static String shape(Board b, Geometry g, int colour, int moveCell) {
    List<Integer> cellsToWin = new ArrayList<>();
    boolean stack = false;
    for (int c = b.nextThreatColumn(0); c >= 0; c = b.nextThreatColumn(c + 1)) {
      int h = b.height(c);
      if (b.threats(colour, g.index(c, h)) > 0) {
        cellsToWin.add(g.index(c, h));
        if (h + 1 < g.rows && b.threats(colour, g.index(c, h + 1)) > 0) {
          cellsToWin.add(g.index(c, h + 1));
          stack = true;
        }
      }
    }
    StringBuilder dirs = new StringBuilder();
    boolean allThroughMove = true;
    for (int cell : cellsToWin) {
      for (int j = g.cellStart[cell]; j < g.cellStart[cell + 1]; j++) {
        int w = g.cellWin[j];
        int mine = 0;
        boolean hasMove = false;
        for (int i = 0; i < g.k; i++) {
          int wc = g.winCells[w * g.k + i];
          if (wc != cell && b.cell(wc / g.rows, wc % g.rows) == colour) {
            mine++;
          }
          hasMove |= wc == moveCell;
        }
        if (mine == g.k - 1) {
          dirs.append(DIRS.charAt(g.winDir[w]));
          allThroughMove &= hasMove;
        }
      }
    }
    char[] d = dirs.toString().toCharArray();
    java.util.Arrays.sort(d);
    return (stack ? "stack " : "fork ") + new String(d) + (allThroughMove && moveCell >= 0 ? " +move" : "");
  }

  /** A playable win of the colour with another of its wins right above it. */
  static boolean stacked(Board b, Geometry g, int colour) {
    for (int c = b.nextThreatColumn(0); c >= 0; c = b.nextThreatColumn(c + 1)) {
      int h = b.height(c);
      if (h + 1 < g.rows && b.threats(colour, g.index(c, h)) > 0 && b.threats(colour, g.index(c, h + 1)) > 0) {
        return true;
      }
    }
    return false;
  }

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
    boolean multi = false;
    String suiteFile = null;
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
        case "--anchor" -> multi = args[++i].equals("multi");
        case "--suite" -> suiteFile = args[++i];
        default -> files.add(args[i]);
      }
    }
    String[] specs = specText.split(";", -1);
    Map<String, ServerGames.Game> byId = new LinkedHashMap<>();
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          ServerGames.Game g;
          try {
            g = ServerGames.game(line);
          } catch (RuntimeException e) {
            continue; // a record without a winner or moves
          }
          byId.putIfAbsent(g.key(), g);
        }
      }
    }
    List<Pos> positions = new ArrayList<>();
    int games = 0;
    Map<String, Geometry> geos = new LinkedHashMap<>();
    MultiStats st = new MultiStats();
    for (ServerGames.Game g : byId.values()) {
      if (games >= maxGames || g.winner() == 0 || (!multi && !winnerBlastNearEnd(g, within))) {
        continue;
      }
      Geometry geo;
      if (multi) {
        if (g.rows() <= 0 || g.cols() <= 0 || (size != null && !size.equals(g.rows() + "x" + g.cols()))) {
          continue;
        }
        geo = geos.computeIfAbsent(g.rows() + "x" + g.cols(), k -> new Geometry(g.rows(), g.cols()));
      } else {
        ServerGames.Replay rp = ServerGames.replayAny(g, false, 0, 0);
        if (rp.problem != null || (size != null && !size.equals(ServerGames.sizeName(rp.geo)))) {
          continue;
        }
        geo = rp.geo;
      }
      List<Pos> ps = positions(g, geo, back, multi, st);
      if (ps.isEmpty()) {
        continue;
      }
      games++;
      positions.addAll(ps);
    }
    System.out.printf(Locale.ROOT, "%d games ending in a %s, %d positions, %d specs at %d nodes, ref %d%n",
        games, multi ? "win after an unstoppable threat" : "winning blast", positions.size(), specs.length, nodes, ref);
    if (multi) {
      st.print();
    }
    if (nodes <= 0) {
      writeSuite(suiteFile, positions, null, null, 0);
      return;
    }

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
    writeSuite(suiteFile, positions, score, move, fRef > 0 ? specs.length : -1);
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

  /** One JSON line per position; scores and moves of spec 0 and of the reference column when searched. */
  static void writeSuite(String file, List<Pos> positions, int[][] score, int[][] move, int refCol)
      throws java.io.IOException {
    if (file == null) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (int p = 0; p < positions.size(); p++) {
      Pos pos = positions.get(p);
      byte[] cells = pos.board().cells();
      StringBuilder cs = new StringBuilder(cells.length);
      for (byte c : cells) {
        cs.append((char) ('0' + c));
      }
      String bot = score == null ? "null" : Integer.toString(score[p][0]);
      String botMove = move == null ? "" : moveName(move[p][0]);
      String ref = score == null || refCol < 0 ? "null" : Integer.toString(score[p][refCol]);
      String refMove = move == null || refCol < 0 ? "" : moveName(move[p][refCol]);
      sb.append("{\"game\":\"").append(pos.game()).append("\",\"rows\":").append(pos.geo().rows)
          .append(",\"cols\":").append(pos.geo().cols).append(",\"back\":").append(pos.back())
          .append(",\"winnerToMove\":").append(pos.winnerToMove()).append(",\"stm\":").append(pos.stm())
          .append(",\"cdStm\":").append(pos.cdStm()).append(",\"cdOpp\":").append(pos.cdOpp())
          .append(",\"turn\":").append(pos.turn()).append(",\"played\":\"").append(moveName(pos.played()))
          .append("\",\"threats\":").append(pos.threats()).append(",\"bot\":").append(bot)
          .append(",\"botMove\":\"").append(botMove).append("\",\"ref\":").append(ref)
          .append(",\"refMove\":\"").append(refMove).append("\",\"cells\":\"").append(cs).append("\"}\n");
    }
    Files.writeString(Path.of(file), sb, StandardCharsets.UTF_8);
  }

  private static String moveName(int m) {
    return Integer.toString(Tactics.column(m), 36) + (Tactics.isSpecial(m) ? "*" : "");
  }

  /** True if the winner made a blast in the last `within` plies of the game (1: the final move is a blast). */
  private static boolean winnerBlastNearEnd(ServerGames.Game g, int within) {
    List<int[]> plies = tokens(g.moves());
    if (plies == null) {
      return false;
    }
    for (int k = plies.size() - 1; k >= 0 && k >= plies.size() - within; k--) {
      if (plies.get(k)[0] == g.winner() && plies.get(k)[2] == 1) {
        return true;
      }
    }
    return false;
  }

  /** Counts of the multi-threat anchors found. */
  static final class MultiStats {
    int decided;
    int anchored;
    final int[] byThreats = new int[8];
    int stackedOnly;
    final int[] toEnd = new int[5];
    final Map<String, int[]> byWinner = new java.util.TreeMap<>();
    final Map<String, int[]> bySize = new java.util.TreeMap<>();
    final Map<String, int[]> byShape = new java.util.TreeMap<>();

    void print() {
      System.out.println("shapes of the unstoppable threat (count), top 20:");
      byShape.entrySet().stream().sorted((x, y) -> y.getValue()[0] - x.getValue()[0]).limit(20)
          .forEach(e -> System.out.printf(Locale.ROOT, "  %-24s %5d%n", e.getKey(), e.getValue()[0]));
      System.out.printf(Locale.ROOT, "decided games replayed: %d; won after an unstoppable threat: %d (%.1f%%)%n",
          decided, anchored, decided == 0 ? 0 : 100.0 * anchored / decided);
      System.out.printf(Locale.ROOT, "playable wins made by the anchor move: 1 with a win above it %d, 2: %d, 3: %d,"
          + " 4: %d, 5+: %d%n", stackedOnly, byThreats[2], byThreats[3], byThreats[4],
          byThreats[5] + byThreats[6] + byThreats[7]);
      System.out.printf(Locale.ROOT, "plies from the anchor move to the end: 1-2 %d, 3-4 %d, 5-8 %d, 9-16 %d, 17+ %d%n",
          toEnd[0], toEnd[1], toEnd[2], toEnd[3], toEnd[4]);
      System.out.println("by size (anchored / decided):");
      bySize.forEach((k, v) -> System.out.printf(Locale.ROOT, "  %-8s %5d / %5d%n", k, v[0], v[1]));
      System.out.println("winners by name (anchored wins / decided wins), top 30:");
      byWinner.entrySet().stream().sorted((x, y) -> y.getValue()[1] - x.getValue()[1]).limit(30)
          .forEach(e -> System.out.printf(Locale.ROOT, "  %-34s %4d / %4d%n", e.getKey(), e.getValue()[0],
              e.getValue()[1]));
    }
  }

  /**
   * Positions 1..back plies before the anchor, with the counters the bot would have: the final (winning) blast, or with
   * multi the winner's last move that created an unstoppable threat (back 1 = the position just before that move).
   */
  private static List<Pos> positions(ServerGames.Game g, Geometry geo, int back, boolean multi, MultiStats st) {
    List<Pos> all = new ArrayList<>();
    List<int[]> toks = tokens(g.moves());
    if (toks == null || toks.isEmpty()) {
      return all;
    }
    Board b = new Board(geo);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    List<int[]> meta = new ArrayList<>();
    List<Board> boards = new ArrayList<>();
    int lastSide = 0;
    int anchor = -1;
    int anchorThreats = 0;
    String anchorShape = "";
    boolean wasUnstoppable = false;
    for (int t = 0; t < toks.size(); t++) {
      int side = toks.get(t)[0];
      int col = toks.get(t)[1];
      boolean star = toks.get(t)[2] == 1;
      if (col < 0 || col >= geo.cols || b.isFull(col)) {
        return all;
      }
      if (side != expected) {
        counter[expected]++;
        turn++;
      }
      Board copy = new Board(geo);
      copy.copyFrom(b);
      boards.add(copy);
      meta.add(new int[] {side, counter[side], counter[3 - side], turn, star ? col | Tactics.SPECIAL : col});
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
      lastSide = side;
      if (win) {
        if (t != toks.size() - 1) {
          return all;
        }
        break;
      }
      if (multi && side == g.winner()) {
        int k = playableWins(b, geo, side);
        boolean un = playableWins(b, geo, 3 - side) == 0 && (k >= 2 || (k == 1 && stacked(b, geo, side)));
        if (un && !wasUnstoppable) {
          anchor = t;
          anchorThreats = k;
          anchorShape = shape(b, geo, side, star ? -1 : geo.index(col, b.height(col) - 1));
        }
        wasUnstoppable = un;
      }
    }
    if (lastSide != g.winner()) {
      return all;
    }
    int last = boards.size() - 1;
    if (multi) {
      st.decided++;
      String wn = g.name()[g.winner() - 1];
      String sz = geo.rows + "x" + geo.cols;
      st.byWinner.computeIfAbsent(wn, k -> new int[2])[1]++;
      st.bySize.computeIfAbsent(sz, k -> new int[2])[1]++;
      if (anchor < 0) {
        return all;
      }
      st.anchored++;
      st.byWinner.get(wn)[0]++;
      st.byShape.computeIfAbsent(anchorShape, k -> new int[1])[0]++;
      st.bySize.get(sz)[0]++;
      if (anchorThreats >= 2) {
        st.byThreats[Math.min(7, anchorThreats)]++;
      } else {
        st.stackedOnly++;
      }
      int d = last - anchor;
      st.toEnd[d <= 2 ? 0 : d <= 4 ? 1 : d <= 8 ? 2 : d <= 16 ? 3 : 4]++;
    }
    int top = multi ? anchor + 1 : last;
    for (int k = 1; k <= back && top - k >= 0; k++) {
      int[] m = meta.get(top - k);
      all.add(new Pos(g.id(), k, m[0] == g.winner(), geo, null, m[0], m[1], m[2], m[3], boards.get(top - k), m[4],
          multi ? anchorThreats : 0));
    }
    return all;
  }
}
