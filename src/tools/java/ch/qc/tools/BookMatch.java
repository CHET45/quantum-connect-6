package ch.qc.tools;

import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.Move;
import ch.cern.quantumconnect.core.SpaceOwnership;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real-protocol games from the empty board, as on the server (thread per call, real limits): colour-swapped pairs with
 * no opening at all, written with their moves, so unique games can be counted. Moves use the server's format, for
 * example F5S5F5*: side, base-36 column, a star for a blast; a skipped turn shows as the other side moving again.
 *
 * <pre>
 * --a SPEC --b SPEC  as in MatchMain   --pairs N   --threads N (at most 5)   --size 15x11   --out FILE
 * </pre>
 */
public final class BookMatch {
  private BookMatch() {}

  public static void main(String[] args) throws Exception {
    String a = null;
    String b = null;
    int pairs = 20;
    int threads = 5;
    String size = "15x11";
    Path out = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--a" -> a = args[++i];
        case "--b" -> b = args[++i];
        case "--pairs" -> pairs = Integer.parseInt(args[++i]);
        case "--threads" -> threads = Integer.parseInt(args[++i]);
        case "--size" -> size = args[++i];
        case "--out" -> out = Path.of(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    String[] rc = size.split("x");
    int rows = Integer.parseInt(rc[0]);
    int cols = Integer.parseInt(rc[1]);
    String specA = a;
    String specB = b;
    if (out != null && out.toAbsolutePath().getParent() != null) {
      Files.createDirectories(out.toAbsolutePath().getParent());
    }
    PrintWriter w = out == null ? null : new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8), true);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    double score = 0;
    int games = 0;
    int timeoutsA = 0;
    int timeoutsB = 0;
    Set<String> unique = new HashSet<>();
    try {
      CompletionService<String[]> done = new ExecutorCompletionService<>(pool);
      for (int p = 0; p < pairs; p++) {
        int pair = p;
        done.submit(() -> new String[] {
            play(specA, specB, rows, cols, pair, true), play(specA, specB, rows, cols, pair, false)});
      }
      for (int p = 0; p < pairs; p++) {
        for (String line : done.take().get()) {
          if (w != null) {
            w.println(line);
          }
          games++;
          score += field(line, "scoreA");
          timeoutsA += (int) field(line, "timeoutsA");
          timeoutsB += (int) field(line, "timeoutsB");
          unique.add(line.substring(line.indexOf("\"moves\"")));
        }
      }
    } finally {
      pool.shutdownNow();
      if (w != null) {
        w.close();
      }
    }
    System.out.printf(Locale.ROOT, "%s vs %s on %s: %d games, %d unique, A scores %.1f%%, timeouts A %d, B %d%n", a, b, size,
        games, unique.size(), games == 0 ? 0 : 100 * score / games, timeoutsA, timeoutsB);
  }

  private static double field(String json, String name) {
    int at = json.indexOf("\"" + name + "\":") + name.length() + 3;
    int end = at;
    while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) {
      end++;
    }
    return Double.parseDouble(json.substring(at, end));
  }

  private static String play(String a, String b, int rows, int cols, int pair, boolean aFirst) throws Exception {
    BotProbe pa = new BotProbe(BotLoader.create(a));
    BotProbe pb = new BotProbe(BotLoader.create(b));
    Experiment exp = new Experiment(aFirst ? pa : pb, aFirst ? pb : pa, false, false, rows, cols);
    exp.runExperiment();
    ExperimentStatus st = exp.getExperimentStatus();
    List<Move> history = new ArrayList<>(exp.getHistory());
    SpaceOwnership aColour = aFirst ? SpaceOwnership.FIRST_PLAYER : SpaceOwnership.SECOND_PLAYER;
    double scoreA = 0.5;
    String reason = st == ExperimentStatus.UNCERTAIN ? "full" : "cap";
    if (st == ExperimentStatus.COLLAPSED) {
      scoreA = exp.getCurrentPlayer() == aColour ? 1 : 0;
      reason = history.get(history.size() - 1).isSpecialMove() ? "blast" : "six";
    }
    StringBuilder moves = new StringBuilder();
    int placedA = 0;
    int placedB = 0;
    for (Move m : history) {
      moves.append(m.player() == SpaceOwnership.FIRST_PLAYER ? 'F' : 'S');
      moves.append(Character.forDigit(m.column(), 36));
      if (m.isSpecialMove()) {
        moves.append('*');
      }
      if (m.player() == aColour) {
        placedA++;
      } else {
        placedB++;
      }
    }
    return String.format(Locale.ROOT,
        "{\"pair\":%d,\"rows\":%d,\"cols\":%d,\"aFirst\":%b,\"scoreA\":%.1f,\"reason\":\"%s\",\"plies\":%d,"
            + "\"timeoutsA\":%d,\"timeoutsB\":%d,\"moves\":\"%s\"}",
        pair, rows, cols, aFirst, scoreA, reason, history.size(), Math.max(0, pa.expectedPlacements() - placedA),
        Math.max(0, pb.expectedPlacements() - placedB), moves);
  }
}
