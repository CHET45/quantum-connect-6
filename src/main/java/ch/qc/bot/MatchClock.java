package ch.qc.bot;

/**
 * Time per move under the server's shared match clock: a game still running after 30 s of wall-clock time (both
 * bots together) is a draw. From the time used per ply so far the clock estimates how many plies are left; when
 * they will not cover what the board can still need, the hard stop shrinks so a won or level game ends before the
 * draw, the faster the better our last search score. A worse position keeps the full stop, since a draw on time is
 * then good for us. The 30 s cannot be measured beforehand and stay an assumption, also under another call limit; a
 * game that outlives the assumed clock proves it too short, and the clock then doubles.
 */
final class MatchClock {
  static final long MATCH_NANOS = 30_000_000_000L;
  /** Kept back for what the clock cannot see: the opponent's first move, engine and server overhead. */
  static final long RESERVE_NANOS = 3_000_000_000L;
  /** Plies needed before the per-ply estimate is trusted. */
  static final int MIN_SAMPLE_PLIES = 20;
  /** Above this many estimated plies left the full stop is used; at RUSH_PLIES and below the minimum. */
  static final int CALM_PLIES = 150;
  static final int RUSH_PLIES = 30;
  /** Plies a game can last beyond its empty cells: blasts empty cells again. */
  static final int BLAST_PLIES = 30;
  /** Plies of slack the clock must keep beyond the need before the second call may spend any of it. */
  static final int PONDER_MARGIN_PLIES = 20;
  static final long WINNING_MIN_NANOS = 15_000_000L;
  static final long LEVEL_MIN_NANOS = 30_000_000L;
  /** Search scores (1/256 logit, our view) at or above WINNING are a winning position, at or below WORSE worse. */
  static final int WINNING = 128;
  static final int WORSE = -256;

  private long start = -1;
  private int startPly;
  private long matchNanos = MATCH_NANOS;

  /** Call at the start of every own turn; turn 1 (the tracker resets it for a new game) restarts the clock. */
  void beginTurn(long t0, int turn, int ply) {
    if (turn == 1 || start < 0) {
      start = t0;
      startPly = ply;
      matchNanos = MATCH_NANOS;
    }
    while (t0 - start > matchNanos) {
      matchNanos *= 2;
    }
  }

  /**
   * What one own turn may spend if the clock is to last and the opponent spends as much: long call limits must not use
   * themselves up before the per-ply estimate has its samples. This does nothing at short limits.
   */
  long turnCap() {
    return 2 * ((matchNanos - matchNanos / 10) / (CALM_PLIES + PONDER_MARGIN_PLIES));
  }

  /** Hard stop for this move: base, or less when the match clock runs out and we are not worse. */
  long budget(long t0, int ply, long base, int lastScore, int emptyCells) {
    return budgetFor(t0 - start, ply - startPly, base, lastScore, emptyCells, matchNanos, CallLimit.PRIOR_NANOS);
  }

  /** As above with the minimum stops taken from the current call limit. */
  long budget(long t0, int ply, long base, int lastScore, int emptyCells, long callLimit) {
    return budgetFor(t0 - start, ply - startPly, base, lastScore, emptyCells, matchNanos, callLimit);
  }

  /** May the second call search now? It spends the shared 30 s, so only while they clearly cover what the board needs. */
  boolean ponderAllowed(long t0, int ply, int lastScore, int emptyCells) {
    return ponderAllowedFor(t0 - start, ply - startPly, lastScore, emptyCells, matchNanos);
  }

  static boolean ponderAllowedFor(long elapsed, int plies, int lastScore, int emptyCells) {
    return ponderAllowedFor(elapsed, plies, lastScore, emptyCells, MATCH_NANOS);
  }

  static boolean ponderAllowedFor(long elapsed, int plies, int lastScore, int emptyCells, long match) {
    if (plies < MIN_SAMPLE_PLIES || elapsed <= 0 || lastScore <= WORSE) {
      return true;
    }
    long left = match - match / 10 - elapsed;
    if (left <= 0) {
      return false;
    }
    return left / (elapsed / plies + 1) >= Math.min(CALM_PLIES, emptyCells + BLAST_PLIES) + PONDER_MARGIN_PLIES;
  }

  static long budgetFor(long elapsed, int plies, long base, int lastScore, int emptyCells) {
    return budgetFor(elapsed, plies, base, lastScore, emptyCells, MATCH_NANOS, CallLimit.PRIOR_NANOS);
  }

  static long budgetFor(long elapsed, int plies, long base, int lastScore, int emptyCells, long match, long callLimit) {
    if (plies < MIN_SAMPLE_PLIES || elapsed <= 0 || lastScore <= WORSE) {
      return base;
    }
    long min = Math.min(base, (lastScore >= WINNING ? WINNING_MIN_NANOS : LEVEL_MIN_NANOS) / 100 * (callLimit / 1_000_000L));
    long left = match - match / 10 - elapsed;
    if (left <= 0) {
      return min;
    }
    long pliesLeft = left / (elapsed / plies + 1);
    long need = Math.min(CALM_PLIES, emptyCells + BLAST_PLIES);
    if (pliesLeft >= need) {
      return base;
    }
    if (pliesLeft <= RUSH_PLIES) {
      return min;
    }
    return min + (base - min) * (pliesLeft - RUSH_PLIES) / (need - RUSH_PLIES);
  }
}
