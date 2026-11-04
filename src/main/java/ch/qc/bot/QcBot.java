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
 * next turn, unless we blast or the match clock is short. A board size without prepared tables never builds them in
 * a first call: that turn plays without search and the second call builds them. The M1 root tactics are the fallback
 * if the search cannot run.
 *
 * <p>Upload rules: only basic Java (no java.lang.System, java.io, java.nio, reflection, threads or runtime access),
 * and no exceptions thrown or caught anywhere in the bot; UploadRulesTest checks the compiled classes. Time comes
 * from {@link Clock}.
 */
public final class QcBot implements Algorithm {
  /**
   * A special after a timed-out first call crashes the engine (design R10, R15; seen in a night match), and the bot
   * cannot see the timeout directly, only the time since it was called, which starts late if its thread started late.
   * So a blast is requested only if the first call took at most SAFE_FINISH_NANOS, which leaves 25 ms for such a late
   * start; a search with a blast in the lead stops at BLAST_STOP_NANOS to stay inside that. And it is requested only if
   * the second call starts before SECOND_CALL_LATE_NANOS since the first began: a first call that timed out makes the
   * second start at 100 ms or later, seen here from at most the late start's length earlier; 90 leaves margin for that
   * and for the clock's 1 ms steps, and catches a stall after the first call measured its own end.
   */
  static final long SAFE_FINISH_NANOS = 75_000_000L;
  static final long BLAST_STOP_NANOS = 70_000_000L;
  static final long SECOND_CALL_LATE_NANOS = 90_000_000L;
  /** Boards above this many cells skip the board model and use the field directly (design 5.6). */
  static final int MAX_MODEL_CELLS = 4096;
  /** Search is limited to boards whose column index fits the transposition-table move field. */
  static final int MAX_SEARCH_COLS = 255;
  /** Board sizes seen on the server: their tables are built in the constructor, outside any timed call. */
  private static final int[][] PREPARED_SIZES = {{8, 12}, {15, 11}, {15, 22}};
  /** The stall sensor never takes the first-call stop below this. */
  static final long MIN_STOP_NANOS = 25_000_000L;
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
  private final Geometry[] preparedGeometry = new Geometry[PREPARED_SIZES.length];
  private final Searcher[] preparedSearcher = new Searcher[PREPARED_SIZES.length];
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
  /** Last own turn of a cut stop, and how far the last first call ran past its stop. */
  private int cutUntil;
  private volatile long lastOverrun;
  /** Longest stall seen lately, fading per own turn (see {@link Timing#stallLimitNanos}). */
  private long stall;
  private volatile boolean building;
  private WarmUp warmUp;

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
    for (int i = 0; i < PREPARED_SIZES.length; i++) {
      preparedGeometry[i] = new Geometry(PREPARED_SIZES[i][0], PREPARED_SIZES[i][1]);
      preparedSearcher[i] = newSearcher(preparedGeometry[i]);
    }
    if (timing.warmupNanos > 0 && !warmedUp) {
      warmedUp = true;
      new WarmUp(preparedGeometry, options).run(timing.warmupNanos);
    }
  }

  private Searcher newSearcher(Geometry g) {
    Evaluator learned = Weights.forGeometry(g);
    return new Searcher(g, learned != null ? learned : new HandEval(g), tt, options, vcfTable);
  }

  /** Selects the tables of a prepared size; false if this size has none. */
  private boolean usePreparedSize(int rows, int cols) {
    for (int i = 0; i < PREPARED_SIZES.length; i++) {
      if (preparedGeometry[i].sameSize(rows, cols)) {
        searcher = preparedSearcher[i];
        geometry = preparedGeometry[i];
        return true;
      }
    }
    return false;
  }

  /**
   * Builds the tables of an unprepared size. This runs in the second call, where running late costs nothing: the
   * first call of the turn played without search, and no blast can be due on the first turns of a size.
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
      cutUntil = 0;
      lastOverrun = 0;
      stall = 0;
    }
    int move = choose(s, t0, callId == 1);
    int col = move == Tactics.NONE ? -1 : Tactics.column(move);
    boolean special = move != Tactics.NONE && Tactics.isSpecial(move);
    if (col < 0 || col >= s.cols || field.isColumnFull(col)) {
      col = safeColumn(field);
      special = false;
    }
    boolean inTime = Clock.nanos() - t0 <= SAFE_FINISH_NANOS;
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
    long t0 = Clock.nanos();
    int rows = field.getNumberOfRows();
    int cols = field.getNumberOfColumns();
    if (!building && rows >= 1 && cols >= 1 && rows * cols <= MAX_MODEL_CELLS && cols <= MAX_SEARCH_COLS
        && (geometry == null || !geometry.sameSize(rows, cols)) && !usePreparedSize(rows, cols)) {
      buildBoardSize(rows, cols);
      return false;
    }
    Decision d = decision;
    if (d == null || !d.finishedInTime() || d.callId() != calls || !secondCallInTime(firstCallStart, t0)) {
      return false;
    }
    int col = d.column();
    if (col < 0 || col >= cols || field.isColumnFull(col) || Snapshot.fingerprint(field) != d.fingerprint()) {
      return false;
    }
    if (d.special() && tracker.ownSpecialReady()) {
      tracker.noteSpecialRequested();
      return true;
    }
    if (tracker.turn() <= timing.warmTurns) {
      if (warmUp == null) {
        warmUp = new WarmUp(preparedGeometry, options);
      }
      warmUp.run(timing.ponderNanos - (Clock.nanos() - t0));
    } else {
      ponder(col, t0);
    }
    return false;
  }

  /** False if the second call starts so late that the first may have timed out. */
  static boolean secondCallInTime(long firstCallStart, long secondCallStart) {
    return secondCallStart - firstCallStart < SECOND_CALL_LATE_NANOS;
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
        searchedPly + 1, t0, timing.ponderNanos);
    if (searcher.lastMaxGapNanos() > stall) {
      stall = searcher.lastMaxGapNanos();
    }
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
    tracker.beginTurn(Snapshot.read(field));
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

  private int choose(Snapshot s, long t0, boolean firstCall) {
    lastResult = null;
    searchedBoard = null;
    if (s.rows < 1 || s.cols < 1 || s.rows * s.cols > MAX_MODEL_CELLS) {
      return Tactics.NONE;
    }
    if (timing.centreOnEmpty && s.discs == 0) {
      return centreOrder(s.cols, 0);
    }
    if ((geometry == null || !geometry.sameSize(s.rows, s.cols)) && !usePreparedSize(s.rows, s.cols)) {
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
      long hard = clock.budget(t0, tracker.plyIndex(), stopNow(firstCall), lastScore, empty);
      searcher.setStallLimit(timing.stallLimitNanos);
      searcher.setBlastStop(BLAST_STOP_NANOS);
      Searcher.Result r = searcher.run(board, me, cdMe, cdOpp, tracker.plyIndex(), t0, hard);
      lastResult = r;
      lastOverrun = Clock.nanos() - t0 - hard;
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
    return new Tactics(geometry).choose(board, me, cdMe, cdOpp, Clock.nanos() + TACTICS_NANOS);
  }

  /** The first-call stop on this turn: by own turn, the first call of an instance lower, and cut after trouble. */
  private long stopNow(boolean firstCall) {
    int turn = tracker.turn() - 1;
    long stop = timing.stop(turn);
    if (timing.cutTurns > 0) {
      if (tracker.lostPreviousTurn() || lastOverrun >= timing.overrunNanos) {
        cutUntil = turn + timing.cutTurns;
      }
      if (turn <= cutUntil) {
        stop = Math.min(stop, timing.cutNanos);
      }
    }
    if (timing.stallLimitNanos > 0) {
      stop = Math.min(stop, Math.max(MIN_STOP_NANOS, timing.stallLimitNanos - stall / 100 * timing.stallWeightPercent));
    }
    return firstCall ? Math.min(stop, timing.firstCallNanos) : stop;
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
