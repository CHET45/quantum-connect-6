import ch.cern.quantumconnect.core.Experiment;
import ch.qc.bot.DarkPrince;
import ch.qc.starter.SpecialMoveBot;

void main() throws InterruptedException {
  var exp = new Experiment(new DarkPrince(), new SpecialMoveBot());
  exp.runExperiment();
}
