package ch.qc.tools;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes positions of real server games in the format of {@link SearchBench}, so search changes can be judged on games
 * against other bots and not only on self-play.
 *
 * <pre>
 * --vs SUBSTRING (a player's name must contain it) --size 15x11 --every 7 --min-ply 8 --count 300 --out FILE a.jsonl ...
 * </pre>
 */
public final class ServerPositions {
  private ServerPositions() {}

  public static void main(String[] args) throws Exception {
    String vs = "";
    String size = "15x11";
    int every = 7;
    int minPly = 8;
    int count = 300;
    String out = null;
    List<String> files = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--vs" -> vs = args[++i];
        case "--size" -> size = args[++i];
        case "--every" -> every = Integer.parseInt(args[++i]);
        case "--min-ply" -> minPly = Integer.parseInt(args[++i]);
        case "--count" -> count = Integer.parseInt(args[++i]);
        case "--out" -> out = args[++i];
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
    List<String> lines = new ArrayList<>();
    int games = 0;
    for (ServerGames.Game g : byId.values()) {
      if (!g.name()[0].contains(vs) && !g.name()[1].contains(vs)) {
        continue;
      }
      ServerGames.Replay rp = ServerGames.replayAny(g, false, 0, 0);
      if (rp.problem != null || !size.equals(ServerGames.sizeName(rp.geo))) {
        continue;
      }
      games++;
      positions(rp.geo, g, every, minPly, games, lines);
    }
    java.util.Collections.shuffle(lines, new java.util.Random(7));
    List<String> kept = lines.subList(0, Math.min(count, lines.size()));
    Files.write(Path.of(out), kept, StandardCharsets.UTF_8);
    System.out.println(kept.size() + " of " + lines.size() + " positions from " + games + " games");
  }

  private static void positions(Geometry geo, ServerGames.Game g, int every, int minPly, int salt, List<String> lines) {
    Board b = new Board(geo);
    int[] counter = new int[3];
    int expected = 1;
    int turn = 0;
    int ply = 0;
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
      if (ply >= minPly && (ply + salt) % every == 0) {
        StringBuilder sb = new StringBuilder();
        for (byte v : b.cells()) {
          sb.append((char) ('0' + v));
        }
        lines.add(geo.rows + " " + geo.cols + " " + side + " " + counter[side] + " " + counter[3 - side] + " " + turn + " " + sb);
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
  }
}
