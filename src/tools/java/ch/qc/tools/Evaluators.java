package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Geometry;
import java.nio.file.Path;
import java.util.function.Function;

/** Evaluation choice for the harness tools: the hand tables, or learned weights from a .qcw file (sized or generic). */
final class Evaluators {
  private Evaluators() {}

  static Function<Geometry, Evaluator> factory(String weights) {
    if (weights == null || weights.equals("hand")) {
      return HandEval::new;
    }
    if (weights.equals("auto")) {
      // what the bot uses: the embedded tables where they fit the geometry (its k), else the hand tables
      return g -> {
        Evaluator e = ch.qc.bot.eval.Weights.forGeometry(g);
        return e != null ? e : new HandEval(g);
      };
    }
    // decode once; the expanded tables are immutable and shared by every engine
    java.util.Map<String, Evaluator> cache = new java.util.concurrent.ConcurrentHashMap<>();
    return g -> cache.computeIfAbsent(g.rows + "x" + g.cols, key -> {
      try {
        return WeightFiles.load(Path.of(weights), g);
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    });
  }
}
