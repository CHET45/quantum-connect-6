package ch.qc.bot;

/**
 * The final-day switches: the rules and times the organisers announce on 2026-11-06 at 9:00. Set them here and nowhere
 * else, or let night/tools/final-build.ps1 write this file. The values below are the core-2.0 engine (six in a row, 3x3
 * blast, cooldown 10, 100 ms per call, 30 s per game), and with them the bot is exactly final-base (rc8 play).
 *
 * <p>Every value is read once, in a static initialiser of the class that uses it; out-of-range rules are clamped by
 * {@link ch.qc.bot.rules.Rules#of}, and AnnouncedTest fails the build if any value here would be clamped.
 */
final class Announced {
  private Announced() {}

  /** Connected quarks needed to win (2..9; the tables exist only for 6, other k play with the hand evaluation). */
  static final int K = 6;
  /** Blast half width and half height: 1 and 1 is the 3x3 block, 2 and 2 is 5x5, 0 and 0 clears only the landing cell. */
  static final int BLAST_W = 1;
  static final int BLAST_H = 1;
  /** Turns between specials: a special is honoured once the counter of turns since the last one reaches this. */
  static final int COOLDOWN = 10;
  /** Time limit of one engine call, in ms. The bot still measures a shorter limit itself. */
  static final long CALL_LIMIT_MS = 100;
  /** Wall-clock time after which a running game is a draw, in seconds (both bots together). */
  static final long MATCH_SECONDS = 30;
  /** Real search in the constructor until this many ms have passed; 0 for none (if the constructor is timed). */
  static final long WARMUP_MS = 0;
}
