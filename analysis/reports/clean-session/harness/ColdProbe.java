package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import ch.cern.quantumconnect.core.Experiment;
import ch.cern.quantumconnect.core.ExperimentStatus;
import ch.cern.quantumconnect.core.QuantumField;
import ch.cern.quantumconnect.core.ToolsFields;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Bot calls as the server runs them: one long JVM, every game a fresh class loader, so bot code starts cold each game. */
public final class ColdProbe {
  private static final class ChildFirst extends URLClassLoader {
    ChildFirst(URL jar, ClassLoader parent) {
      super(new URL[] {jar}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith("ch.qc.")) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        return c != null ? c : findClass(name);
      }
    }
  }

  private static Algorithm fresh(ClassLoader l, String botClass) throws Exception {
    return (Algorithm) l.loadClass(botClass).getDeclaredConstructor().newInstance();
  }

  public static void main(String[] a) throws Exception {
    Path jar = Path.of(a[0]);
    int rows = Integer.parseInt(a[1]);
    int cols = Integer.parseInt(a[2]);
    int games = Integer.parseInt(a[3]);
    int maxPlies = Integer.parseInt(a[4]);
    long warmMs = Long.parseLong(a[5]);
    String label = a[6];
    String botClass;
    try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
      botClass = jf.getManifest().getMainAttributes().getValue("Bot-Class");
    }
    try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(a[7]), java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND))) {
      ClassLoader keepA = new ChildFirst(jar.toUri().toURL(), ColdProbe.class.getClassLoader());
      ClassLoader keepB = new ChildFirst(jar.toUri().toURL(), ColdProbe.class.getClassLoader());
      boolean reuse = label.startsWith("hot");
      for (int g = 0; g < games; g++) {
        ClassLoader la = reuse ? keepA : new ChildFirst(jar.toUri().toURL(), ColdProbe.class.getClassLoader());
        ClassLoader lb = reuse ? keepB : new ChildFirst(jar.toUri().toURL(), ColdProbe.class.getClassLoader());
        if (warmMs > 0) {
          warm(la, botClass, rows, cols, warmMs);
          warm(lb, botClass, rows, cols, warmMs);
        }
        long t0 = System.nanoTime();
        Algorithm first = fresh(la, botClass);
        long ctor = (System.nanoTime() - t0) / 1000;
        Algorithm second = fresh(lb, botClass);
        QuantumField field = ToolsFields.create(rows, cols, first, second);
        int[] counter = new int[3];
        int[] own = new int[3];
        int toMove = 1;
        int iter = 0;
        while (ToolsFields.status(field) == ExperimentStatus.INCOMPLETE && iter < maxPlies) {
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
            }, 100);
          } catch (RuntimeException e) {
            to = true;
          }
          long outer = System.nanoTime() - s0;
          boolean sp = false;
          try {
            sp = Experiment.executeWithTimeout(() -> bot.useSpecialMove(field), 100);
          } catch (RuntimeException e) {
            sp = false;
          }
          long nodes = -1;
          int depth = -1;
          try {
            Object r = bot.getClass().getMethod("lastResult").invoke(bot);
            if (r != null) {
              nodes = (Long) r.getClass().getMethod("nodes").invoke(r);
              depth = (Integer) r.getClass().getMethod("depth").invoke(r);
            }
          } catch (ReflectiveOperationException e) {
            nodes = -2;
          }
          own[toMove]++;
          w.printf(Locale.ROOT, "%s,%dx%d,%d,%d,%d,%d,%.2f,%.2f,%b,%d,%d,%d%n", label, rows, cols, g, toMove, own[toMove], iter,
              in.get() / 1e6, outer / 1e6, to, nodes, depth, ctor);
          boolean honoured = sp && counter[toMove] >= 10 && col >= 0;
          ToolsFields.play(field, col, honoured);
          counter[toMove] = honoured ? 1 : counter[toMove] + 1;
          toMove = 3 - toMove;
          iter++;
        }
      }
    }
  }

  /** What a warm-up in the constructor could do: a throwaway game with the same classes for a fixed time. */
  private static void warm(ClassLoader l, String botClass, int rows, int cols, long ms) throws Exception {
    long end = System.nanoTime() + ms * 1_000_000L;
    while (System.nanoTime() < end) {
      Algorithm x = fresh(l, botClass);
      Algorithm y = fresh(l, botClass);
      QuantumField f = ToolsFields.create(rows, cols, x, y);
      int toMove = 1;
      while (ToolsFields.status(f) == ExperimentStatus.INCOMPLETE && System.nanoTime() < end) {
        Algorithm b = toMove == 1 ? x : y;
        ToolsFields.play(f, b.accelerateQuark(f), false);
        toMove = 3 - toMove;
      }
    }
  }

  private ColdProbe() {}
}
