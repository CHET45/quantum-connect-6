package ch.qc.bot.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qc.bot.Clock;
import ch.qc.bot.eval.HandEval;
import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;
import org.junit.jupiter.api.Test;

/** Draw mode: an own line only when every other move loses; the opponent's wins stay wins. */
class DrawModeTest {
  private static final Geometry G = new Geometry(8, 12);
  private static final long BUDGET = 40_000_000L;

  private static Searcher searcher(int drawColour) {
    Searcher s = new Searcher(G, new HandEval(G), new TranspositionTable(16));
    s.setDrawColour(drawColour);
    return s;
  }

  /** Colour 1 has five on the bottom row in columns 0-4: a drop in column 5 wins for it. */
  private static Board ownFive() {
    Board b = new Board(G);
    for (int c = 0; c < 5; c++) {
      assertFalse(b.make(c, 1));
    }
    return b;
  }

  private static void stack(Board b, int col, int colour, int n) {
    for (int i = 0; i < n; i++) {
      assertFalse(b.make(col, colour));
    }
  }

  @Test
  void declinesAnOwnWinWhenNothingIsAtStake() {
    Board b = ownFive();
    b.make(7, 2);
    b.make(9, 2);
    b.make(11, 2);
    b.make(7, 2);
    b.make(9, 2);
    Searcher.Result normal = searcher(0).run(b, 1, 0, 0, 10, Clock.nanos(), BUDGET);
    assertEquals(5, normal.move());
    Searcher.Result draw = searcher(1).run(b, 1, 0, 0, 10, Clock.nanos(), BUDGET);
    assertNotEquals(5, Tactics.column(draw.move()));
    assertTrue(draw.score() > Searcher.DRAW_WIN, "score " + draw.score());
  }

  @Test
  void blocksTheOpponentRatherThanWin() {
    Board b = ownFive();
    stack(b, 10, 2, 5);
    Searcher.Result draw = searcher(1).run(b, 1, 0, 0, 10, Clock.nanos(), BUDGET);
    assertEquals(10, draw.move());
  }

  @Test
  void winsWhenEveryOtherMoveLoses() {
    Board b = ownFive();
    stack(b, 10, 2, 5);
    stack(b, 8, 2, 5);
    Searcher.Result draw = searcher(1).run(b, 1, 0, 0, 10, Clock.nanos(), BUDGET);
    assertEquals(5, draw.move());
    assertEquals(Searcher.DRAW_WIN, draw.score());
  }

  @Test
  void theOpponentStillWinsInASearchForIt() {
    // the second call searches for the opponent: its wins are wins
    Board b = ownFive();
    stack(b, 10, 2, 5);
    Searcher.Result r = searcher(1).run(b, 2, 0, 0, 11, Clock.nanos(), BUDGET);
    assertEquals(10, r.move());
    assertTrue(r.score() > Searcher.MATE_BOUND);
  }

  @Test
  void tacticsDeclineAndWinOnlyWhenLost() {
    Board b = ownFive();
    b.make(7, 2);
    b.make(9, 2);
    Tactics t = new Tactics(G);
    assertEquals(5, t.choose(b, 1, 0, 0, Clock.nanos() + BUDGET));
    t.noWin = true;
    assertNotEquals(5, Tactics.column(t.choose(b, 1, 0, 0, Clock.nanos() + BUDGET)));
    stack(b, 10, 2, 5);
    assertEquals(10, t.choose(b, 1, 0, 0, Clock.nanos() + BUDGET));
    stack(b, 8, 2, 5);
    assertEquals(5, t.choose(b, 1, 0, 0, Clock.nanos() + BUDGET));
  }
}
