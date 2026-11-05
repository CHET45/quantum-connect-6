package ch.qc.bot.eval;

import ch.qc.bot.rules.Geometry;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the learned evaluation comes from (design 3.7): the file named by the system property {@code qc.weights}
 * (development), else the blob compiled into {@code EvalBlob} (the release path), else none and the bot uses the
 * hand tables. The blob is decoded and expanded once per JVM; the result is immutable and shared by all bots.
 */
public final class Weights {
  private Weights() {}

  private static final class Holder {
    static final LearnedEval EVAL = load();
  }

  /** Decodes the weights now (call from a constructor, outside any timed call). */
  public static void preload() {
    Evaluator ignored = Holder.EVAL;
  }

  /** The learned evaluation if weights for this board size are available, else null. */
  public static Evaluator forGeometry(Geometry g) {
    LearnedEval e = Holder.EVAL;
    return e != null && e.fits(g) ? e : null;
  }

  /** A learned evaluation from a .qcw file (harness tools); throws if it does not fit the geometry. */
  public static LearnedEval fromFile(Path file, Geometry g) throws java.io.IOException {
    WeightBlob blob = WeightBlob.parse(Files.readAllBytes(file));
    if (blob.rows != g.rows || blob.cols != g.cols) {
      throw new IllegalArgumentException(file + " is for " + blob.rows + "x" + blob.cols);
    }
    return new LearnedEval(new FeatureSpec(g), blob);
  }

  private static LearnedEval load() {
    try {
      byte[] raw;
      String file = System.getProperty("qc.weights");
      if (file != null) {
        raw = Files.readAllBytes(Path.of(file));
      } else if (EvalBlob.CHUNKS.length > 0) {
        raw = EvalBlob.bytes();
      } else {
        return null;
      }
      WeightBlob blob = WeightBlob.parse(raw);
      return new LearnedEval(new FeatureSpec(new Geometry(blob.rows, blob.cols, blob.k)), blob);
    } catch (Exception | OutOfMemoryError e) {
      // a broken or mismatched blob must never stop the bot: fall back to the hand tables
      return null;
    }
  }
}
