package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.FieldFactory;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.starter.SpecialMoveBot;
import ch.qc.tools.BotProbe;
import ch.qc.tools.MatchMain;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * M0 acceptance (design 5.6 and 6): QcBot through the official protocol over the board-size list, against
 * opponents that use specials and forfeit: no timeout, no invalid column, no invalid special, latency
 * p99.99 below 10 ms. Size with -Pqc.robust.calls=100000.
 */
class QcBotRobustnessTest {
  private static final int CALLS = Integer.getInteger("qc.robust.calls", 20_000);
  private static final String SIZES = "1x1,1x12,12x1,2x2,3x3,5x5,6x6,6x7,7x6,8x12,12x8,9x9,10x14,25x25,40x40";

  @Test
  void officialProtocolOverAllBoardSizes() throws Exception {
    String[] opponents = {
        "ch.qc.starter.SpecialMoveBot", "ch.cern.quantumconnect.bots.RandomBot", "ch.cern.quantumconnect.bots.LinearBot",
        "ch.qc.starter.RandomBot"};
    int[][] sizes = sizes();
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    int calls = 0;
    int round = 0;
    MatchMain.Match total = null;
    while (calls < CALLS) {
      String opp = opponents[round % opponents.length];
      MatchMain.Match m = new MatchMain.Match("ch.qc.bot.QcBot", opp, sizes, round % 3 == 0 ? 0 : 4, round);
      List<MatchMain.Result> results = m.run(sizes.length, threads);
      for (MatchMain.Result r : results) {
        String ctx = opp + " " + r.rows() + "x" + r.cols() + " game " + r.game();
        assertEquals(0, r.timeoutsA(), ctx + ": timeouts");
        assertEquals(0, r.invalidA(), ctx + ": invalid columns");
        assertEquals(0, r.badSpecialsA(), ctx + ": invalid specials");
      }
      if (total == null) {
        total = m;
      } else {
        total.latencyA.merge(m.latencyA);
        total.specialLatencyA.merge(m.specialLatencyA);
      }
      calls += m.callsA();
      round++;
    }
    System.out.println("robustness: " + calls + " QcBot calls; accelerate " + total.latencyA.summary()
        + "; special " + total.specialLatencyA.summary());
    assertTrue(total.latencyA.quantileMillis(0.9999) < 10, "p99.99 latency " + total.latencyA.summary());
  }

  @Test
  void neverAFullColumnOnRandomPositions() {
    SplittableRandom rnd = new SplittableRandom(99);
    int[][] sizes = sizes();
    int checked = 0;
    while (checked < 200_000) {
      int[] size = sizes[rnd.nextInt(sizes.length)];
      QcBot bot = new QcBot();
      Algorithm other = new SpecialMoveBot();
      QuantumField field = FieldFactory.create(size[0], size[1], bot, other);
      boolean botToMove = true;
      for (int ply = 0; ply < 3 * size[0] * size[1] && FieldFactory.status(field) == ExperimentStatus.INCOMPLETE; ply++) {
        int col;
        if (botToMove) {
          col = bot.accelerateQuark(field);
          assertTrue(col >= 0 && col < size[1] && !field.isColumnFull(col), "bad column " + col);
          assertTrue(!bot.useSpecialMove(field), "M0 must not request specials");
          checked++;
        } else {
          col = rnd.nextInt(size[1]);
        }
        FieldFactory.play(field, col, !botToMove && rnd.nextInt(4) == 0);
        botToMove = !botToMove;
      }
    }
  }

  @Test
  void oneInstanceAcrossGamesAndSides() throws Exception {
    QcBot shared = new QcBot();
    for (int g = 0; g < 10; g++) {
      BotProbe probe = new BotProbe(shared);
      Experiment exp = g % 2 == 0
          ? new Experiment(probe, new SpecialMoveBot(0.5), false, false, 8, 12)
          : new Experiment(new SpecialMoveBot(0.5), probe, false, false, 8, 12);
      exp.runExperiment();
      assertEquals(0, probe.invalidColumns());
      assertEquals(0, probe.exceptions());
      assertEquals(0, probe.invalidSpecials());
    }
  }

  private static int[][] sizes() {
    String[] parts = SIZES.split(",");
    int[][] s = new int[parts.length][];
    for (int i = 0; i < parts.length; i++) {
      String[] rc = parts[i].split("x");
      s[i] = new int[] {Integer.parseInt(rc[0]), Integer.parseInt(rc[1])};
    }
    return s;
  }
}
