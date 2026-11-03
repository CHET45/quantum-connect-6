package ch.qc.tools;

import ch.cern.quantumconnect.core.Algorithm;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * Creates bots from a spec: a class name on the classpath, {@code qcbot:+razor,-leafVcf} for the current QcBot with
 * search switches changed ({@link SearchOptions#parse}), or {@code jar:PATH} for a frozen build (its Bot-Class
 * is loaded child-first for {@code ch.qc.*}, so two versions of QcBot can play each other while both share this
 * JVM's engine classes). {@code jar:PATH@TIMING} gives the bot a time plan through its String constructor.
 */
public final class BotLoader {
  private static final Map<String, Class<?>> JAR_CLASSES = new ConcurrentHashMap<>();

  private BotLoader() {}

  public static Algorithm create(String spec) throws ReflectiveOperationException, IOException {
    if (spec.startsWith("qcbot:")) {
      return new ch.qc.bot.QcBot(SearchOptions.parse(spec.substring(6)));
    }
    int at = spec.indexOf('@');
    if (spec.startsWith("jar:") && at > 0) {
      return (Algorithm) jarClass(spec.substring(4, at)).getConstructor(String.class).newInstance(spec.substring(at + 1));
    }
    Class<?> cls = spec.startsWith("jar:") ? jarClass(spec.substring(4)) : Class.forName(spec);
    return (Algorithm) cls.getDeclaredConstructor().newInstance();
  }

  private static Class<?> jarClass(String path) throws IOException, ClassNotFoundException {
    Class<?> cached = JAR_CLASSES.get(path);
    if (cached != null) {
      return cached;
    }
    synchronized (JAR_CLASSES) {
      cached = JAR_CLASSES.get(path);
      if (cached == null) {
        Path p = Path.of(path);
        String botClass;
        try (JarFile jf = new JarFile(p.toFile())) {
          botClass = jf.getManifest().getMainAttributes().getValue("Bot-Class");
        }
        @SuppressWarnings("resource") // lives as long as the bots it created
        ChildFirst loader = new ChildFirst(p.toUri().toURL(), BotLoader.class.getClassLoader());
        cached = loader.loadClass(botClass);
        JAR_CLASSES.put(path, cached);
      }
      return cached;
    }
  }

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
        if (c == null) {
          c = findClass(name);
        }
        if (resolve) {
          resolveClass(c);
        }
        return c;
      }
    }
  }
}
