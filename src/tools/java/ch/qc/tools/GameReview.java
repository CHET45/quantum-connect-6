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

/**
 * Reviews server games our team lost ({@link ServerGames} exports): before every ply a short search (the bot's
 * stop) and a deep one, both from the mover's view printed in our view, so the ply where our deep score drops is
 * the mistake and the short search shows whether the bot, given more time, would have seen it. Each game is
 * replayed on its own board size (8x12, 15x11, 15x22; see {@link ServerGames#replayAny}) and columns are base 36.
 * After each game a summary gives the first ply where the deep and the short search see a forced loss, the first
 * ply where the deep score is -1 or worse, the largest fall of the deep score and how often the searches
 * disagree with the move played.
 *
 * <pre>
 * --team NAME (default "No beaches?") --weights FILE (or hand) --short MS (62) --deep MS (2000) --vs SUBSTRING
 * --only 15x11 (board size) --id SUBSTRING (game id) --ours-only (search only our own moves) --from-ply N (first
 * ply searched, 1-based; earlier plies are only replayed) --budget-s N (no new search once N seconds are used, the
 * game is marked truncated) --games N (all) a.jsonl b.jsonl
 * </pre>
 */
public final class GameReview {
  private GameReview() {}

  /** One searched ply, scores in our view (1/256 logit, or a mate score). */
  private record Row(int ply, String played, String shortMove, int shortScore, String deepMove, int deepScore,
      int depth) {}

  private static final long START = System.nanoTime();

  public static void main(String[] args) throws Exception {
    String team = "No beaches?";
    String weights = "hand";
    long shortMs = 62;
    long deepMs = 2000;
    String vs = "";
    String only = null;
    String id = "";
    boolean oursOnly = false;
    int fromPly = 1;
    long budgetS = Long.MAX_VALUE;
    int maxGames = Integer.MAX_VALUE;
    java.util.List<String> files = new java.util.ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--team" -> team = args[++i];
        case "--weights" -> weights = args[++i];
        case "--short" -> shortMs = Long.parseLong(args[++i]);
        case "--deep" -> deepMs = Long.parseLong(args[++i]);
        case "--vs" -> vs = args[++i];
        case "--only" -> only = args[++i];
        case "--id" -> id = args[++i];
        case "--ours-only" -> oursOnly = true;
        case "--from-ply" -> fromPly = Integer.parseInt(args[++i]);
        case "--budget-s" -> budgetS = Long.parseLong(args[++i]);
        case "--games" -> maxGames = Integer.parseInt(args[++i]);
        default -> files.add(args[i]);
      }
    }
    Map<String, ServerGames.Game> byId = new LinkedHashMap<>();
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          ServerGames.Game g = ServerGames.game(line);
          byId.putIfAbsent(g.id(), g);
        }
      }
    }
    java.util.function.Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    Map<String, Searcher[]> engines = new LinkedHashMap<>();
    int done = 0;
    for (ServerGames.Game g : byId.values()) {
      int us = team.equals(g.team()[0]) ? 1 : team.equals(g.team()[1]) ? 2 : 0;
      if (us == 0 || g.winner() == us || !g.name()[2 - us].contains(vs) || !g.id().contains(id) || done >= maxGames) {
        continue;
      }
      ServerGames.Replay rp = ServerGames.replayAny(g, false, 0, 0);
      if (rp.problem != null) {
        System.out.printf(Locale.ROOT, "%nskipped %s: no board size replays consistently (%s)%n", g.id(), rp.problem);
        continue;
      }
      Geometry geo = rp.geo;
      if (only != null && !only.equals(ServerGames.sizeName(geo))) {
        continue;
      }
      if ((System.nanoTime() - START) / 1_000_000_000L >= budgetS) {
        System.out.printf(Locale.ROOT, "%nbudget of %d s used, game %s not reviewed%n", budgetS, g.id());
        continue;
      }
      done++;
      Searcher[] pair = engines.computeIfAbsent(ServerGames.sizeName(geo), k -> {
        Evaluator eval = ef.apply(geo);
        return new Searcher[] {new Searcher(geo, eval, new TranspositionTable(18)),
            new Searcher(geo, eval, new TranspositionTable(22))};
      });
      review(geo, pair[0], pair[1], g, us, shortMs * 1_000_000L, deepMs * 1_000_000L, oursOnly, fromPly, budgetS);
    }
  }

  private static void review(Geometry geo, Searcher shortS, Searcher deepS, ServerGames.Game g, int us, long shortNs,
      long deepNs, boolean oursOnly, int fromPly, long budgetS) {
    System.out.printf(Locale.ROOT, "%n## %s (%s) vs %s, game %s, board %s%n%n", g.name()[us - 1], us == 1 ? "F" : "S",
        g.name()[2 - us], g.id(), ServerGames.sizeName(geo));
    System.out.println("| ply | mover | played | short move | short (our view) | deep move | deep (our view) | depth |");
    System.out.println("|---|---|---|---|---|---|---|---|");
    Board b = new Board(geo);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    int ply = 0;
    boolean truncated = false;
    List<Row> rows = new ArrayList<>();
    String s = g.moves();
    int i = 0;
    while (i < s.length()) {
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 36);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      if (side != expected) {
        counter[expected]++;
        turn++;
      }
      boolean search = (!oursOnly || side == us) && ply + 1 >= fromPly;
      if (search && (System.nanoTime() - START) / 1_000_000_000L >= budgetS) {
        truncated = true;
        search = false;
      }
      if (search) {
        int sign = side == us ? 1 : -1;
        Board view = new Board(geo);
        view.copyFrom(b);
        Searcher.Result rs = shortS.run(view, side, counter[side], counter[3 - side], turn, Clock.nanos(), shortNs);
        view.copyFrom(b);
        Searcher.Result rd = deepS.run(view, side, counter[side], counter[3 - side], turn, Clock.nanos(), deepNs);
        String played = Integer.toString(col, 36) + (star ? "*" : "");
        System.out.printf(Locale.ROOT, "| %d | %s | %s | %s | %s | %s | %s | %d |%n", ply + 1, side == 1 ? "F" : "S",
            played, move(rs.move()), score(sign * rs.score()), move(rd.move()), score(sign * rd.score()), rd.depth());
        System.out.flush();
        if (side == us) {
          rows.add(new Row(ply + 1, played, move(rs.move()), sign * rs.score(), move(rd.move()), sign * rd.score(),
              rd.depth()));
        }
      }
      if (star) {
        b.blast(col);
        counter[side] = 0;
      } else {
        b.make(col, side);
      }
      counter[side]++;
      turn++;
      ply++;
      expected = 3 - side;
    }
    summary(rows, ply, truncated);
  }

  /** Short digest of one reviewed game: where each search first sees the loss, and where the score falls. */
  private static void summary(List<Row> rows, int plies, boolean truncated) {
    System.out.printf(Locale.ROOT, "%nSummary: game of %d plies, %d of our moves searched%s.%n", plies, rows.size(),
        truncated ? " (TRUNCATED by the time budget)" : "");
    if (rows.isEmpty()) {
      return;
    }
    int deepDiff = 0;
    int shortDiff = 0;
    int shortDeepDiff = 0;
    Row firstDeepLoss = null;
    Row firstShortLoss = null;
    Row firstDeepMinus1 = null;
    Row biggest = null;
    int biggestDrop = 0;
    Row lastNonNegative = null;
    for (int k = 0; k < rows.size(); k++) {
      Row r = rows.get(k);
      deepDiff += r.deepMove.equals(r.played) ? 0 : 1;
      shortDiff += r.shortMove.equals(r.played) ? 0 : 1;
      shortDeepDiff += r.shortMove.equals(r.deepMove) ? 0 : 1;
      if (firstDeepLoss == null && r.deepScore < -Searcher.MATE_BOUND) {
        firstDeepLoss = r;
      }
      if (firstShortLoss == null && r.shortScore < -Searcher.MATE_BOUND) {
        firstShortLoss = r;
      }
      if (firstDeepMinus1 == null && r.deepScore <= -256) {
        firstDeepMinus1 = r;
      }
      if (r.deepScore >= 0) {
        lastNonNegative = r;
      }
      if (k + 1 < rows.size()) {
        int drop = clamp(r.deepScore) - clamp(rows.get(k + 1).deepScore);
        if (drop > biggestDrop) {
          biggestDrop = drop;
          biggest = r;
        }
      }
    }
    int n = rows.size();
    System.out.printf(Locale.ROOT, "- played move differs from the deep move in %d of %d, from the short move in %d; "
        + "short and deep disagree in %d.%n", deepDiff, n, shortDiff, shortDeepDiff);
    System.out.printf(Locale.ROOT, "- first deep score of -1.00 or worse: %s.%n", at(firstDeepMinus1, plies));
    System.out.printf(Locale.ROOT, "- first deep forced loss: %s.%n", at(firstDeepLoss, plies));
    System.out.printf(Locale.ROOT, "- first short forced loss: %s.%n", at(firstShortLoss, plies));
    System.out.printf(Locale.ROOT, "- last deep score of 0.00 or better: %s.%n", at(lastNonNegative, plies));
    if (biggest != null) {
      System.out.printf(Locale.ROOT, "- largest fall of the deep score between our consecutive moves (mate scores "
          + "clamped to +-10): %.2f after ply %d (played %s, deep move %s, short move %s).%n", biggestDrop / 256.0,
          biggest.ply, biggest.played, biggest.deepMove, biggest.shortMove);
    }
  }

  private static int clamp(int v) {
    return Math.max(-2560, Math.min(2560, v));
  }

  private static String at(Row r, int plies) {
    if (r == null) {
      return "never";
    }
    return String.format(Locale.ROOT, "ply %d (%d plies before the end; played %s, deep move %s, deep %s, short move "
        + "%s, short %s)", r.ply, plies - r.ply + 1, r.played, r.deepMove, score(r.deepScore), r.shortMove,
        score(r.shortScore));
  }

  private static String move(int m) {
    return m == Tactics.NONE ? "-" : Integer.toString(Tactics.column(m), 36) + (Tactics.isSpecial(m) ? "*" : "");
  }

  private static String score(int v) {
    if (Math.abs(v) > Searcher.MATE_BOUND) {
      int n = Searcher.MATE - Math.abs(v);
      return (v > 0 ? "win " : "loss ") + n;
    }
    return String.format(Locale.ROOT, "%+.2f", v / 256.0);
  }
}
