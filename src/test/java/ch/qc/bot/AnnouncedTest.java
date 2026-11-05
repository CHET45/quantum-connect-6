package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.cern.quantumconnect.core.FieldFactory;
import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.rules.Rules;
import ch.qc.starter.RandomBot;
import org.junit.jupiter.api.Test;

/**
 * The final-day switches ({@link Announced}) reach the bot unchanged: no value is clamped, the bot plays by them from
 * its first call, and every budget at the announced call limit stays inside that limit.
 */
class AnnouncedTest {
  private static final long MS = 1_000_000L;

  @Test
  void noAnnouncedRuleIsClamped() {
    Rules r = EngineRules.ANNOUNCED;
    assertEquals(Announced.K, r.k, "k outside 2..9");
    assertEquals(Announced.BLAST_W, r.blastW, "blast half width outside 0..8");
    assertEquals(Announced.BLAST_H, r.blastH, "blast half height outside 0..8");
    assertEquals(Announced.COOLDOWN, r.cooldown, "cooldown outside 0..1000");
  }

  @Test
  void theTimesAreSane() {
    assertTrue(Announced.CALL_LIMIT_MS >= 5 && Announced.CALL_LIMIT_MS <= 10_000, "call limit " + Announced.CALL_LIMIT_MS);
    assertTrue(Announced.MATCH_SECONDS >= 1, "match clock " + Announced.MATCH_SECONDS);
    assertTrue(Announced.WARMUP_MS >= 0 && Announced.WARMUP_MS <= 5_000, "warm-up " + Announced.WARMUP_MS);
    assertEquals(Announced.CALL_LIMIT_MS * MS, CallLimit.ASSUMED_NANOS);
    assertEquals(Announced.CALL_LIMIT_MS * MS, new CallLimit().nanos());
    assertEquals(Announced.MATCH_SECONDS * 1_000_000_000L, MatchClock.MATCH_NANOS);
  }

  @Test
  void budgetsAtTheAnnouncedLimitStayInsideIt() {
    long limit = CallLimit.ASSUMED_NANOS;
    Timing t = Timing.DEFAULT;
    long[] budgets = {t.stop(0), t.stop(1000), t.blastStopNanos, t.safeFinishNanos, t.stallLimitNanos,
        QcBot.SECOND_CALL_LATE_NANOS, QcBot.PROBE_NANOS};
    for (long atPrior : budgets) {
      long b = CallLimit.scaled(atPrior, 25, limit);
      assertTrue(b > 0 && b < limit, "budget " + atPrior / MS + " ms at 100 ms is " + b / MS + " ms");
    }
    assertTrue(QcBot.PROBE_END_NANOS < limit && QcBot.PROBE_END_NANOS > limit / 2, "probe end " + QcBot.PROBE_END_NANOS);
  }

  @Test
  void theBotPlaysByTheAnnouncedRulesFromItsFirstCall() {
    QcBot bot = new QcBot("s=10,p=0");
    QuantumField f = FieldFactory.create(8, 12, bot, new RandomBot());
    int col = bot.accelerateQuark(f);
    assertTrue(col >= 0 && col < 12);
    assertEquals(EngineRules.ANNOUNCED, bot.rules());
  }

  /** At the core-2.0 values the switches are the engine's rules object itself, so nothing else in the bot changes. */
  @Test
  void theCoreValuesAreTheDefaultRules() {
    if (Announced.K == 6 && Announced.BLAST_W == 1 && Announced.BLAST_H == 1 && Announced.COOLDOWN == 10) {
      assertTrue(EngineRules.ANNOUNCED == Rules.DEFAULT);
    }
    if (Announced.CALL_LIMIT_MS == 100) {
      assertEquals(QcBot.PROBE_NANOS, QcBot.PROBE_END_NANOS);
      assertEquals(CallLimit.PRIOR_NANOS, CallLimit.ASSUMED_NANOS);
    }
  }
}
