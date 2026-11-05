package ch.qc.bot.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import org.junit.jupiter.api.Test;

/** Draw scores with contempt: worse than equal for the searching colour, at even and odd plies alike. */
class ContemptTest {
  private static final Geometry G = new Geometry(8, 12);
  private static final long BUDGET = 50_000_000L;

  private static Searcher searcher(int contempt) {
    Searcher.Options o = Searcher.Options.DEFAULT;
    return new Searcher(G, new HandEval(G), new TranspositionTable(16),
        new Searcher.Options(o.rootVcf, o.opponentVcf, o.leafVcf, o.exactLeaves, o.razor, contempt));
  }

  /** Board full except the top cell of column col, coloured so that no line is longer than two. */
  private static Board fullBut(int col) {
    Board b = new Board(G);
    for (int c = 0; c < G.cols; c++) {
      for (int r = 0; r < G.rows - (c == col ? 1 : 0); r++) {
        assertFalse(b.make(c, colour(c, r)));
      }
    }
    return b;
  }

  private static int colour(int c, int r) {
    return ((c / 2 + r) & 1) + 1;
  }

  @Test
  void fillingTheBoardScoresMinusContempt() {
    int col = 5;
    int me = colour(col, G.rows - 1);
    assertEquals(0, searcher(0).run(fullBut(col), me, 0, 0, 95, Clock.nanos(), BUDGET).score());
    assertEquals(-64, searcher(64).run(fullBut(col), me, 0, 0, 95, Clock.nanos(), BUDGET).score());
  }

  @Test
  void turnCapAfterTheOpponentsReply() {
    // turn 999 is the last: the cap draw comes at ply 1 (opponent to move)
    assertEquals(-64, searcher(64).run(new Board(G), 1, 0, 0, 999, Clock.nanos(), BUDGET).score());
  }

  @Test
  void turnCapAfterOurNextTurn() {
    // turn 998: the opponent's reply is the last turn, the cap draw comes at ply 2 (us to move)
    assertEquals(-64, searcher(64).run(new Board(G), 1, 0, 0, 998, Clock.nanos(), BUDGET).score());
    assertEquals(0, searcher(0).run(new Board(G), 1, 0, 0, 998, Clock.nanos(), BUDGET).score());
  }
}
