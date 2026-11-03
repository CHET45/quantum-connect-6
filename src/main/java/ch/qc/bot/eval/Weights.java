package ch.qc.bot.eval;

import ch.qc.bot.rules.Geometry;

/**
 * Where the learned evaluation comes from (design 3.7): the blobs compiled into {@code EvalBlob}, one per board
 * size, else none and the bot uses the hand tables. The blobs are decoded and expanded once per JVM; the results
 * are immutable and shared by all bots. Harness tools load .qcw files themselves (ch.qc.tools.WeightFiles) and pass the evaluator in.
 */
public final class Weights {
  private Weights() {}

  private static final class Holder {
    static final LearnedEval[] EVALS = load();
  }

  /** Decodes the weights now (call from a constructor, outside any timed call). */
  public static void preload() {
    LearnedEval[] ignored = Holder.EVALS;
  }

  /** The learned evaluation if weights for this board size are available, else null. */
  public static Evaluator forGeometry(Geometry g) {
    for (LearnedEval e : Holder.EVALS) {
      if (e != null && e.fits(g)) {
        return e;
      }
    }
    return null;
  }

  /** A learned evaluation from an inflated blob, or null if the blob is broken or does not match its spec. */
  public static LearnedEval fromBlob(WeightBlob blob) {
    if (blob == null) {
      return null;
    }
    FeatureSpec spec = new FeatureSpec(new Geometry(blob.rows, blob.cols, blob.k));
    return blob.matches(spec) ? new LearnedEval(spec, blob) : null;
  }

  private static LearnedEval[] load() {
    // a broken or mismatched blob must never stop the bot: that board size falls back to the hand tables
    LearnedEval[] evals = new LearnedEval[EvalBlob.BLOBS.length];
    for (int i = 0; i < evals.length; i++) {
      evals[i] = fromBlob(WeightBlob.parse(decodeBase64(EvalBlob.BLOBS[i])));
    }
    return evals;
  }

  /** Decodes concatenated standard Base64 chunks; null on a malformed input. */
  static byte[] decodeBase64(String[] chunks) {
    int chars = 0;
    for (String c : chunks) {
      chars += c.length();
    }
    if (chars % 4 != 0) {
      return null;
    }
    int pad = 0;
    for (String c : chunks) {
      for (int i = 0; i < c.length(); i++) {
        pad += c.charAt(i) == '=' ? 1 : 0;
      }
    }
    if (pad > 2) {
      return null;
    }
    byte[] out = new byte[chars / 4 * 3 - pad];
    int acc = 0;
    int bits = 0;
    int o = 0;
    for (String c : chunks) {
      for (int i = 0; i < c.length(); i++) {
        char ch = c.charAt(i);
        if (ch == '=') {
          continue;
        }
        int v = ch >= 'A' && ch <= 'Z' ? ch - 'A'
            : ch >= 'a' && ch <= 'z' ? ch - 'a' + 26
            : ch >= '0' && ch <= '9' ? ch - '0' + 52
            : ch == '+' ? 62 : ch == '/' ? 63 : -1;
        if (v < 0) {
          return null;
        }
        acc = (acc << 6) | v;
        bits += 6;
        if (bits >= 8) {
          bits -= 8;
          if (o < out.length) {
            out[o++] = (byte) (acc >> bits);
          }
          acc &= (1 << bits) - 1;
        }
      }
    }
    return o == out.length ? out : null;
  }
}
