package ch.qc.bot;

import ch.cern.quantumconnect.core.QuantumField;
import ch.qc.bot.rules.Rules;

/**
 * The one place that learns the match rules from the engine (docs/dynamic-rules.md). The core-2.0 engine has no way to
 * ask: its line length is a package-private constant of QuantumField, the blast is hard-coded in
 * destroySurroundingDiscs, and Experiment.MOVES_BETWEEN_SPECIAL_MOVES is a compile-time constant that javac copies into
 * our class, so reading it would always say 10. Until the organisers publish the new core jar this returns the
 * core-2.0 rules; once it has an accessor (for example field.getConnectedQuarksRequiredToWin()), wire it in here and
 * nowhere else: QcBot calls this at every call and rebuilds its model when the answer changes.
 */
final class EngineRules {
  private EngineRules() {}

  static Rules read(QuantumField field) {
    return Rules.DEFAULT;
  }
}
