package ch.qc.bot;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.eval.Weights;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.util.SplittableRandom;

/**
 * Real searches on made-up mid-game positions with both blasts ready, run in the constructor so the JIT compiles the
 * hot code, blast paths included, before the first timed call. Its tables are thrown away and the bot's stay empty.
 */
final class WarmUp {
  private static final long SLICE_NANOS = 30_000_000L;
  private static final int COOLDOWN = 10;

  private WarmUp() {}

  static void run(Geometry[] sizes, Searcher.Options options, long nanos) {
    long end = Clock.nanos() + nanos;
    TranspositionTable tt = new TranspositionTable(16);
    Vcf.Table vcf = new Vcf.Table(14);
    Searcher[] searchers = new Searcher[sizes.length];
    SplittableRandom rnd = new SplittableRandom(0x5EEDL);
    for (int i = 0; i < sizes.length; i++) {
      Evaluator learned = Weights.forGeometry(sizes[i]);
      searchers[i] = new Searcher(sizes[i], learned != null ? learned : new HandEval(sizes[i]), tt, options, vcf);
    }
    int round = 0;
    while (Clock.nanos() < end) {
      int i = round % sizes.length;
      Geometry g = sizes[i];
      // the fill grows with the round, so early-game and later positions both appear
      int discs = g.cells * (15 + 5 * (round / sizes.length % 7)) / 100;
      Board b = position(g, discs, rnd);
      Board fresh = new Board(g);
      fresh.load(b.cells());
      int colour = 1 + (round & 1);
      long now = Clock.nanos();
      searchers[i].run(fresh, colour, COOLDOWN, COOLDOWN, discs, now, Math.min(SLICE_NANOS, Math.max(0L, end - now)));
      round++;
    }
  }

  /** Random drops around the centre by alternating colours, never completing a line. */
  private static Board position(Geometry g, int discs, SplittableRandom rnd) {
    Board b = new Board(g);
    int colour = 1;
    int placed = 0;
    for (int tries = 0; placed < discs && tries < 4 * discs + 8; tries++) {
      int spread = Math.max(1, g.cols / 2);
      int c = g.cols / 2 + rnd.nextInt(-spread, spread + 1);
      if (c < 0 || c >= g.cols || b.isFull(c) || b.height(c) + 1 >= g.rows) {
        continue;
      }
      if (b.make(c, colour)) {
        b.unmake(c);
        continue;
      }
      colour = 3 - colour;
      placed++;
    }
    return b;
  }
}
