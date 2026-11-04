package ch.qc.bot;

import ch.qc.bot.eval.Evaluator;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.eval.Weights;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import ch.qc.bot.search.Searcher;
import ch.qc.bot.search.Tactics;
import ch.qc.bot.search.TranspositionTable;
import ch.qc.bot.search.Vcf;
import java.util.SplittableRandom;

/**
 * Short real games against itself on every prepared size, both blasts ready from the start, on throwaway tables. Most
 * JIT work in a cold game is recompilation: the JIT compiles a method for the branches seen so far, a new branch (a
 * blast, a stacked threat, a nearly full board) throws the code away, and it compiles again, while the search runs slow
 * and competes with the compiler for CPU. Games like these show those branches before the first timed call.
 */
final class WarmUp {
  /** Boards of the constructor's warm-up: all that matters is that they show the code a range of shapes. */
  private static final int[][] SIZES = {{8, 12}, {15, 11}, {15, 22}};
  private static final long MOVE_NANOS = 8_000_000L;
  private static final int COOLDOWN = 10;

  private final Geometry[] sizes;
  private final Searcher[] searchers;
  private final Board[] boards;
  private final int[] toMove;
  private final int[] plies;
  private final int[][] counter;
  private final SplittableRandom rnd = new SplittableRandom(0x5EEDL);
  private int round;

  /** The warm-up of the constructor, before the board is known. */
  static WarmUp beforeTheGame(Searcher.Options options) {
    Geometry[] sizes = new Geometry[SIZES.length];
    for (int i = 0; i < SIZES.length; i++) {
      sizes[i] = new Geometry(SIZES[i][0], SIZES[i][1]);
    }
    return new WarmUp(sizes, options);
  }

  WarmUp(Geometry[] sizes, Searcher.Options options) {
    this.sizes = sizes;
    TranspositionTable tt = new TranspositionTable(16);
    Vcf.Table vcf = new Vcf.Table(14);
    searchers = new Searcher[sizes.length];
    boards = new Board[sizes.length];
    toMove = new int[sizes.length];
    plies = new int[sizes.length];
    counter = new int[sizes.length][3];
    for (int i = 0; i < sizes.length; i++) {
      Evaluator learned = Weights.forGeometry(sizes[i]);
      searchers[i] = new Searcher(sizes[i], learned != null ? learned : new HandEval(sizes[i]), tt, options, vcf);
      startGame(i);
    }
  }

  /** Plays moves until the time is used up. */
  void run(long nanos) {
    long end = Clock.nanos() + nanos;
    while (true) {
      long now = Clock.nanos();
      if (now >= end) {
        return;
      }
      int i = round++ % sizes.length;
      move(i, now, Math.min(MOVE_NANOS, end - now));
    }
  }

  private void move(int i, long now, long nanos) {
    Board b = boards[i];
    int me = toMove[i];
    int[] cd = counter[i];
    // as a real call does: a fresh board loaded from the cells
    Board root = new Board(sizes[i]);
    root.load(b.cells());
    Searcher.Result r = searchers[i].run(root, me, cd[me], cd[3 - me], plies[i], now, nanos);
    int m = r.move();
    if (m == Tactics.NONE) {
      startGame(i);
      return;
    }
    int c = Tactics.column(m);
    boolean over;
    if (Tactics.isSpecial(m)) {
      over = b.isFull(c) || b.blast(c);
      cd[me] = 1;
    } else {
      over = b.isFull(c) || b.make(c, me);
      cd[me] = Math.min(cd[me] + 1, COOLDOWN);
    }
    plies[i]++;
    toMove[i] = 3 - me;
    if (over || b.topRowFull()) {
      startGame(i);
    }
  }

  /** A new game from a few random drops, with both blasts ready at once so their code runs from the start. */
  private void startGame(int i) {
    Geometry g = sizes[i];
    Board b = new Board(g);
    int colour = 1;
    int placed = 0;
    int discs = g.cells * rnd.nextInt(3, 40) / 100;
    for (int tries = 0; placed < discs && tries < 4 * discs + 8; tries++) {
      int c = rnd.nextInt(g.cols);
      if (b.isFull(c) || b.height(c) + 1 >= g.rows) {
        continue;
      }
      if (b.make(c, colour)) {
        b.unmake(c);
        continue;
      }
      colour = 3 - colour;
      placed++;
    }
    boards[i] = b;
    toMove[i] = colour;
    plies[i] = placed;
    counter[i][1] = COOLDOWN;
    counter[i][2] = COOLDOWN;
  }
}
