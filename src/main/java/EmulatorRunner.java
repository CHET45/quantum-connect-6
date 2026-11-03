import ch.cern.quantumconnect.core.Experiment;
import ch.qc.bot.QcBot;
import ch.qc.starter.SpecialMoveBot;

void main() throws InterruptedException {
  var exp = new Experiment(new QcBot(), new SpecialMoveBot());
  exp.runExperiment();
}
