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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reviews server games our team lost ({@link ServerGames} exports): before every ply a short search (the bot's
 * stop) and a deep one, both from the mover's view printed in our view, so the ply where our deep score drops is
 * the mistake and the short search shows whether the bot, given more time, would have seen it.
 *
 * <pre>
 * --team NAME (default "No beaches?") --weights FILE (or hand) --short MS (62) --deep MS (2000) --vs SUBSTRING
 * --games N (all) a.jsonl b.jsonl
 * </pre>
 */
public final class GameReview {
  private GameReview() {}

  public static void main(String[] args) throws Exception {
    String team = "No beaches?";
    String weights = "hand";
    long shortMs = 62;
    long deepMs = 2000;
    String vs = "";
    int maxGames = Integer.MAX_VALUE;
    java.util.List<String> files = new java.util.ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--team" -> team = args[++i];
        case "--weights" -> weights = args[++i];
        case "--short" -> shortMs = Long.parseLong(args[++i]);
        case "--deep" -> deepMs = Long.parseLong(args[++i]);
        case "--vs" -> vs = args[++i];
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
    Geometry geo = new Geometry(8, 12);
    java.util.function.Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    int done = 0;
    for (ServerGames.Game g : byId.values()) {
      int us = team.equals(g.team()[0]) ? 1 : team.equals(g.team()[1]) ? 2 : 0;
      if (us == 0 || g.winner() == us || !g.name()[2 - us].contains(vs) || done >= maxGames) {
        continue;
      }
      done++;
      review(geo, ef.apply(geo), g, us, shortMs * 1_000_000L, deepMs * 1_000_000L);
    }
  }

  private static void review(Geometry geo, Evaluator eval, ServerGames.Game g, int us, long shortNs, long deepNs) {
    System.out.printf(Locale.ROOT, "%n## %s (%s) vs %s, game %s%n%n", g.name()[us - 1], us == 1 ? "F" : "S",
        g.name()[2 - us], g.id());
    System.out.println("| ply | mover | played | short move | short (our view) | deep move | deep (our view) | depth |");
    System.out.println("|---|---|---|---|---|---|---|---|");
    Searcher shortS = new Searcher(geo, eval, new TranspositionTable(18));
    Searcher deepS = new Searcher(geo, eval, new TranspositionTable(22));
    Board b = new Board(geo);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    int ply = 0;
    String s = g.moves();
    int i = 0;
    while (i < s.length()) {
      int side = s.charAt(i) == 'F' ? 1 : 2;
      int col = Character.digit(s.charAt(i + 1), 16);
      boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
      i += star ? 3 : 2;
      if (side != expected) {
        counter[expected]++;
        turn++;
      }
      int sign = side == us ? 1 : -1;
      Board view = new Board(geo);
      view.copyFrom(b);
      Searcher.Result rs = shortS.run(view, side, counter[side], counter[3 - side], turn, Clock.nanos(), shortNs);
      view.copyFrom(b);
      Searcher.Result rd = deepS.run(view, side, counter[side], counter[3 - side], turn, Clock.nanos(), deepNs);
      System.out.printf(Locale.ROOT, "| %d | %s | %s | %s | %s | %s | %s | %d |%n", ply + 1, side == 1 ? "F" : "S",
          Integer.toHexString(col) + (star ? "*" : ""), move(rs.move()), score(sign * rs.score()), move(rd.move()),
          score(sign * rd.score()), rd.depth());
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
  }

  private static String move(int m) {
    return m == Tactics.NONE ? "-" : Integer.toHexString(Tactics.column(m)) + (Tactics.isSpecial(m) ? "*" : "");
  }

  private static String score(int v) {
    if (Math.abs(v) > Searcher.MATE_BOUND) {
      int n = Searcher.MATE - Math.abs(v);
      return (v > 0 ? "win " : "loss ") + n;
    }
    return String.format(Locale.ROOT, "%+.2f", v / 256.0);
  }
}
