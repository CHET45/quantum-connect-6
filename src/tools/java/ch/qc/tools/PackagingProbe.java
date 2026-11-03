package ch.qc.tools;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * Loads the uploadable jar the way an upload harness plausibly does: read Bot-Class from the manifest, load it
 * through an isolated URLClassLoader (parent = platform loader, so only the jar's own classes are visible),
 * instantiate it, read a resource through the same loader, and play one headless game against the engine's
 * RandomBot from the jar. Exits non-zero on any failure.
 */
public final class PackagingProbe {
  public static void main(String[] args) throws Exception {
    Path jar = Path.of(args[0]);
    String botClass;
    try (JarFile jf = new JarFile(jar.toFile())) {
      botClass = jf.getManifest().getMainAttributes().getValue("Bot-Class");
      if (botClass == null) {
        fail("manifest has no Bot-Class");
      }
      if (jf.stream().anyMatch(e -> e.getName().startsWith("ch/qc/tools/") || (e.getName().startsWith("ch/qc/") && e.getName().contains("Test")))) {
        fail("jar contains tool or test classes");
      }
      System.out.printf("jar %s: %d KB, Bot-Class %s%n", jar.getFileName(), jar.toFile().length() / 1024, botClass);
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      Class<?> algorithm = loader.loadClass("ch.cern.quantumconnect.core.Algorithm");
      Class<?> cls = loader.loadClass(botClass);
      if (!algorithm.isAssignableFrom(cls)) {
        fail(botClass + " does not implement Algorithm");
      }
      Constructor<?> ctor = cls.getConstructor();
      long t0 = System.nanoTime();
      Object bot = ctor.newInstance();
      long ctorMicros = (System.nanoTime() - t0) / 1000;
      try (InputStream in = loader.getResourceAsStream(botClass.replace('.', '/') + ".class")) {
        if (in == null || in.readAllBytes().length == 0) {
          fail("resource read through the jar loader failed");
        }
      }
      Object opponent = loader.loadClass("ch.cern.quantumconnect.bots.RandomBot").getConstructor().newInstance();
      Class<?> experiment = loader.loadClass("ch.cern.quantumconnect.core.Experiment");
      Object exp = experiment.getConstructor(algorithm, algorithm, boolean.class, boolean.class, int.class, int.class)
          .newInstance(bot, opponent, false, false, 8, 12);
      experiment.getMethod("runExperiment").invoke(exp);
      Method status = experiment.getMethod("getExperimentStatus");
      Method current = experiment.getMethod("getCurrentPlayer");
      Method history = experiment.getMethod("getHistory");
      System.out.printf("constructor %d us; game vs engine RandomBot: %s, current player %s, %d placements%n",
          ctorMicros, status.invoke(exp), current.invoke(exp), ((java.util.List<?>) history.invoke(exp)).size());
    }
    System.out.println("packaging probe OK");
  }

  private static void fail(String msg) {
    System.err.println("packaging probe FAILED: " + msg);
    System.exit(1);
  }

  private PackagingProbe() {}
}
