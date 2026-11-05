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
 * tables of this board, whatever its size, and finds out whether the call limit is below 100 ms ({@link CallLimit}); a
 * shorter limit is measured on the next two turns as well.
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
  /** Search is limited to models whose column index fits the transposition-table move field. */
  static final int MAX_SEARCH_COLS = 255;
  /** The stall sensor never takes the first-call stop below this share of the limit. */
  static final int MIN_STOP_PERCENT = 25;
  /** Before the limit is known no search runs with this many discs or fewer. */
  private static final int NO_SEARCH_DISCS = 3;
  /** A search before the limit is known: only harness games with scripted openings get there. */
  static final long UNPROBED_STOP_NANOS = 20_000_000L;
  /** The probe ends here: on a limit of 100 ms a late thread start can hardly turn this into a timeout. */
  static final long PROBE_NANOS = 85_000_000L;
  /** The probe for a limit above 100 ms gives up here and takes this as the limit. */
  static final long PROBE_UP_NANOS = 2_000_000_000L;
  /**
   * An opponent's whole turn longer than this share of the limit, plus a little, hints at a longer limit if this many
   * in a row last about the same: a stall gives a long turn but not the same one again.
   */
  private static final int HINT_PERCENT = 110;
  private static final long HINT_SLACK_NANOS = 5_000_000L;
  private static final int HINT_TURNS = 5;
  private static final int HINT_AGREE_PERCENT = 15;
  /** This many lost turns since the limit was last measured call for measuring it again. */
  private static final int FORFEITS_TO_MEASURE = 2;
  /** Budget of the fallback tactics. */
  static final long TACTICS_NANOS = 10_000_000L;
  /** A far drop ({@link Timing#wideTurns}) only while the search score is within this, and this far from any disc. */
  private static final int WIDE_QUIET = 150;
  private static final int WIDE_ROOM = 20;

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
  private long lastReturn;
  private long hintTurn;
  private int hintCount;
  /** An opponent that loses turns says nothing about the limit, even if some of its turns are long. */
  private boolean hintClosed;
  /** Own lost turns at the last measurement of the limit. */
  private int forfeitsMeasured;
  private Geometry geometry;
  private Searcher searcher;
  /** The part of the board that geometry and searcher cover; set last, so a set crop means a usable model. */
  private volatile Crop crop;
  /** Added to the column of what {@link #choose} returns to get the real column, when no crop was used. */
  private int moveOffset;
  /** The crop that what {@link #choose} returns is a column of; null when none was used. */
  private Crop moveCrop;
  private Crop searchedCrop;
  /** Far drops played this game ({@link Timing#wideTurns}). */
  private int widePlayed;
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
    long constructed = Clock.nanos();
    this.options = options;
    this.timing = timing;
    tt = TranspositionTable.forBot();
    Weights.preload();
    vcfTable = new Vcf.Table(16);
    probeUpward = timing.probeUpward;
    if (timing.warmupNanos > 0 && !warmedUp) {
      warmedUp = true;
      // the whole constructor stays inside this time, also where loading the tables is slow
      WarmUp warm = WarmUp.beforeTheGame(options);
      long left = timing.warmupNanos - (Clock.nanos() - constructed);
      if (left > 0) {
        warm.run(left);
      }
    }
  }

  private Searcher newSearcher(Geometry g) {
    Evaluator learned = Weights.forGeometry(g);
    return new Searcher(g, learned != null ? learned : new HandEval(g), tt, options, vcfTable);
  }

  /**
   * Builds the tables of this crop of the board. The first build runs in the second call, where running late costs
   * nothing: the first call of the turn played without search, and no blast can be due on the first turn. A crop that
   * has to change later is small, so that build takes a little of the first call's own time.
   */
  private void buildModel(Crop c) {
    building = true;
    Geometry g = new Geometry(c.rows, c.cols);
    Searcher s = newSearcher(g);
    searcher = s;
    geometry = g;
    crop = c;
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
      widePlayed = 0;
      lastScore = 0;
      stall = 0;
      forfeitsMeasured = 0;
    } else if (lastReturn > 0) {
      noteOpponentTurn(t0 - lastReturn);
    }
    int move = choose(s, t0);
    int col = move == Tactics.NONE ? -1 : realColumn(Tactics.column(move));
    boolean special = move != Tactics.NONE && Tactics.isSpecial(move);
    if (col < 0 || col >= s.cols || field.isColumnFull(col)) {
      col = safeColumn(field);
      special = false;
    }
    long took = Clock.nanos() - t0;
    boolean inTime = took <= blastFinish();
    // the blast is the only defence: keep it while the call is still well inside the limit by our own clock
    if (special && !inTime && took <= limit.nanos() / 10 * 8 && onlyBlastSaves()) {
      inTime = true;
    }
    if (special && !inTime && lastResult != null) {
      // too late to ask for the blast: play the best drop, not a drop in the blast's column
      int drop = searcher.lastBestDrop();
      int dropCol = drop == Tactics.NONE ? -1 : realColumn(Tactics.column(drop));
      if (dropCol >= 0 && dropCol < s.cols && !field.isColumnFull(dropCol)) {
        col = dropCol;
        special = false;
      }
    }
    decision = new Decision(callId, s.fingerprint, col, special, inTime);
    return col;
  }

  /** The real column of a column of what {@link #choose} returned; -1 if there is none. */
  private int realColumn(int modelCol) {
    Crop c = moveCrop;
    if (c == null) {
      return modelCol + moveOffset;
    }
    return modelCol >= 0 && modelCol < c.cols ? c.real(modelCol) : -1;
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
    if (!building && rows >= 1 && cols >= 1 && (crop == null || !crop.isFor(rows, cols))) {
      Crop c = Crop.start(rows, cols, timing.cropAbove);
      if (c != null) {
        buildModel(c);
      }
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
    } else if (probeUpward && limit.upwardOpen() && clock.ponderAllowed(t0, searchedPly, lastScore, searchedEmpty)) {
      probe(field, d, t0, PROBE_UP_NANOS);
    } else {
      ponder(col, t0);
    }
    return false;
  }

  /**
   * Finds out where the call limit lies: the engine applies our move as soon as it gives up on this call, so a change of
   * the field tells when that was. This thread touches the field only by reading it, as every call does, and it ends as
   * soon as the field changes, so it takes nothing from the opponent's turn. A probe that ends at its own time leaves no
   * timeout behind.
   */
  private void probe(QuantumField field, Decision d, long t0, long until) {
    boolean upward = until > PROBE_NANOS;
    while (true) {
      long took = Clock.nanos() - t0;
      // first the time: a stall that ends after the engine gave up must not look like a limit of that length
      if (!upward && took >= until) {
        limit.assumePrior();
        return;
      }
      if (Snapshot.fingerprint(field) != d.fingerprint()) {
        if (upward) {
          limit.upwardSample(took);
        } else {
          limit.measured(took);
        }
        return;
      }
      if (took >= until) {
        limit.upwardSample(until);
        return;
      }
    }
  }

  /** Called with the time since our last return: the opponent's whole turn. */
  private void noteOpponentTurn(long turnNanos) {
    if (!tracker.opponentMoved()) {
      hintClosed = true;
    }
    if (hintClosed || turnNanos <= limit.nanos() / 100 * HINT_PERCENT + HINT_SLACK_NANOS) {
      hintCount = 0;
      return;
    }
    boolean same = hintCount > 0 && Math.abs(turnNanos - hintTurn) <= hintTurn / 100 * HINT_AGREE_PERCENT;
    hintCount = same ? hintCount + 1 : 1;
    hintTurn = turnNanos;
    if (hintCount >= HINT_TURNS) {
      probeUpward = true;
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
    int modelCol = searchedCrop == null ? -1 : searchedCrop.model(col);
    if (timing.ponderNanos <= 0 || tracker.turn() <= timing.ponderFromTurn || b == null || modelCol < 0
        || modelCol >= b.g.cols || b.isFull(modelCol)
        || !clock.ponderAllowed(t0, searchedPly, lastScore, searchedEmpty)) {
      return;
    }
    if (b.make(modelCol, searchedMe) || b.topRowFull()) {
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
    long floor = timing.blastFloorNanos > 0 ? Math.min(limit.scaled(timing.blastFloorNanos, 10), limitNow / 10 * 9)
        : limitNow / 10;
    return Math.max(floor, limit.scaled(timing.safeFinishNanos, 40) - stall);
  }

  /**
   * True if the opponent wins at once in two or more columns of the searched model, so no drop saves the game and a
   * blast is the only defence ({@link Timing#blastDesperate}).
   */
  private boolean onlyBlastSaves() {
    Board b = searchedBoard;
    if (timing.blastDesperate == 0 || b == null) {
      return false;
    }
    int opp = 3 - searchedMe;
    int wins = 0;
    for (int c = 0; c < geometry.cols && wins < 2; c++) {
      if (!b.isFull(c)) {
        if (b.make(c, opp)) {
          wins++;
        }
        b.unmake(c);
      }
    }
    return wins >= 2;
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
    if (crop == null || !crop.isFor(s.rows, s.cols)) {
      Crop c = Crop.start(s.rows, s.cols, timing.cropAbove);
      if (c != null) {
        buildModel(c);
      }
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
    moveOffset = 0;
    moveCrop = null;
    if (s.rows < 1 || s.cols < 1) {
      return Tactics.NONE;
    }
    // with so few discs nothing can be lost by not searching, and a short limit may have cut the probe off once
    if (s.discs == 0 || (s.discs <= NO_SEARCH_DISCS && !limit.known())) {
      moveOffset = centreOrder(s.cols, 0);
      return 0;
    }
    if (building || crop == null || !crop.isFor(s.rows, s.cols)) {
      return Tactics.NONE;
    }
    Crop c = crop.refit(s.cells, timing.cropAbove, timing.cropGaps);
    if (c == null) {
      return Tactics.NONE;
    }
    if (c != crop) {
      buildModel(c);
    }
    // a fresh board per call: a search that outlived its call may still be walking the previous one
    Board board = new Board(geometry);
    if (!board.load(c.cut(s.cells))) {
      return Tactics.NONE;
    }
    moveCrop = c;
    // on an empty board the colour is unknown, but then nothing depends on it
    int me = tracker.myColour() != 0 ? tracker.myColour() : tracker.iAmFirst() ? Board.FIRST : Board.SECOND;
    int cdMe = tracker.myCooldownCounter();
    int cdOpp = tracker.opponentCooldownCounter();
    if (c.cols <= MAX_SEARCH_COLS) {
      int empty = s.rows * s.cols - s.discs;
      long hard = clock.budget(t0, tracker.plyIndex(), stopNow(), lastScore, empty, limit.nanos());
      searcher.setStallLimit(limit.scaled(timing.stallLimitNanos, 50));
      searcher.setBlastStop(Math.max(limit.nanos() / 20, limit.scaled(timing.blastStopNanos, 30) - stall));
      Searcher.Result r = searcher.run(board, me, cdMe, cdOpp, tracker.plyIndex(), t0, hard);
      lastResult = r;
      noteStall(searcher.lastMaxGapNanos());
      if (r.move() != Tactics.NONE) {
        int far = wideDrop(s, r);
        if (far >= 0) {
          widePlayed++;
          lastScore = r.score();
          moveCrop = null;
          moveOffset = far;
          return 0;
        }
        lastScore = r.score();
        searchedBoard = board;
        searchedCrop = c;
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

  /**
   * A free column near the edge farthest from all discs, if this quiet early turn should drop there instead
   * ({@link Timing#wideTurns}); -1 if not.
   */
  private int wideDrop(Snapshot s, Searcher.Result r) {
    if (widePlayed >= timing.wideTurns || s.cols <= timing.cropAbove || tracker.turn() < 2
        || Tactics.isSpecial(r.move()) || Math.abs(r.score()) >= WIDE_QUIET) {
      return -1;
    }
    int lo = s.cols;
    int hi = -1;
    for (int c = 0; c < s.cols; c++) {
      if (s.cells[c * s.rows] != 0) {
        lo = Math.min(lo, c);
        hi = c;
      }
    }
    if (hi < 0) {
      return -1;
    }
    boolean left = lo > s.cols - 1 - hi;
    int room = left ? lo : s.cols - 1 - hi;
    if (room < WIDE_ROOM) {
      return -1;
    }
    int col = left ? 1 : s.cols - 2;
    return s.cells[col * s.rows + s.rows - 1] == 0 ? col : -1;
  }

  /** The first-call stop on this turn: by own turn and call limit, less the stalls seen lately; a lost turn is one. */
  private long stopNow() {
    int turn = tracker.turn() - 1;
    if (tracker.lostPreviousTurn()) {
      limit.closeUpward();
      if (tracker.ownForfeits() - forfeitsMeasured >= FORFEITS_TO_MEASURE) {
        forfeitsMeasured = tracker.ownForfeits();
        limit.measureAgain();
      }
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
    long lostCap = timing.lostCap(tracker.ownForfeits());
    if (lostCap != Long.MAX_VALUE) {
      stop = Math.min(stop, Math.max(min, limit.scaled(lostCap, MIN_STOP_PERCENT)));
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
