package ch.qc.bot;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that play the bot inside the core-2.0 engine (six, 3x3, cooldown 10, 100 ms, 30 s) or check numbers written for
 * those values hold only while {@link Announced} has them: a bot set for other rules disagrees with that engine (it
 * asks for a blast the engine does not grant yet, it plans 200 ms on a 100 ms engine). Under other announced values
 * they are skipped, and night/tools/final-build.ps1 checks the bot under the announced rules with a FastMatch instead.
 */
final class CoreEngine {
  private CoreEngine() {}

  static boolean coreRules() {
    return EngineRules.ANNOUNCED.isDefault();
  }

  static boolean coreTimes() {
    return Announced.CALL_LIMIT_MS == 100 && Announced.MATCH_SECONDS == 30;
  }

  /** Skips the test unless the announced rules are the core-2.0 rules. */
  static void assumeCoreRules() {
    assumeTrue(coreRules(), "written for the core-2.0 rules (k 6, 3x3, cooldown 10)");
  }

  /** Skips the test unless the announced times are the core-2.0 times. */
  static void assumeCoreTimes() {
    assumeTrue(coreTimes(), "written for 100 ms per call and 30 s per game");
  }

  static void assumeCore() {
    assumeCoreRules();
    assumeCoreTimes();
  }
}
