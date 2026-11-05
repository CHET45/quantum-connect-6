package ch.qc.bot.rules;

/**
 * Lookup tables over base-3 window codes. A window of K cells is encoded as sum(digit[i] * 3^i) with
 * digit 0 = empty, 1 = first player, 2 = second player (absolute colours, index 0 of colour arrays unused).
 */
public final class Tables {
  public final int k;
  public final int codes;
  public final int[] pow3;
  /** six[colour]: the code of a window filled with that colour. */
  public final int[] six;
  /** thrPos[colour][code]: digit index of the single empty cell when the other K-1 digits are colour, else -1. */
  public final byte[][] thrPos;
  /** live3[colour][code]: at least three digits of colour and none of the other colour. */
  public final boolean[][] live3;
  /** swap12[code]: the same window with the two colours exchanged. */
  public final short[] swap12;

  public Tables(int k) {
    this.k = k;
    pow3 = new int[k + 1];
    pow3[0] = 1;
    for (int i = 1; i <= k; i++) {
      pow3[i] = pow3[i - 1] * 3;
    }
    codes = pow3[k];
    six = new int[] {0, (codes - 1) / 2, codes - 1};
    thrPos = new byte[3][codes];
    live3 = new boolean[3][codes];
    swap12 = new short[codes];
    int[] count = new int[3];
    for (int code = 0; code < codes; code++) {
      count[0] = count[1] = count[2] = 0;
      int emptyAt = -1;
      int swapped = 0;
      for (int i = 0, c = code; i < k; i++, c /= 3) {
        int d = c % 3;
        count[d]++;
        if (d == 0) {
          emptyAt = i;
        }
        swapped += (d == 0 ? 0 : 3 - d) * pow3[i];
      }
      swap12[code] = (short) swapped;
      for (int x = 1; x <= 2; x++) {
        int other = 3 - x;
        thrPos[x][code] = (byte) (count[x] == k - 1 && count[0] == 1 ? emptyAt : -1);
        live3[x][code] = count[x] >= 3 && count[other] == 0;
      }
    }
  }
}
