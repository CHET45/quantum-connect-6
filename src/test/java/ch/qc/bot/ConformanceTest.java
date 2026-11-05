package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.Move;
import ch.cern.quantumconnect.core.QuantumField;
import ch.cern.quantumconnect.core.SpaceOwnership;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;

/**
 * Rules conformance against the official engine (design 5.1). Scripted bots replay (column, special) sequences,
 * including forfeits, exceptions, timeouts, refused and wasted specials, through a headless Experiment; a model
 * built on {@link Board} replays the same scripts and must match the field every call observes, the history,
 * the final field and the result. Size with -Pqc.conf.games=N.
 */
class ConformanceTest {
  private static final int GAMES = Integer.getInteger("qc.conf.games", 1500);

  private static final int NORMAL = 0;
  private static final int THROW_IN_ACCELERATE = 1;
  private static final int THROW_IN_SPECIAL = 2;
  private static final int TIMEOUT_IN_ACCELERATE = 3;

  record Action(int col, boolean special, int kind) {}

  private static final Action FORFEIT = new Action(-1, false, NORMAL);

  record Scenario(String name, int rows, int cols, Action[][] script) {}

  record Observation(int player, byte[] cells, int myColour, int historySize) {}

  private final Map<String, LongAdder> coverage = new ConcurrentHashMap<>();

  @Test
  void engineAndModelAgree() throws Exception {
    List<Scenario> scenarios = new ArrayList<>(targetedScenarios());
    SplittableRandom root = new SplittableRandom(4242);
    for (int i = 0; i < GAMES; i++) {
      scenarios.add(randomScenario(i, root.split()));
    }
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Scenario s : scenarios) {
        futures.add(pool.submit(() -> {
          check(s);
          return null;
        }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
    } finally {
      pool.shutdownNow();
    }
    Map<String, Long> report = new TreeMap<>();
    coverage.forEach((k, v) -> report.put(k, v.sum()));
    System.out.println("conformance: " + scenarios.size() + " games, coverage " + report);
    for (String required : List.of(
        "normal win", "draw full top row", "draw at 1000 turns", "win on turn 1000", "win fills board",
        "forfeit out of range", "forfeit full column", "forfeit exception", "forfeit timeout",
        "special exception dropped", "special refused by cooldown", "special honoured", "special on full column",
        "special on empty column", "special on nearly full column", "first special on own turn 11",
        "second special 10 turns later", "blast win own line only", "blast win opponent line only",
        "blast win both colours", "special while opponent forfeits")) {
      assertTrue(report.getOrDefault(required, 0L) > 0, "scenario never exercised: " + required);
    }
  }

  private void check(Scenario s) throws Exception {
    List<Observation> log = java.util.Collections.synchronizedList(new ArrayList<>());
    ScriptedBot first = new ScriptedBot(1, s.script[1], log);
    ScriptedBot second = new ScriptedBot(2, s.script[2], log);
    Experiment exp = new Experiment(first, second, false, false, s.rows, s.cols);
    exp.runExperiment();
    java.lang.reflect.Field qf = Experiment.class.getDeclaredField("quantumField");
    qf.setAccessible(true);
    QuantumField finalField = (QuantumField) qf.get(exp);

    Model m = new Model(s.rows, s.cols, coverage);
    int[] next = new int[3];
    String ctx = s.name + " " + s.rows + "x" + s.cols;
    while (!m.over()) {
      assertTrue(m.iter < log.size(), ctx + ": engine stopped early at turn " + m.iter);
      Observation o = log.get(m.iter);
      assertEquals(m.toMove, o.player, ctx + ": player at turn " + m.iter);
      assertArrayEquals(m.board.cells(), o.cells, ctx + ": field seen at turn " + m.iter);
      assertEquals(m.history.size(), o.historySize, ctx + ": history size at turn " + m.iter);
      if (m.board.discs() > 0) {
        assertEquals(m.toMove, o.myColour, ctx + ": belongsToMe colour at turn " + m.iter);
      }
      Action[] script = s.script[m.toMove];
      int idx = next[m.toMove]++;
      m.step(idx < script.length ? script[idx] : FORFEIT);
    }
    assertEquals(m.iter, log.size(), ctx + ": number of calls");
    assertEquals(m.status(), exp.getExperimentStatus(), ctx + ": final status");
    int expectedCurrent = m.status() == ExperimentStatus.COLLAPSED ? 3 - m.toMove : m.toMove;
    assertEquals(expectedCurrent, Snapshot.encode(exp.getCurrentPlayer()), ctx + ": winner / current player");
    assertEquals(m.history, exp.getHistory(), ctx + ": move history");
    assertArrayEquals(m.board.cells(), Snapshot.read(finalField).cells, ctx + ": final field");
    m.recordOutcome();
  }

  /** Replays turns with the engine's turn loop semantics on top of Board (R11-R19). */
  static final class Model {
    final Board board;
    final int rows;
    final int cols;
    int toMove = 1;
    int iter;
    final int[] counter = new int[3];
    final int[] ownTurns = new int[3];
    final int[] lastSpecialTurn = {0, -1, -1};
    final int[] forfeitStreak = new int[3];
    final List<Move> history = new ArrayList<>();
    private final Map<String, LongAdder> coverage;
    private boolean lastWasBlastWin;

    Model(int rows, int cols, Map<String, LongAdder> coverage) {
      this.rows = rows;
      this.cols = cols;
      this.board = new Board(new Geometry(rows, cols));
      this.coverage = coverage;
    }

    ExperimentStatus status() {
      if (board.hasLine(0)) {
        return ExperimentStatus.COLLAPSED;
      }
      return board.topRowFull() ? ExperimentStatus.UNCERTAIN : ExperimentStatus.INCOMPLETE;
    }

    boolean over() {
      return status() != ExperimentStatus.INCOMPLETE || iter >= 1000;
    }

    boolean specialAvailable() {
      return counter[toMove] >= 10;
    }

    void step(Action a) {
      int me = toMove;
      int opp = 3 - me;
      ownTurns[me]++;
      int col = a.kind == THROW_IN_ACCELERATE || a.kind == TIMEOUT_IN_ACCELERATE ? -1 : a.col;
      boolean requested = a.kind != THROW_IN_SPECIAL && a.special;
      if (a.kind == THROW_IN_SPECIAL && a.special) {
        cover("special exception dropped");
      }
      boolean honoured = requested && counter[me] >= 10;
      if (requested && !honoured) {
        cover("special refused by cooldown");
      }
      lastWasBlastWin = false;
      boolean placed = false;
      if (honoured) {
        if (col < 0 || col >= cols) {
          throw new IllegalStateException("script would crash the engine (R10)");
        }
        cover("special honoured");
        if (lastSpecialTurn[me] < 0 && ownTurns[me] == 11) {
          cover("first special on own turn 11");
        }
        if (lastSpecialTurn[me] >= 0 && ownTurns[me] - lastSpecialTurn[me] == 10) {
          cover("second special 10 turns later");
        }
        if (forfeitStreak[opp] >= 3) {
          cover("special while opponent forfeits");
        }
        lastSpecialTurn[me] = ownTurns[me];
        if (board.isFull(col)) {
          cover("special on full column");
        } else {
          if (board.height(col) == 0) {
            cover("special on empty column");
          }
          if (board.height(col) == rows - 1) {
            cover("special on nearly full column");
          }
          boolean win = board.blast(col);
          history.add(new Move(true, col, owner(me)));
          placed = true;
          if (win) {
            lastWasBlastWin = true;
            boolean mine = board.hasLine(me);
            boolean theirs = board.hasLine(opp);
            cover(mine && theirs ? "blast win both colours" : mine ? "blast win own line only" : "blast win opponent line only");
          }
        }
        counter[me] = 0;
      } else if (col >= 0 && col < cols && !board.isFull(col)) {
        board.make(col, me);
        history.add(new Move(false, col, owner(me)));
        placed = true;
      } else if (a.kind == THROW_IN_ACCELERATE) {
        cover("forfeit exception");
      } else if (a.kind == TIMEOUT_IN_ACCELERATE) {
        cover("forfeit timeout");
      } else if (col < 0 || col >= cols) {
        cover("forfeit out of range");
      } else {
        cover("forfeit full column");
      }
      forfeitStreak[me] = placed ? 0 : forfeitStreak[me] + 1;
      counter[me]++;
      toMove = opp;
      iter++;
    }

    void recordOutcome() {
      ExperimentStatus st = status();
      if (st == ExperimentStatus.COLLAPSED) {
        if (!lastWasBlastWin) {
          cover("normal win");
        }
        if (board.topRowFull()) {
          cover("win fills board");
        }
        if (iter == 1000) {
          cover("win on turn 1000");
        }
      } else if (st == ExperimentStatus.UNCERTAIN) {
        cover("draw full top row");
      } else {
        cover("draw at 1000 turns");
      }
    }

    private void cover(String key) {
      coverage.computeIfAbsent(key, x -> new LongAdder()).increment();
    }

    static SpaceOwnership owner(int colour) {
      return colour == 1 ? SpaceOwnership.FIRST_PLAYER : SpaceOwnership.SECOND_PLAYER;
    }
  }

  static final class ScriptedBot implements Algorithm {
    private final int player;
    private final Action[] script;
    private final List<Observation> log;
    private int next;
    private volatile Action current = FORFEIT;

    ScriptedBot(int player, Action[] script, List<Observation> log) {
      this.player = player;
      this.script = script;
      this.log = log;
    }

    @Override
    public int accelerateQuark(QuantumField field) {
      Action a = next < script.length ? script[next] : FORFEIT;
      next++;
      current = a;
      Snapshot s = Snapshot.read(field);
      log.add(new Observation(player, s.cells, s.myColour, s.history.size()));
      if (a.kind == THROW_IN_ACCELERATE) {
        throw new IllegalStateException("scripted exception");
      }
      if (a.kind == TIMEOUT_IN_ACCELERATE) {
        try {
          Thread.sleep(150);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return a.col;
    }

    @Override
    public boolean useSpecialMove(QuantumField field) {
      Action a = current;
      if (a.kind == THROW_IN_SPECIAL) {
        throw new IllegalStateException("scripted exception");
      }
      return a.special;
    }
  }

  // ---------------------------------------------------------------- scenario generation

  /** Policy knobs of one random game. */
  record Profile(double special, double outOfRange, double throwRate, double timeoutRate, double smart, double forfeit) {}

  private Scenario randomScenario(int id, SplittableRandom rnd) {
    int rows;
    int cols;
    int r = rnd.nextInt(10);
    if (r < 3) {
      rows = 8;
      cols = 12;
    } else if (r < 8) {
      rows = 1 + rnd.nextInt(10);
      cols = 1 + rnd.nextInt(12);
    } else {
      rows = 1 + rnd.nextInt(40);
      cols = 1 + rnd.nextInt(40);
    }
    Profile[] p = new Profile[3];
    for (int pl = 1; pl <= 2; pl++) {
      double[] specials = {0, 0.1, 0.5, 1.0};
      p[pl] = new Profile(
          specials[rnd.nextInt(4)],
          rnd.nextInt(3) == 0 ? 0.05 : 0,
          rnd.nextInt(3) == 0 ? 0.03 : 0,
          id % 40 == 0 ? 0.02 : 0,
          rnd.nextInt(2) == 0 ? 0.8 : 0,
          rnd.nextInt(8) == 0 ? 0.6 : 0);
    }
    return generate("random-" + id, rows, cols, p, rnd);
  }

  /** Plays the policy against the model to produce scripts; scripts never trigger the R10 engine crash. */
  private Scenario generate(String name, int rows, int cols, Profile[] p, SplittableRandom rnd) {
    List<List<Action>> out = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    Model m = new Model(rows, cols, new ConcurrentHashMap<>());
    int[] towers = {rnd.nextInt(cols), rnd.nextInt(cols)};
    Board probe = new Board(m.board.g);
    while (!m.over()) {
      Profile pr = p[m.toMove];
      Action a = null;
      if (m.specialAvailable() && rnd.nextDouble() < pr.smart) {
        for (int c = 0; c < cols && a == null; c++) {
          if (!m.board.isFull(c) && m.board.blastMayWin(c)) {
            probe.copyFrom(m.board);
            if (probe.blast(c)) {
              a = new Action(c, true, NORMAL);
            }
          }
        }
      }
      if (a == null) {
        a = randomAction(m, pr, towers, rnd);
      }
      out.get(m.toMove).add(a);
      m.step(a);
    }
    Action[][] script = new Action[3][];
    for (int pl = 1; pl <= 2; pl++) {
      script[pl] = out.get(pl).toArray(new Action[0]);
    }
    return new Scenario(name, rows, cols, script);
  }

  private static Action randomAction(Model m, Profile pr, int[] towers, SplittableRandom rnd) {
    int cols = m.cols;
    if (rnd.nextDouble() < pr.forfeit) {
      return FORFEIT;
    }
    int col;
    if (rnd.nextDouble() < pr.outOfRange) {
      col = rnd.nextBoolean() ? -1 : cols + rnd.nextInt(3);
    } else if (rnd.nextInt(3) == 0) {
      col = towers[rnd.nextInt(2)];
    } else {
      col = rnd.nextInt(cols);
    }
    boolean special = rnd.nextDouble() < pr.special;
    int kind = NORMAL;
    double k = rnd.nextDouble();
    if (k < pr.timeoutRate) {
      kind = TIMEOUT_IN_ACCELERATE;
    } else if (k < pr.timeoutRate + pr.throwRate) {
      kind = THROW_IN_ACCELERATE;
    } else if (k < pr.timeoutRate + 2 * pr.throwRate) {
      kind = THROW_IN_SPECIAL;
    }
    boolean honoured = special && kind != THROW_IN_SPECIAL && m.specialAvailable();
    boolean noColumn = kind == THROW_IN_ACCELERATE || kind == TIMEOUT_IN_ACCELERATE || col < 0 || col >= cols;
    if (honoured && noColumn) {
      // an honoured special with col = -1 or out of range throws inside the engine (R10)
      if (kind == NORMAL) {
        col = rnd.nextInt(cols);
      } else {
        special = false;
      }
    }
    return new Action(col, special, kind);
  }

  private List<Scenario> targetedScenarios() {
    List<Scenario> list = new ArrayList<>();
    Action[] forfeits = new Action[600];
    Arrays.fill(forfeits, FORFEIT);
    list.add(new Scenario("cap-draw", 8, 12, new Action[][] {null, forfeits, forfeits}));

    // the second player completes a vertical six on iteration 999, the last one before the cap
    Action[] firstP = forfeits.clone();
    Action[] secondP = forfeits.clone();
    for (int t = 989; t <= 999; t += 2) {
      secondP[(t - 1) / 2] = new Action(0, false, NORMAL);
    }
    for (int t = 990; t <= 998; t += 2) {
      firstP[t / 2] = new Action(1, false, NORMAL);
    }
    list.add(new Scenario("win-on-1000", 8, 12, new Action[][] {null, firstP, secondP}));

    // a single column: the sixth own disc both wins and fills the board
    Action[] stack = new Action[6];
    Arrays.fill(stack, new Action(0, false, NORMAL));
    list.add(new Scenario("win-fills-board", 6, 1, new Action[][] {null, stack, forfeits}));

    // specials requested every turn from turn 1: honoured on own turns 11, 21, 31, ...; columns 5 and 11 stay
    // empty so one colour alone cannot make a horizontal or diagonal six
    int[] pattern = {0, 1, 2, 3, 4, 6, 7, 8, 9, 10};
    Action[] spam = new Action[60];
    for (int i = 0; i < spam.length; i++) {
      spam[i] = new Action(pattern[i % pattern.length], true, NORMAL);
    }
    Action[] calm = new Action[60];
    for (int i = 0; i < calm.length; i++) {
      calm[i] = new Action((i * 5) % 12, false, NORMAL);
    }
    list.add(new Scenario("special-spam", 8, 12, new Action[][] {null, spam, calm}));
    list.add(new Scenario("special-spam-second", 8, 12, new Action[][] {null, calm, spam}));
    list.add(new Scenario("special-vs-forfeits", 8, 12, new Action[][] {null, spam, forfeits}));

    // 8x13, bottom row before the blast: 1 1 1 1 1 [2,2,1] _ [1,1,2] 2 2 2 2 2 (brackets: columns 5 and 7 from
    // the bottom). The first player's blast in the empty column 6 removes the two bottom discs of columns 5 and 7,
    // which drops a 1 into column 5 and a 2 into column 7: both colours get a six in the bottom row at once.
    int[] firstCols = {7, 7, 0, 1, 2, 3, 4, 5, -1, -1};
    int[] secondCols = {5, 5, 7, 8, 9, 10, 11, 12, -1, -1};
    Action[] firstBoth = new Action[11];
    Action[] secondBoth = new Action[10];
    for (int i = 0; i < 10; i++) {
      firstBoth[i] = new Action(firstCols[i], false, NORMAL);
      secondBoth[i] = new Action(secondCols[i], false, NORMAL);
    }
    firstBoth[10] = new Action(6, true, NORMAL);
    list.add(new Scenario("blast-both-colours", 8, 13, new Action[][] {null, firstBoth, secondBoth}));
    return list;
  }
}
