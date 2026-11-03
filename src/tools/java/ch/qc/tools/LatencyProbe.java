package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.QuantumField;
import ch.cern.quantumconnect.core.ToolsFields;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Latency as the engine sees it (design 5.5): games between two bots driven by the engine's own public timer,
 * {@code Experiment.executeWithTimeout(Supplier, 100)}, which creates a thread per call like a real game. For
 * every accelerateQuark call it records the time inside the bot, the time around the official call, and their
 * difference (thread creation, scheduling and hand-over), so the bot's budget reserve can be checked.
 *
 * <pre>
 * --a SPEC --b SPEC --calls N --openings FILE --load THREADS
 * </pre>
 * {@code --load} starts busy threads to emulate other games on the machine. Run with -Pjvm="-XX:+UseSerialGC" etc.
 */
public final class LatencyProbe {
  public static void main(String[] args) throws Exception {
    String a = "ch.qc.bot.QcBot";
    String b = "ch.qc.bot.QcBot";
    int calls = 20_000;
    String openings = "suites/openings-8x12.txt";
    int load = 0;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--a" -> a = args[++i];
        case "--b" -> b = args[++i];
        case "--calls" -> calls = Integer.parseInt(args[++i]);
        case "--openings" -> openings = args[++i];
        case "--load" -> load = Integer.parseInt(args[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + args[i]);
      }
    }
    List<int[]> pool = MatchMain.readOpenings(java.nio.file.Path.of(openings));
    for (int i = 0; i < load; i++) {
      Thread t = new Thread(() -> {
        long x = 0;
        while (!Thread.currentThread().isInterrupted()) {
          x += System.nanoTime() & 1;
        }
      });
      t.setDaemon(true);
      t.start();
    }
    LatencyHistogram inside = new LatencyHistogram();
    LatencyHistogram outside = new LatencyHistogram();
    LatencyHistogram overhead = new LatencyHistogram();
    LatencyHistogram special = new LatencyHistogram();
    int timeouts = 0;
    int measured = 0;
    int games = 0;
    while (measured < calls) {
      Algorithm first = BotLoader.create(a);
      Algorithm second = BotLoader.create(b);
      QuantumField field = ToolsFields.create(8, 12, first, second);
      int[] line = pool.get(games % pool.size());
      int[] counter = new int[3];
      int toMove = 1;
      int iter = 0;
      for (int c : line) {
        // scripted plies: the bots still see their turns, as the match harness arranges
        Algorithm bot = toMove == 1 ? first : second;
        if (bot instanceof ch.qc.bot.QcBot q) {
          q.observeScriptedTurn(field);
        }
        ToolsFields.play(field, c, false);
        counter[toMove]++;
        toMove = 3 - toMove;
        iter++;
      }
      while (ToolsFields.status(field) == ExperimentStatus.INCOMPLETE && iter < 1000 && measured < calls) {
        Algorithm bot = toMove == 1 ? first : second;
        AtomicLong in = new AtomicLong();
        long t0 = System.nanoTime();
        int col = -1;
        try {
          col = Experiment.executeWithTimeout(() -> {
            long s = System.nanoTime();
            int c = bot.accelerateQuark(field);
            in.set(System.nanoTime() - s);
            return c;
          }, 100);
        } catch (RuntimeException e) {
          timeouts++;
        }
        long outer = System.nanoTime() - t0;
        boolean sp = false;
        long s0 = System.nanoTime();
        try {
          sp = Experiment.executeWithTimeout(() -> bot.useSpecialMove(field), 100);
        } catch (RuntimeException e) {
          sp = false;
        }
        special.record(System.nanoTime() - s0);
        inside.record(in.get());
        outside.record(outer);
        overhead.record(Math.max(0, outer - in.get()));
        measured++;
        boolean honoured = sp && counter[toMove] >= 10 && col >= 0;
        ToolsFields.play(field, col, honoured);
        counter[toMove] = honoured ? 1 : counter[toMove] + 1;
        toMove = 3 - toMove;
        iter++;
      }
      games++;
    }
    System.out.printf(java.util.Locale.ROOT, "latency probe: %s vs %s, %d calls in %d games, background load %d,"
        + " GC %s, timeouts %d%n  inside the bot     %s%n  around the call    %s%n  engine overhead    %s%n"
        + "  useSpecialMove     %s%n", a, b, measured, games, load, gcName(), timeouts, inside.summary(),
        outside.summary(), overhead.summary(), special.summary());
  }

  private static String gcName() {
    StringBuilder sb = new StringBuilder();
    for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
      sb.append(sb.isEmpty() ? "" : "/").append(gc.getName());
    }
    return sb.toString();
  }

  private LatencyProbe() {}
}
