package ch.qc.tools;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.eval.LearnedEval;
import ch.qc.bot.eval.Weights;
import ch.qc.bot.rules.Geometry;
import java.nio.file.Path;
import java.util.function.Function;

/** Evaluation choice for the harness tools: the hand tables, or learned weights from a .qcw file. */
final class Evaluators {
  private Evaluators() {}

  static Function<Geometry, Evaluator> factory(String weights) {
    if (weights == null || weights.equals("hand")) {
      return HandEval::new;
    }
    // decode once; the expanded tables are immutable and shared by every engine
    java.util.Map<String, LearnedEval> cache = new java.util.concurrent.ConcurrentHashMap<>();
    return g -> cache.computeIfAbsent(g.rows + "x" + g.cols, key -> {
      try {
        return Weights.fromFile(Path.of(weights), g);
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    });
  }
}
