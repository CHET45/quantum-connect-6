package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.QuantumField;
import ch.cern.quantumconnect.core.ToolsFields;
import java.io.PrintWriter;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;

/**
 * Imitation of the tournament server as far as its logs show it: one long JVM (Spring Boot, pid 1 in a container),
 * games strictly one after another, each bot through its own fresh class loader, two 100 ms calls per turn on a new
 * thread per call, and a 100 ms sleeping memory monitor. Every call is recorded with its own turn, so the cold-JIT
 * pattern of the server's lost turns can be compared with what a budget does here.
 *
 * <pre>
 * --a jar:PATH --b jar:PATH    the two bots (--b light:CLASS plays a class from the classpath, loaded once);
 *                              jar:PATH@TIMING passes a time plan to the bot's String constructor
 * --rows R --cols C --games N  board and number of games; sides swap every game
 * --out FILE --label L         CSV per call, appended
 * --affinity HEXMASK           restrict this process to these logical CPUs at the start
 * --load N                     busy threads (other work in the container)
 * --cfs PYTHON,SCRIPT,QUOTA,CPU imitate a container CPU limit of QUOTA ms per 100 ms (src/tools/python/cfsthrottle.py),
 *                              the throttler spinning on logical CPU number CPU
 * --reuse                      keep one class loader per jar: hot code, for comparison
 * --limit MS                   the engine's call limit (default 100)
 * --clock S                    the shared game clock, past it a game is a draw (default 30)
 * --maxPlies N                 stop a game after N plies (default 1000)
 * </pre>
 * Run with -XX:ActiveProcessorCount=K and a heap the server plausibly has. Tick lateness of the monitor is written
 * as lines "tick,LABEL,bucket,count" at the end, the same sensor the server's logs provide.
 */
public final class ServerSim {
  private static final class ChildFirst extends URLClassLoader {
    ChildFirst(URL jar, ClassLoader parent) {
      super(new URL[] {jar}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith("ch.qc.bot")) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        return c != null ? c : findClass(name);
      }
    }
  }

  private static final class BotSpec {
    final String spec;
    final URL jar;
    final String cls;
    final String timing;
    ClassLoader kept;

    BotSpec(String spec) throws Exception {
      this.spec = spec;
      int at = spec.indexOf('@');
      timing = at > 0 ? spec.substring(at + 1) : null;
      if (spec.startsWith("jar:")) {
        Path p = Path.of(spec.substring(4, at > 0 ? at : spec.length()));
        jar = p.toUri().toURL();
        try (JarFile jf = new JarFile(p.toFile())) {
          cls = jf.getManifest().getMainAttributes().getValue("Bot-Class");
        }
      } else {
        jar = null;
        cls = spec.substring(spec.indexOf(':') + 1);
      }
    }

    Algorithm create(boolean reuse) throws Exception {
      ClassLoader l;
      if (jar == null) {
        l = ServerSim.class.getClassLoader();
      } else if (reuse) {
        if (kept == null) {
          kept = new ChildFirst(jar, ServerSim.class.getClassLoader());
        }
        l = kept;
      } else {
        l = new ChildFirst(jar, ServerSim.class.getClassLoader());
      }
      Class<?> c = l.loadClass(cls);
      return (Algorithm) (timing == null ? c.getDeclaredConstructor().newInstance()
          : c.getConstructor(String.class).newInstance(timing));
    }
  }

  private static volatile long sink;

  public static void main(String[] a) throws Exception {
    String sa = null;
    String sb = null;
    int rows = 15;
    int cols = 11;
    int games = 10;
    String out = null;
    String label = "run";
    String affinity = null;
    int load = 0;
    boolean reuse = false;
    int maxPlies = 1000;
    String cfs = null;
    long limit = 100;
    long clockSeconds = 30;
    for (int i = 0; i < a.length; i++) {
      switch (a[i]) {
        case "--a" -> sa = a[++i];
        case "--b" -> sb = a[++i];
        case "--rows" -> rows = Integer.parseInt(a[++i]);
        case "--cols" -> cols = Integer.parseInt(a[++i]);
        case "--games" -> games = Integer.parseInt(a[++i]);
        case "--out" -> out = a[++i];
        case "--label" -> label = a[++i];
        case "--affinity" -> affinity = a[++i];
        case "--load" -> load = Integer.parseInt(a[++i]);
        case "--reuse" -> reuse = true;
        case "--maxPlies" -> maxPlies = Integer.parseInt(a[++i]);
        case "--cfs" -> cfs = a[++i];
        case "--limit" -> limit = Long.parseLong(a[++i]);
        case "--clock" -> clockSeconds = Long.parseLong(a[++i]);
        default -> throw new IllegalArgumentException("unknown argument " + a[i]);
      }
    }
    if (affinity != null) {
      long mask = Long.parseLong(affinity, 16);
      long pid = ProcessHandle.current().pid();
      new ProcessBuilder("powershell", "-NoProfile", "-Command",
          "(Get-Process -Id " + pid + ").ProcessorAffinity=" + mask).inheritIO().start().waitFor();
    }
    if (cfs != null) {
      String[] c = cfs.split(",");
      new ProcessBuilder(c[0], "-I", c[1], Long.toString(ProcessHandle.current().pid()), c[2], "100", c[3])
          .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new java.io.File(out + ".cfs"))).start();
    }
    for (int i = 0; i < load; i++) {
      Thread t = new Thread(() -> {
        long x = 0;
        while (true) {
          x += System.nanoTime() & 1;
          sink = x;
        }
      });
      t.setDaemon(true);
      t.start();
    }
    long[] lateness = new long[64];
    Thread monitor = new Thread(() -> {
      long prev = System.nanoTime();
      while (true) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          return;
        }
        long now = System.nanoTime();
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        sink = used;
        long late = (now - prev) / 1_000_000L - 100;
        synchronized (lateness) {
          lateness[(int) Math.max(0, Math.min(63, late / 5))]++;
        }
        prev = now;
      }
    });
    monitor.setDaemon(true);
    monitor.start();

    BotSpec ba = new BotSpec(sa);
    BotSpec bb = new BotSpec(sb);
    CompilationMXBean comp = ManagementFactory.getCompilationMXBean();
    try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(out), StandardOpenOption.CREATE,
        StandardOpenOption.APPEND))) {
      for (int g = 0; g < games; g++) {
        boolean aFirst = g % 2 == 0;
        long c0 = System.nanoTime();
        Algorithm x = ba.create(reuse);
        long ctorA = System.nanoTime() - c0;
        c0 = System.nanoTime();
        Algorithm y = bb.create(reuse);
        long ctorB = System.nanoTime() - c0;
        Algorithm first = aFirst ? x : y;
        Algorithm second = aFirst ? y : x;
        Method mf = lastResult(first);
        Method ms = lastResult(second);
        QuantumField field = ToolsFields.create(rows, cols, first, second);
        int[] counter = new int[3];
        int[] own = new int[3];
        int toMove = 1;
        int iter = 0;
        long compStart = comp.getTotalCompilationTime();
        long gameStart = System.nanoTime();
        boolean onTime = false;
        int crashes = 0;
        while (ToolsFields.status(field) == ExperimentStatus.INCOMPLETE && iter < maxPlies) {
          if (System.nanoTime() - gameStart > clockSeconds * 1_000_000_000L) {
            onTime = true;
            break;
          }
          Algorithm bot = toMove == 1 ? first : second;
          AtomicLong in = new AtomicLong();
          long s0 = System.nanoTime();
          int col = -1;
          boolean to = false;
          try {
            col = Experiment.executeWithTimeout(() -> {
              long s = System.nanoTime();
              int c = bot.accelerateQuark(field);
              in.set(System.nanoTime() - s);
              return c;
            }, limit);
          } catch (RuntimeException e) {
            to = true;
          }
          long outer = System.nanoTime() - s0;
          AtomicLong spIn = new AtomicLong();
          boolean sp = false;
          boolean spTo = false;
          long s1 = System.nanoTime();
          try {
            sp = Experiment.executeWithTimeout(() -> {
              long s = System.nanoTime();
              boolean b = bot.useSpecialMove(field);
              spIn.set(System.nanoTime() - s);
              return b;
            }, limit);
          } catch (RuntimeException e) {
            spTo = true;
          }
          long spOuter = System.nanoTime() - s1;
          long nodes = -1;
          int depth = -1;
          long ponderNodes = -1;
          Method m = toMove == 1 ? mf : ms;
          if (m != null) {
            Object r = m.invoke(bot);
            if (r != null) {
              nodes = (Long) r.getClass().getMethod("nodes").invoke(r);
              depth = (Integer) r.getClass().getMethod("depth").invoke(r);
            }
            Method mp = lastPonder(bot);
            if (mp != null) {
              Object rp = mp.invoke(bot);
              if (rp != null) {
                ponderNodes = (Long) rp.getClass().getMethod("nodes").invoke(rp);
              }
            }
          }
          long gcCount = 0;
          long gcMs = 0;
          for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, b.getCollectionCount());
            gcMs += Math.max(0, b.getCollectionTime());
          }
          boolean isA = (toMove == 1) == aFirst;
          w.printf(Locale.ROOT, "%s,%dx%d,%d,%s,%d,%d,%.2f,%.2f,%d,%.2f,%.2f,%d,%d,%d,%d,%d,%d,%d,%d%n", label, rows, cols,
              g, isA ? "A" : "B", own[toMove], iter, in.get() / 1e6, outer / 1e6, to ? 1 : 0, spIn.get() / 1e6,
              spOuter / 1e6, spTo ? 1 : 0, nodes, depth, gcCount, gcMs, comp.getTotalCompilationTime() - compStart,
              (isA ? ctorA : ctorB) / 1_000_000L, ponderNodes);
          own[toMove]++;
          // the real engine crashes here: a special it would honour on a column of -1
          crashes += sp && counter[toMove] >= 10 && col < 0 ? 1 : 0;
          boolean honoured = sp && counter[toMove] >= 10 && col >= 0;
          ToolsFields.play(field, col, honoured);
          counter[toMove] = honoured ? 1 : counter[toMove] + 1;
          toMove = 3 - toMove;
          iter++;
        }
        ExperimentStatus st = ToolsFields.status(field);
        // after a win the engine leaves the winner's turn passed: the side now to move lost
        String winner = st == ExperimentStatus.COLLAPSED && !onTime ? ((toMove == 1) == aFirst ? "B" : "A") : "-";
        w.printf(Locale.ROOT, "game,%s,%dx%d,%d,%s,%s,%d,%s,%.1f,%d,%d,%d%n", label, rows, cols, g, aFirst ? "A" : "B", winner, iter,
            onTime ? "TIME" : st.toString(), (System.nanoTime() - gameStart) / 1e9, crashes,
            limitOf(aFirst ? first : second), limitOf(aFirst ? second : first));
        w.flush();
      }
      synchronized (lateness) {
        for (int i = 0; i < lateness.length; i++) {
          if (lateness[i] > 0) {
            w.printf(Locale.ROOT, "tick,%s,%d,%d%n", label, i * 5, lateness[i]);
          }
        }
      }
    }
    System.exit(0);
  }

  private static Method lastResult(Algorithm bot) {
    try {
      return bot.getClass().getMethod("lastResult");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  /** The limit in ms a bot believes in, -1 if it has no such idea. */
  private static long limitOf(Algorithm bot) throws ReflectiveOperationException {
    try {
      return (Long) bot.getClass().getMethod("callLimitNanos").invoke(bot) / 1_000_000L;
    } catch (NoSuchMethodException e) {
      return -1;
    }
  }

  private static Method lastPonder(Algorithm bot) {
    try {
      return bot.getClass().getMethod("lastPonder");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private ServerSim() {}
}
