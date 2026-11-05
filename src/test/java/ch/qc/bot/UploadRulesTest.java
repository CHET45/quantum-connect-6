package ch.qc.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ExceptionCatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Upload rules of the tournament: bots may use only basic Java (no java.io, java.net, java.nio, reflection,
 * java.lang.System, threads or runtime access) and may not throw or catch exceptions. The compiled bot classes are
 * checked against an allowlist of JDK classes, and their code must contain no athrow, no exception handlers (try,
 * catch, finally), no throws clauses and no invokedynamic (string concatenation, lambdas and records bootstrap
 * through java.lang.invoke).
 */
class UploadRulesTest {
  private static final Set<String> ALLOWED_JDK = Set.of(
      "java/lang/Object", "java/lang/Math", "java/lang/Integer", "java/lang/Long", "java/lang/Short",
      "java/lang/String", "java/lang/StringBuilder", "java/util/Arrays", "java/util/Random",
      "java/util/SplittableRandom", "java/util/Date", "java/util/List");
  private static final List<String> FORBIDDEN_TEXT = List.of("java/io", "java/nio", "java/net", "java/lang/reflect",
      "java/lang/invoke", "java/lang/runtime", "java/lang/System", "java/lang/Runtime", "java/lang/Thread",
      "java/lang/Class", "java/util/zip", "java/util/concurrent", "Exception", "Throwable", "java/lang/Error");

  @Test
  void botClassesUseOnlyAllowedJavaAndNoExceptions() throws Exception {
    Path root = Path.of("build", "classes", "java", "main", "ch", "qc", "bot");
    assertTrue(Files.isDirectory(root), "compiled bot classes not found at " + root.toAbsolutePath());
    List<Path> classes;
    try (var files = Files.walk(root)) {
      classes = files.filter(p -> p.toString().endsWith(".class")).sorted().toList();
    }
    assertTrue(classes.size() > 10, "too few bot classes: " + classes.size());
    List<String> problems = new ArrayList<>();
    for (Path p : classes) {
      ClassModel cm = ClassFile.of().parse(Files.readAllBytes(p));
      String name = cm.thisClass().asInternalName();
      for (PoolEntry e : cm.constantPool()) {
        if (e instanceof ClassEntry c) {
          String n = c.asInternalName().replaceAll("^\\[+L?|;$", "");
          if ((n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("jdk/") || n.startsWith("sun/"))
              && !ALLOWED_JDK.contains(n)) {
            problems.add(name + " references " + n);
          }
        } else if (e instanceof Utf8Entry u) {
          for (String f : FORBIDDEN_TEXT) {
            if (u.stringValue().contains(f)) {
              problems.add(name + " constant mentions " + f + ": " + abbreviate(u.stringValue()));
            }
          }
        }
      }
      if (cm.findAttribute(Attributes.record()).isPresent()) {
        problems.add(name + " is a record");
      }
      if (cm.findAttribute(Attributes.bootstrapMethods()).isPresent()) {
        problems.add(name + " has bootstrap methods");
      }
      for (MethodModel m : cm.methods()) {
        String where = name + "." + m.methodName().stringValue();
        if (m.findAttribute(Attributes.exceptions()).isPresent()) {
          problems.add(where + " declares throws");
        }
        CodeModel code = m.code().orElse(null);
        if (code == null) {
          continue;
        }
        code.forEach(el -> {
          if (el instanceof ExceptionCatch) {
            problems.add(where + " has an exception handler");
          } else if (el instanceof java.lang.classfile.Instruction ins) {
            if (ins.opcode() == Opcode.ATHROW) {
              problems.add(where + " throws");
            } else if (ins.opcode() == Opcode.INVOKEDYNAMIC) {
              problems.add(where + " uses invokedynamic");
            } else if (ins.opcode() == Opcode.MONITORENTER) {
              problems.add(where + " synchronizes");
            }
          }
        });
      }
    }
    assertEquals(List.of(), problems, "bot classes break the upload rules");
  }

  private static String abbreviate(String s) {
    return s.length() <= 80 ? s : s.substring(0, 80) + "...";
  }
}
