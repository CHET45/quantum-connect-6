package ch.qc.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.Snapshot;
import ch.qc.bot.TurnTracker;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Tactics;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * FastGame must play exactly the games the official engine plays: deterministic engines whose move depends only on
 * the position, the own counter and the turn (including specials, refused specials and forfeits into full columns)
 * play the same games in both loops, and winner, reason and length must agree. The engines never request a special
 * on a full column: the engine wastes it without a history entry, so the adapter's tracker could not see its own
 * counter reset (QcBot never makes that move; the wasted-special rule itself is covered by ConformanceTest).
 */
class FastGameConformanceTest {
  /** Pseudo-random but position-determined engine. */
  static FastGame.Engine chaotic(long seed) {
    return (Board b, int me, int cdMe, int cdOpp, int turn) -> {
      SplittableRandom r = new SplittableRandom(b.hash() ^ seed ^ (31L * turn + cdMe));
      int col = r.nextInt(b.g.cols);
      boolean special = r.nextInt(4) == 0 && !b.isFull(col);
      return col | (special ? Tactics.SPECIAL : 0);
    };
  }

  /** Plays a FastGame engine through the official protocol, tracking its counters like QcBot does. */
  static final class EngineBot implements Algorithm {
    private final FastGame.Engine engine;
    private final TurnTracker tracker = new TurnTracker();
    private boolean special;

    EngineBot(FastGame.Engine engine) {
      this.engine = engine;
    }

    @Override
    public int accelerateQuark(QuantumField field) {
      Snapshot s = Snapshot.read(field);
      tracker.beginTurn(s);
      Board b = new Board(new Geometry(s.rows, s.cols));
      b.load(s.cells);
      int me = tracker.myColour() != 0 ? tracker.myColour() : tracker.iAmFirst() ? 1 : 2;
      int move = engine.move(b, me, tracker.myCooldownCounter(), tracker.opponentCooldownCounter(),
          tracker.plyIndex());
      special = Tactics.isSpecial(move);
      return Tactics.column(move);
    }

    @Override
    public boolean useSpecialMove(QuantumField field) {
      if (special && tracker.ownSpecialReady()) {
        tracker.noteSpecialRequested();
        return true;
      }
      return special;
    }
  }

  @Test
  void sameGamesAsTheEngine() throws Exception {
    Geometry g = new Geometry(8, 12);
    Map<String, Integer> reasons = new HashMap<>();
    for (int game = 0; game < 300; game++) {
      long sa = 1000L + game;
      long sb = 5000L + game;
      FastGame.Outcome fast = FastGame.play(g, new int[0], chaotic(sa), chaotic(sb), null);
      EngineBot first = new EngineBot(chaotic(sa));
      EngineBot second = new EngineBot(chaotic(sb));
      Experiment exp = new Experiment(first, second, false, false, 8, 12);
      exp.runExperiment();
      ExperimentStatus st = exp.getExperimentStatus();
      int winner = st == ExperimentStatus.COLLAPSED ? Snapshot.encode(exp.getCurrentPlayer()) : 0;
      java.lang.reflect.Field f = Experiment.class.getDeclaredField("iterations");
      f.setAccessible(true);
      int plies = ((java.util.concurrent.atomic.AtomicInteger) f.get(exp)).get();
      String ctx = "game " + game;
      assertEquals(fast.winner(), winner, ctx + " winner");
      assertEquals(fast.plies(), plies, ctx + " plies");
      if (st == ExperimentStatus.COLLAPSED) {
        java.util.List<ch.cern.quantumconnect.core.Move> h = exp.getHistory();
        assertEquals(fast.reason(), h.get(h.size() - 1).isSpecialMove() ? "blast" : "six", ctx + " reason");
      } else {
        assertEquals(fast.reason(), st == ExperimentStatus.UNCERTAIN ? "full" : "cap", ctx + " reason");
      }
      reasons.merge(fast.reason(), 1, Integer::sum);
    }
    System.out.println("FastGame vs engine: 300 identical games, outcomes " + reasons);
    assertTrue(reasons.getOrDefault("blast", 0) > 0, "no game ended by a blast");
  }
}
