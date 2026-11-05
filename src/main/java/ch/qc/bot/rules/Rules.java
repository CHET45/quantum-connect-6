package ch.qc.bot.rules;

/**
 * The rules that may change from match to match: the line length that wins (k), the size of the blast (the block it
 * clears reaches blastW columns left and right of the landing cell and blastH rows below and above it) and the cooldown
 * (a special is honoured iff the mover's counter of turns since its last special is at least this). Built once per match
 * and reached everywhere through {@link Geometry#rules}. {@link #DEFAULT} is the core-2.0 engine: six, 3x3, ten.
 *
 * <p>Values out of range are clamped, never refused (the bot must not throw): k to [{@link #MIN_K}, {@link #MAX_K}],
 * the blast half sizes to [0, {@link #MAX_BLAST}], the cooldown to [0, {@link #MAX_COOLDOWN}].
 */
public final class Rules {
  public static final int K_DEFAULT = 6;
  public static final int BLAST_DEFAULT = 1;
  public static final int COOLDOWN_DEFAULT = 10;
  public static final int MIN_K = 2;
  /** Window codes are 3^k and kept in 16-bit arrays: 3^9 = 19,683 fits, 3^10 does not. */
  public static final int MAX_K = 9;
  public static final int MAX_BLAST = 8;
  /** The engine stops at 1,000 turns, 500 per player: a longer cooldown means no special at all. */
  public static final int MAX_COOLDOWN = 1000;

  public static final Rules DEFAULT = new Rules(K_DEFAULT, BLAST_DEFAULT, BLAST_DEFAULT, COOLDOWN_DEFAULT);

  public final int k;
  /** Columns cleared on each side of the landing column. */
  public final int blastW;
  /** Rows cleared below and above the landing row. */
  public final int blastH;
  public final int cooldown;

  private Rules(int k, int blastW, int blastH, int cooldown) {
    this.k = k;
    this.blastW = blastW;
    this.blastH = blastH;
    this.cooldown = cooldown;
  }

  /** A square blast of the given radius (1 is the 3x3 block). */
  public static Rules of(int k, int blastRadius, int cooldown) {
    return of(k, blastRadius, blastRadius, cooldown);
  }

  public static Rules of(int k, int blastW, int blastH, int cooldown) {
    Rules r = new Rules(clamp(k, MIN_K, MAX_K), clamp(blastW, 0, MAX_BLAST), clamp(blastH, 0, MAX_BLAST),
        clamp(cooldown, 0, MAX_COOLDOWN));
    return r.equals(DEFAULT) ? DEFAULT : r;
  }

  public Rules withK(int newK) {
    return of(newK, blastW, blastH, cooldown);
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  public boolean isDefault() {
    return equals(DEFAULT);
  }

  /** The 3x3 blast, for which Board has its fast exact tests. */
  public boolean defaultBlast() {
    return blastW == 1 && blastH == 1;
  }

  /**
   * A column this much taller than the blast column has a disc that falls when the blast lands next to it (within
   * blastW columns): the block reaches blastH rows above the landing cell, and a disc above that falls into it.
   */
  public int fallGap() {
    return blastH + 2;
  }

  /** Counter of the side that just blasted, after its turn: the engine sets it to 0 and adds one. */
  public int cdAfterBlast() {
    return Math.min(1, cooldown);
  }

  /**
   * A cooldown counter (0 .. cooldown) on the scale of {@link #COOLDOWN_DEFAULT}, which the evaluation features and
   * tables were made for: turns left until ready count down to ready at 10. Identity for the default cooldown.
   */
  public int evalCounter(int cd) {
    if (cd >= cooldown) {
      return COOLDOWN_DEFAULT;
    }
    return Math.max(0, COOLDOWN_DEFAULT - (cooldown - cd));
  }

  /** Mixed into the Zobrist seed, so positions under other rules never share table entries; 0 for the default. */
  long hashSalt() {
    if (isDefault()) {
      return 0;
    }
    return ((long) k << 48) ^ ((long) blastW << 40) ^ ((long) blastH << 32) ^ cooldown ^ 0x9E37_79B9_7F4A_7C15L;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Rules)) {
      return false;
    }
    Rules r = (Rules) o;
    return k == r.k && blastW == r.blastW && blastH == r.blastH && cooldown == r.cooldown;
  }

  @Override
  public int hashCode() {
    return ((k * 31 + blastW) * 31 + blastH) * 31 + cooldown;
  }

  @Override
  public String toString() {
    // no string concatenation in bot code: it compiles to invokedynamic, which the upload rules forbid
    return new StringBuilder().append("k=").append(k).append(" blast=").append(2 * blastW + 1).append('x')
        .append(2 * blastH + 1).append(" cooldown=").append(cooldown).toString();
  }
}
