package ch.qc.bot;

/**
 * Time per move under the server's shared match clock: a game still running after 30 s of wall-clock time (both
 * bots together) is a draw. From the time used per ply so far the clock estimates how many plies are left; when
 * few are, the hard stop shrinks so a won or level game ends before the draw, the faster the better our last
 * search score. A worse position keeps the full stop, since a draw on time is then good for us.
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
  static final long WINNING_MIN_NANOS = 15_000_000L;
  static final long LEVEL_MIN_NANOS = 30_000_000L;
  /** Search scores (1/256 logit, our view) at or above WINNING are a winning position, at or below WORSE worse. */
  static final int WINNING = 128;
  static final int WORSE = -256;

  private long start = -1;
  private int startPly;

  /** Call at the start of every own turn; turn 1 (the tracker resets it for a new game) restarts the clock. */
  void beginTurn(long t0, int turn, int ply) {
    if (turn == 1 || start < 0) {
      start = t0;
      startPly = ply;
    }
  }

  /** Hard stop for this move: base, or less when the match clock runs out and we are not worse. */
  long budget(long t0, int ply, long base, int lastScore) {
    return budgetFor(t0 - start, ply - startPly, base, lastScore);
  }

  static long budgetFor(long elapsed, int plies, long base, int lastScore) {
    if (plies < MIN_SAMPLE_PLIES || elapsed <= 0 || lastScore <= WORSE) {
      return base;
    }
    long min = Math.min(base, lastScore >= WINNING ? WINNING_MIN_NANOS : LEVEL_MIN_NANOS);
    long left = MATCH_NANOS - RESERVE_NANOS - elapsed;
    if (left <= 0) {
      return min;
    }
    long pliesLeft = left / (elapsed / plies + 1);
    if (pliesLeft >= CALM_PLIES) {
      return base;
    }
    if (pliesLeft <= RUSH_PLIES) {
      return min;
    }
    return min + (base - min) * (pliesLeft - RUSH_PLIES) / (CALM_PLIES - RUSH_PLIES);
  }
}
