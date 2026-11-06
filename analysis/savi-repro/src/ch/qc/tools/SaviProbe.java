package ch.qc.tools;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Prints one position of a server game and what the engine plays there. SaviProbe games.jsonl IDPREFIX PLY SPEC@EVAL NODES ... */
public final class SaviProbe {
  private SaviProbe() {}

  public static void main(String[] a) throws Exception {
    for (String line : Files.readAllLines(Path.of(a[0]), StandardCharsets.UTF_8)) {
      ServerGames.Game g = ServerGames.game(line);
      if (!g.id().startsWith(a[1])) {
        continue;
      }
      int target = Integer.parseInt(a[2]);
      Geometry geo = ServerGames.geometry(g.rows(), g.cols());
      Board b = new Board(geo);
      int[] counter = new int[3];
      String s = g.moves();
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
        if (ply == target) {
          System.out.printf("game %s %s vs %s, ply %d, to move %s, counters me %d opp %d, played %d%s%n", g.id(), g.name()[0], g.name()[1], ply, side == 1 ? "F" : "S", counter[side], counter[3 - side], col, star ? "*" : "");
          for (int y = geo.rows - 1; y >= 0; y--) {
            StringBuilder r = new StringBuilder();
            boolean any = false;
            for (int c = 0; c < geo.cols; c++) {
              int v = y < b.height(c) ? b.cell(c, y) : 0;
              any |= v != 0;
              r.append(v == 0 ? " ." : v == 1 ? " F" : " S");
            }
            if (any) {
              System.out.println(String.format("%2d", y) + r);
            }
          }
          System.out.print("  ");
          for (int c = 0; c < geo.cols; c++) {
            System.out.print(" " + Character.forDigit(c, 36));
          }
          System.out.println();
          for (int k = 3; k + 1 < a.length; k += 2) {
            String[] sp = (a[k] + "@hand").split("@", 3);
            Searcher e = new Searcher(geo, Evaluators.factory(sp[1]).apply(geo), new TranspositionTable(20), SearchOptions.parse(sp[0]), new Vcf.Table(16));
            Board v = new Board(geo);
            v.copyFrom(b);
            Searcher.Result r = e.run(v, side, counter[side], counter[3 - side], turn, ch.qc.bot.Clock.nanos(), Long.MAX_VALUE / 4, Long.parseLong(a[k + 1]));
            System.out.printf("  %-24s %8s nodes: move %d%s score %d depth %d, searched %d, vcf nodes %d proofs %d%n", a[k], a[k + 1], Tactics.column(r.move()), Tactics.isSpecial(r.move()) ? "*" : "", r.score(), r.depth(), r.nodes(), r.vcfNodes(), r.vcfProofs());
          }
          return;
        }
        boolean win = star ? b.blast(col) : b.make(col, side);
        if (star) {
          counter[side] = 0;
        }
        counter[side]++;
        turn++;
        ply++;
        expected = 3 - side;
        if (win) {
          return;
        }
      }
    }
  }
}
