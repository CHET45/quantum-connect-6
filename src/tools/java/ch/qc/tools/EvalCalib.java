package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Dumps the static evaluation of every position of server games with the game's outcome and blast features, for a
 * calibration check by group (who is charged, does a blast win now, how many columns could make a blast win).
 *
 * <pre>
 * --weights FILE --size 15x30 --min-ply 10 --out FILE a.jsonl ...
 * </pre>
 * Columns: game, ply, left (plies to the end), stm, cdStm, cdOpp, eval (1/256 logit, side to move), result (side to
 * move: 1 win, 0 loss, 0.5 draw), blastNow (some blast wins now), mayCols (columns with a neighbour at least three
 * taller), endBlast (the game ended with a blast).
 */
public final class EvalCalib {
  private EvalCalib() {}

  public static void main(String[] args) throws Exception {
    String weights = "hand";
    String size = "15x30";
    int minPly = 10;
    String out = null;
    java.util.List<String> files = new java.util.ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--weights" -> weights = args[++i];
        case "--size" -> size = args[++i];
        case "--min-ply" -> minPly = Integer.parseInt(args[++i]);
        case "--out" -> out = args[++i];
        default -> files.add(args[i]);
      }
    }
    Map<String, ServerGames.Game> byKey = new LinkedHashMap<>();
    for (String f : files) {
      for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          ServerGames.Game g = ServerGames.game(line);
          byKey.putIfAbsent(g.key(), g);
        }
      }
    }
    Function<Geometry, Evaluator> ef = Evaluators.factory(weights);
    StringBuilder sb = new StringBuilder("game\tply\tleft\tstm\tcdStm\tcdOpp\teval\tresult\tblastNow\tmayCols\tendBlast\n");
    int games = 0;
    for (ServerGames.Game g : byKey.values()) {
      ServerGames.Replay rp = ServerGames.replayAny(g, false, 0, 0);
      if (rp.problem != null || !size.equals(ServerGames.sizeName(rp.geo))) {
        continue;
      }
      games++;
      Geometry geo = rp.geo;
      Evaluator eval = ef.apply(geo);
      Board b = new Board(geo);
      int[] counter = new int[3];
      int expected = 1;
      String s = g.moves();
      int total = 0;
      for (int i = 0; i < s.length(); total++) {
        i += i + 2 < s.length() && s.charAt(i + 2) == '*' ? 3 : 2;
      }
      boolean endBlast = s.endsWith("*");
      int ply = 0;
      for (int i = 0; i < s.length();) {
        int side = s.charAt(i) == 'F' ? 1 : 2;
        int col = Character.digit(s.charAt(i + 1), 36);
        boolean star = i + 2 < s.length() && s.charAt(i + 2) == '*';
        i += star ? 3 : 2;
        if (side != expected) {
          counter[expected]++;
        }
        if (ply >= minPly) {
          int cdS = Math.min(counter[side], 10);
          int cdO = Math.min(counter[3 - side], 10);
          int v = eval.evaluate(b, side, cdS, cdO);
          double res = g.winner() == 0 ? 0.5 : g.winner() == side ? 1 : 0;
          int may = 0;
          for (int c = 0; c < geo.cols; c++) {
            may += b.blastMayWin(c) ? 1 : 0;
          }
          sb.append(g.id(), 0, 8).append('\t').append(ply).append('\t').append(total - ply).append('\t').append(side)
              .append('\t').append(cdS).append('\t').append(cdO).append('\t').append(v).append('\t').append(res)
              .append('\t').append(b.anyBlastWins() ? 1 : 0).append('\t').append(may).append('\t')
              .append(endBlast ? 1 : 0).append('\n');
        }
        if (star) {
          b.blast(col);
          counter[side] = 0;
        } else {
          b.make(col, side);
        }
        counter[side]++;
        ply++;
        expected = 3 - side;
      }
    }
    Files.writeString(Path.of(out), sb, StandardCharsets.UTF_8);
    System.out.println(games + " games -> " + out);
  }
}
