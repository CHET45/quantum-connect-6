package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatchClockTest {
  private static final long BASE = 62_000_000L;
  private static final long MS = 1_000_000L;

  @Test
  void fullStopEarlyOrWithFewSamples() {
    assertEquals(BASE, MatchClock.budgetFor(1_000 * MS, 10, BASE, 0, 200));
    // 100 plies at 80 ms: 19 s left before the reserve, about 237 plies
    assertEquals(BASE, MatchClock.budgetFor(8_000 * MS, 100, BASE, 500, 200));
  }

  @Test
  void shrinksNearTheEndUnlessWorse() {
    // 300 plies at 80 ms: 3 s left before the reserve, about 37 plies
    long level = MatchClock.budgetFor(24_000 * MS, 300, BASE, 0, 200);
    long winning = MatchClock.budgetFor(24_000 * MS, 300, BASE, 500, 200);
    assertTrue(level < BASE && level >= MatchClock.LEVEL_MIN_NANOS, "level " + level);
    assertTrue(winning < level && winning >= MatchClock.WINNING_MIN_NANOS, "winning " + winning);
    assertEquals(BASE, MatchClock.budgetFor(24_000 * MS, 300, BASE, MatchClock.WORSE, 200));
    // past the reserve
    assertEquals(MatchClock.WINNING_MIN_NANOS, MatchClock.budgetFor(28_000 * MS, 350, BASE, 30_000, 200));
  }

  @Test
  void neverAboveBase() {
    long small = 20_000_000L;
    assertEquals(small, MatchClock.budgetFor(28_000 * MS, 350, small, 0, 200));
  }

  @Test
  void aNearlyFullBoardNeedsFewPlies() {
    // 240 plies at 80 ms: 7.8 s left before the reserve, about 97 plies; enough for 40 empty cells, not for 150
    assertEquals(BASE, MatchClock.budgetFor(19_200 * MS, 240, BASE, 0, 40));
    assertTrue(MatchClock.budgetFor(19_200 * MS, 240, BASE, 0, 150) < BASE);
  }

  @Test
  void restartsOnANewGame() {
    MatchClock c = new MatchClock();
    c.beginTurn(0, 1, 0);
    c.beginTurn(24_000 * MS, 150, 298);
    assertTrue(c.budget(24_000 * MS, 298, BASE, 0, 200) < BASE);
    c.beginTurn(25_000 * MS, 1, 1);
    assertEquals(BASE, c.budget(26_000 * MS, 41, BASE, 0, 200));
  }
}
