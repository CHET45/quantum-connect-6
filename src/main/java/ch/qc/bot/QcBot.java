package ch.qc.bot;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.eval.Weights;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;

/**
 * Quantum Connect bot. M5: alpha-beta search ({@link Searcher}) with the learned pattern tables ({@link Weights};
 * the hand evaluation when no weights fit the board) and the gravity threat search ({@link Vcf}) at the root, under
 * the stop of the time plan ({@link Timing}: by own turn, cut after signs of trouble; less near the end of the shared
 * match clock, {@link MatchClock}), on top of the per-call pipeline (snapshot, turn tracking, board load, guarded
 * two-call protocol). The second call searches the position after our move to fill the transposition table for the
 * next turn, unless we blast or the match clock is short. The M1 root tactics are the fallback if the search cannot run.
 *
 * <p>The first turn of a game knows neither the call limit nor the board's tables, so it plays without search; nothing
 * can be lost there with at most one disc on the board. Its second call, where running late costs nothing, builds the
 * tables of this board, whatever its size, and finds out whether the call limit is below 100 ms ({@link CallLimit}).
 *
 * <p>Upload rules: only basic Java (no java.lang.System, java.io, java.nio, reflection, threads or runtime access),
 * and no exceptions thrown or caught anywhere in the bot; UploadRulesTest checks the compiled classes. Time comes
 * from {@link Clock}.
 */
public final class QcBot implements Algorithm {
  /**
   * No blast if the second call starts this long after the first began (at a limit of 100 ms): a first call that timed
   * out makes the second start at the limit or later, seen here from at most the first thread's late start earlier; 90
   * leaves margin for that and for the clock's 1 ms steps, and catches a stall after the first call measured its end.
   */
  static final long SECOND_CALL_LATE_NANOS = 90_000_000L;
  /** A lost turn counts as a stall of this much on top of the one remembered, up to MAX_STALL_PERCENT of the limit. */
  static final long LOST_TURN_STALL_NANOS = 30_000_000L;
  static final int MAX_STALL_PERCENT = 60;
  /** Boards above this many cells skip the board model and use the field directly (design 5.6). */
  static final int MAX_MODEL_CELLS = 4096;
  /** Search is limited to boards whose column index fits the transposition-table move field. */
  static final int MAX_SEARCH_COLS = 255;
  /** The stall sensor never takes the first-call stop below this share of the limit. */
  static final int MIN_STOP_PERCENT = 25;
  /** Before the limit is known no search runs with this many discs or fewer. */
  private static final int NO_SEARCH_DISCS = 3;
  /** A search before the limit is known: only harness games with scripted openings get there. */
  static final long UNPROBED_STOP_NANOS = 20_000_000L;
  /** The probe of the first turn ends here: on a limit of 100 ms a late thread start cannot turn this into a timeout. */
  static final long PROBE_NANOS = 85_000_000L;
  /** The probe for a limit above 100 ms gives up here and takes this as the limit. */
  static final long PROBE_UP_NANOS = 2_000_000_000L;
  /** One slice of warm-up games between looks at the field during a probe. */
  private static final long PROBE_SLICE_NANOS = 2_000_000L;
  /** An opponent's whole turn longer than this share of the limit, plus a little, hints at a longer limit. */
  private static final int HINT_PERCENT = 110;
  private static final long HINT_SLACK_NANOS = 5_000_000L;
  /** Budget of the fallback tactics. */
  static final long TACTICS_NANOS = 10_000_000L;

  private final TurnTracker tracker = new TurnTracker();
  private final MatchClock clock = new MatchClock();
  /** Score of our latest search, our view; 0 at the start of a game. */
  private int lastScore;
  private final TranspositionTable tt;
  private final Searcher.Options options;
  private final Timing timing;
  /** The warm-up runs once per loaded class: a server that keeps the class between games has hot code already. */
  private static boolean warmedUp;
  private final Vcf.Table vcfTable;
  private final CallLimit limit = new CallLimit();
  /** An opponent's turns or the time plan call for a look above the prior limit. */
  private boolean probeUpward;
  private boolean probedUpward;
  private long lastReturn;
  private Geometry geometry;
  private Searcher searcher;
  private int calls;
  private volatile Decision decision;
  private volatile long firstCallStart;
  private volatile Searcher.Result lastResult;
  private volatile Searcher.Result lastPonder;
  /** Our search position of this turn and its context, for the second call; null when no search ran. */
  private Board searchedBoard;
  private int searchedMe;
  private int searchedCdMe;
  private int searchedCdOpp;
  private int searchedPly;
  private int searchedEmpty;
  /** Longest stall seen lately, fading per own turn (see {@link Timing#stallLimitNanos}). */
  private long stall;
  private volatile boolean building;

  public QcBot() {
    this(Searcher.Options.DEFAULT, Timing.DEFAULT);
  }

  /** A bot with non-default search switches (harness experiments). */
  public QcBot(Searcher.Options options) {
    this(options, Timing.DEFAULT);
  }

  /** A bot with another time plan (harness experiments, {@link Timing#parse}). */
  public QcBot(String timingSpec) {
    this(Searcher.Options.DEFAULT, Timing.parse(timingSpec));
  }

  public QcBot(Searcher.Options options, Timing timing) {
    this.options = options;
    this.timing = timing;
    tt = TranspositionTable.forBot();
    Weights.preload();
    vcfTable = new Vcf.Table(16);
    probeUpward = timing.probeUpward;
    if (timing.warmupNanos > 0 && !warmedUp) {
      warmedUp = true;
      WarmUp.beforeTheGame(options).run(timing.warmupNanos);
    }
  }

  private Searcher newSearcher(Geometry g) {
    Evaluator learned = Weights.forGeometry(g);
    return new Searcher(g, learned != null ? learned : new HandEval(g), tt, options, vcfTable);
  }

  /**
   * Builds the tables of this board. This runs in the second call, where running late costs nothing: the first call of
   * the turn played without search, and no blast can be due on the first turn.
   */
  private void buildBoardSize(int rows, int cols) {
    building = true;
    Geometry g = new Geometry(rows, cols);
    Searcher s = newSearcher(g);
    searcher = s;
    geometry = g;
    building = false;
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    long t0 = Clock.nanos();
    firstCallStart = t0;
    int callId = ++calls;
    decision = null;
    Snapshot s = Snapshot.read(field);
    tracker.beginTurn(s);
    clock.beginTurn(t0, tracker.turn(), tracker.plyIndex());
    if (tracker.turn() == 1) {
      lastScore = 0;
      stall = 0;
    } else if (lastReturn > 0 && tracker.opponentMoved()
        && t0 - lastReturn > limit.nanos() / 100 * HINT_PERCENT + HINT_SLACK_NANOS) {
      probeUpward = true;
    }
    int move = choose(s, t0);
    int col = move == Tactics.NONE ? -1 : Tactics.column(move);
    boolean special = move != Tactics.NONE && Tactics.isSpecial(move);
    if (col < 0 || col >= s.cols || field.isColumnFull(col)) {
      col = safeColumn(field);
      special = false;
    }
    boolean inTime = Clock.nanos() - t0 <= blastFinish();
    if (special && !inTime && lastResult != null) {
      // too late to ask for the blast: play the best drop, not a drop in the blast's column
      int drop = searcher.lastBestDrop();
      if (drop != Tactics.NONE && !field.isColumnFull(Tactics.column(drop))) {
        col = Tactics.column(drop);
        special = false;
      }
    }
    decision = new Decision(callId, s.fingerprint, col, special, inTime);
    return col;
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    boolean special = secondCall(field);
    lastReturn = Clock.nanos();
    return special;
  }

  private boolean secondCall(QuantumField field) {
    long t0 = Clock.nanos();
    int rows = field.getNumberOfRows();
    int cols = field.getNumberOfColumns();
    if (!building && rows >= 1 && cols >= 1 && rows * cols <= MAX_MODEL_CELLS && cols <= MAX_SEARCH_COLS
        && (geometry == null || !geometry.sameSize(rows, cols))) {
      buildBoardSize(rows, cols);
    }
    Decision d = decision;
    if (d == null || d.callId() != calls || !secondCallInTime(firstCallStart, t0, limit.nanos())) {
      return false;
    }
    int col = d.column();
    if (col < 0 || col >= cols || field.isColumnFull(col) || Snapshot.fingerprint(field) != d.fingerprint()) {
      return false;
    }
    // only the blast needs the stricter limit: a search here cannot crash the engine, whatever the first call did
    if (d.special() && d.finishedInTime() && tracker.ownSpecialReady()) {
      tracker.noteSpecialRequested();
      return true;
    }
    if (!limit.known()) {
      probe(field, d, t0, PROBE_NANOS);
    } else if (probeUpward && !probedUpward && limit.nanos() <= CallLimit.PRIOR_NANOS
        && clock.ponderAllowed(t0, searchedPly, lastScore, searchedEmpty)) {
      probedUpward = true;
      probe(field, d, t0, PROBE_UP_NANOS);
    } else {
      ponder(col, t0);
    }
    return false;
  }

  /**
   * Finds out where the call limit lies: the engine applies our move as soon as it gives up on this call, so a change of
   * the field tells when that was. Until then the time goes to warm-up games on this very board. This thread touches
   * the field only by reading it, as every call does, and it ends as soon as the field changes, so it takes nothing from
   * the opponent's turn. A probe that ends at its own time leaves no timeout behind.
   */
  private void probe(QuantumField field, Decision d, long t0, long until) {
    boolean upward = until > PROBE_NANOS;
    if (!upward && !limit.known() && timing.probeUpward) {
      until = PROBE_UP_NANOS;
      upward = true;
      probedUpward = true;
    }
    WarmUp warm = geometry != null && geometry.sameSize(field.getNumberOfRows(), field.getNumberOfColumns())
        ? new WarmUp(new Geometry[] {geometry}, options) : null;
    while (true) {
      long now = Clock.nanos();
      if (Snapshot.fingerprint(field) != d.fingerprint()) {
        limit.measured(now - t0);
        return;
      }
      if (now - t0 >= until) {
        if (upward) {
          limit.atLeast(until);
        } else {
          limit.assumePrior();
        }
        return;
      }
      if (warm != null) {
        warm.run(Math.min(PROBE_SLICE_NANOS, until - (now - t0)));
      }
    }
  }

  /** False if the second call starts so late that the first may have timed out. */
  static boolean secondCallInTime(long firstCallStart, long secondCallStart, long callLimit) {
    return secondCallStart - firstCallStart < CallLimit.scaled(SECOND_CALL_LATE_NANOS, 60, callLimit);
  }

  /** The measured call limit (harness only). */
  public long callLimitNanos() {
    return limit.nanos();
  }

  /**
   * Searches the position after our committed move, opponent to move, so the next turn starts from a filled
   * transposition table. It returns well inside the second call; the next first call's search ends any overrun.
   */
  private void ponder(int col, long t0) {
    Board b = searchedBoard;
    searchedBoard = null;
    lastPonder = null;
    if (timing.ponderNanos <= 0 || tracker.turn() <= timing.ponderFromTurn || b == null || b.isFull(col)
        || !clock.ponderAllowed(t0, searchedPly, lastScore, searchedEmpty)) {
      return;
    }
    if (b.make(col, searchedMe) || b.topRowFull()) {
      return;
    }
    searcher.setStallLimit(0L);
    searcher.setBlastStop(0L);
    lastPonder = searcher.run(b, 3 - searchedMe, searchedCdOpp, Math.min(searchedCdMe + 1, TurnTracker.COOLDOWN),
        searchedPly + 1, t0, Math.min(limit.scaled(timing.ponderNanos, 40), clock.turnCap() / 5 * 2));
    if (searcher.lastMaxGapNanos() > stall) {
      stall = searcher.lastMaxGapNanos();
    }
  }

  /** How long a first call may take and still ask for a blast: the less the more this thread stalled lately. */
  private long blastFinish() {
    long limitNow = limit.nanos();
    return Math.max(limitNow / 10, limit.scaled(timing.safeFinishNanos, 40) - stall);
  }

  /** Called once per own turn with the longest gap between clock polls in the first call. */
  private void noteStall(long gap) {
    stall = Math.max(gap, stall / 100 * timing.stallDecayPercent);
  }

  /**
   * Advances the turn tracker for a turn played by an opening script instead of this bot, so the own turn
   * count and cooldowns stay exact (match harness only).
   */
  public void observeScriptedTurn(QuantumField field) {
    ++calls;
    decision = null;
    Snapshot s = Snapshot.read(field);
    tracker.beginTurn(s);
    limit.assumePrior();
    if (geometry == null || !geometry.sameSize(s.rows, s.cols)) {
      buildBoardSize(s.rows, s.cols);
    }
  }

  TurnTracker tracker() {
    return tracker;
  }

  Decision lastDecision() {
    return decision;
  }

  /** Search statistics of the latest call (harness only). */
  public Searcher.Result lastResult() {
    return lastResult;
  }

  /** Search statistics of the latest second-call search, null if there was none on this turn (harness only). */
  public Searcher.Result lastPonder() {
    return lastPonder;
  }

  private int choose(Snapshot s, long t0) {
    lastResult = null;
    searchedBoard = null;
    if (s.rows < 1 || s.cols < 1 || s.rows * s.cols > MAX_MODEL_CELLS) {
      return Tactics.NONE;
    }
    // with so few discs nothing can be lost by not searching, and a short limit may have cut the probe off once
    if (s.discs == 0 || (s.discs <= NO_SEARCH_DISCS && !limit.known())) {
      return centreOrder(s.cols, 0);
    }
    if (geometry == null || !geometry.sameSize(s.rows, s.cols)) {
      return Tactics.NONE;
    }
    // a fresh board per call: a search that outlived its call may still be walking the previous one
    Board board = new Board(geometry);
    if (!board.load(s.cells)) {
      return Tactics.NONE;
    }
    // on an empty board the colour is unknown, but then nothing depends on it
    int me = tracker.myColour() != 0 ? tracker.myColour() : tracker.iAmFirst() ? Board.FIRST : Board.SECOND;
    int cdMe = tracker.myCooldownCounter();
    int cdOpp = tracker.opponentCooldownCounter();
    if (s.cols <= MAX_SEARCH_COLS) {
      int empty = s.rows * s.cols - s.discs;
      long hard = clock.budget(t0, tracker.plyIndex(), stopNow(), lastScore, empty, limit.nanos());
      searcher.setStallLimit(limit.scaled(timing.stallLimitNanos, 50));
      searcher.setBlastStop(Math.max(limit.nanos() / 20, limit.scaled(timing.blastStopNanos, 30) - stall));
      Searcher.Result r = searcher.run(board, me, cdMe, cdOpp, tracker.plyIndex(), t0, hard);
      lastResult = r;
      noteStall(searcher.lastMaxGapNanos());
      if (r.move() != Tactics.NONE) {
        lastScore = r.score();
        searchedBoard = board;
        searchedMe = me;
        searchedCdMe = cdMe;
        searchedCdOpp = cdOpp;
        searchedPly = tracker.plyIndex();
        searchedEmpty = empty;
        return r.move();
      }
    }
    return new Tactics(geometry).choose(board, me, cdMe, cdOpp, Clock.nanos() + Math.min(TACTICS_NANOS, limit.nanos() / 10));
  }

  /** The first-call stop on this turn: by own turn and call limit, less the stalls seen lately; a lost turn is one. */
  private long stopNow() {
    int turn = tracker.turn() - 1;
    if (tracker.lostPreviousTurn()) {
      limit.distrustRaise();
      stall = Math.min(stall + LOST_TURN_STALL_NANOS, limit.nanos() / 100 * MAX_STALL_PERCENT);
    }
    long min = limit.nanos() / 100 * MIN_STOP_PERCENT;
    long stop = limit.scaled(timing.stop(turn), MIN_STOP_PERCENT);
    if (timing.stallLimitNanos > 0) {
      long sensed = limit.scaled(timing.stallLimitNanos, 50) - stall / 100 * timing.stallWeightPercent;
      stop = Math.min(stop, Math.max(min, sensed));
    }
    if (!limit.known()) {
      stop = Math.min(stop, UNPROBED_STOP_NANOS);
    }
    return Math.min(stop, clock.turnCap() / 5 * 3);
  }

  /** Never returns a full column while one is free; works on the field alone. */
  static int safeColumn(QuantumField field) {
    int cols = field.getNumberOfColumns();
    for (int i = 0; i < cols; i++) {
      int c = centreOrder(cols, i);
      if (!field.isColumnFull(c)) {
        return c;
      }
    }
    return 0;
  }

  /** i-th column in centre-first order: for 12 columns 5, 6, 4, 7, 3, 8, ... */
  static int centreOrder(int cols, int i) {
    int mid = (cols - 1) / 2;
    return i % 2 == 0 ? mid - i / 2 : mid + (i + 1) / 2;
  }
}
