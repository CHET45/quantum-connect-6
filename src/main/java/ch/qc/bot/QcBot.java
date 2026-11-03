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
 * the hard stop {@link #HARD_NANOS} ({@link #FIRST_CALL_HARD_NANOS} on the first call of an instance; less near
 * the end of the shared match clock, {@link MatchClock}), on top of the per-call pipeline (snapshot, turn tracking, board load, guarded two-call
 * protocol). The M1 root tactics are the fallback if the search cannot run.
 *
 * <p>Upload rules: only basic Java (no java.lang.System, java.io, java.nio, reflection, threads or runtime access),
 * and no exceptions thrown or caught anywhere in the bot; UploadRulesTest checks the compiled classes. Time comes
 * from {@link Clock}.
 */
public final class QcBot implements Algorithm {
  /** A special is never raised if the first call took longer than this (it may have timed out, R10, R15). */
  static final long SAFE_FINISH_NANOS = 85_000_000L;
  /** Boards above this many cells skip the board model and use the field directly (design 5.6). */
  static final int MAX_MODEL_CELLS = 4096;
  /** Hard stop of the search, measured from the entry of accelerateQuark (design 2.8). */
  static final long HARD_NANOS = 62_000_000L;
  static final long FIRST_CALL_HARD_NANOS = 40_000_000L;
  /** Search is limited to boards whose column index fits the transposition-table move field. */
  static final int MAX_SEARCH_COLS = 255;
  /** Budget of the fallback tactics. */
  static final long TACTICS_NANOS = 10_000_000L;

  private final TurnTracker tracker = new TurnTracker();
  private final MatchClock clock = new MatchClock();
  /** Score of our latest search, our view; 0 at the start of a game. */
  private int lastScore;
  private final TranspositionTable tt;
  private final Searcher.Options options;
  private final Vcf.Table vcfTable;
  private Geometry geometry;
  private Searcher searcher;
  private int calls;
  private volatile Decision decision;
  private volatile Searcher.Result lastResult;

  public QcBot() {
    this(Searcher.Options.DEFAULT);
  }

  /** A bot with non-default search switches (harness experiments). */
  public QcBot(Searcher.Options options) {
    this.options = options;
    tt = TranspositionTable.forBot();
    Weights.preload();
    vcfTable = new Vcf.Table(16);
  }

  @Override
  public int accelerateQuark(QuantumField field) {
    long t0 = Clock.nanos();
    int callId = ++calls;
    decision = null;
    Snapshot s = Snapshot.read(field);
    tracker.beginTurn(s);
    clock.beginTurn(t0, tracker.turn(), tracker.plyIndex());
    if (tracker.turn() == 1) {
      lastScore = 0;
    }
    int move = choose(s, t0, callId == 1);
    int col = move == Tactics.NONE ? -1 : Tactics.column(move);
    boolean special = move != Tactics.NONE && Tactics.isSpecial(move);
    if (col < 0 || col >= s.cols || field.isColumnFull(col)) {
      col = safeColumn(field);
      special = false;
    }
    decision = new Decision(callId, s.fingerprint, col, special, Clock.nanos() - t0 <= SAFE_FINISH_NANOS);
    return col;
  }

  @Override
  public boolean useSpecialMove(QuantumField field) {
    Decision d = decision;
    if (d == null || !d.special() || !d.finishedInTime() || d.callId() != calls || !tracker.ownSpecialReady()) {
      return false;
    }
    int col = d.column();
    if (col < 0 || col >= field.getNumberOfColumns() || field.isColumnFull(col)) {
      return false;
    }
    if (Snapshot.fingerprint(field) != d.fingerprint()) {
      return false;
    }
    tracker.noteSpecialRequested();
    return true;
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

  /** Search statistics of the latest call (harness only). */
  public Searcher.Result lastResult() {
    return lastResult;
  }

  private int choose(Snapshot s, long t0, boolean firstCall) {
    lastResult = null;
    if (s.rows < 1 || s.cols < 1 || s.rows * s.cols > MAX_MODEL_CELLS) {
      return Tactics.NONE;
    }
    if (geometry == null || !geometry.sameSize(s.rows, s.cols)) {
      geometry = new Geometry(s.rows, s.cols);
      Evaluator learned = Weights.forGeometry(geometry);
      searcher = new Searcher(geometry, learned != null ? learned : new HandEval(geometry), tt, options, vcfTable);
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
      long hard = firstCall ? FIRST_CALL_HARD_NANOS : clock.budget(t0, tracker.plyIndex(), HARD_NANOS, lastScore);
      Searcher.Result r = searcher.run(board, me, cdMe, cdOpp, tracker.plyIndex(), t0, hard);
      lastResult = r;
      if (r.move() != Tactics.NONE) {
        lastScore = r.score();
        return r.move();
      }
    }
    return new Tactics(geometry).choose(board, me, cdMe, cdOpp, Clock.nanos() + TACTICS_NANOS);
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
