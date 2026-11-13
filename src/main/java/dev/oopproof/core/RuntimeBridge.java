package dev.oopproof.core;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Isolated reflection and synthetic-extension compilation bridge for behavioral
 * and Open-Closed Principle verification.
 */
public final class RuntimeBridge {

    private RuntimeBridge() {}

    public record ExtensionCompileResult(
            boolean success,
            List<String> diagnostics,
            Path extensionClassesDir) {}

    public static Object instantiateNumeric(Class<?> cls, double... args) throws Throwable {
        // 1. Try matching public constructors
        for (Constructor<?> ctor : cls.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == args.length && allNumeric(params)) {
                Object[] converted = convertNumericArgs(params, args);
                try {
                    return ctor.newInstance(converted);
                } catch (InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }
        }
        // 2. Try static factory methods (of, create, from)
        for (Method m : cls.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !cls.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length == args.length && allNumeric(params)) {
                Object[] converted = convertNumericArgs(params, args);
                try {
                    return m.invoke(null, converted);
                } catch (InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }
        }
        throw new NoSuchMethodException(
                "No public constructor or static factory with " + args.length + " numeric params on " + cls.getName());
    }

    public static Object instantiateZeroArg(Class<?> cls) throws Throwable {
        try {
            Constructor<?> ctor = cls.getConstructor();
            return ctor.newInstance();
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    public static Object invokeMethod(Object target, String name, Object... args) throws Throwable {
        Class<?> cls = target instanceof Class<?> c ? c : target.getClass();
        Object receiver = target instanceof Class<?> ? null : target;
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                continue;
            }
            if (argsCompatible(m.getParameterTypes(), args)) {
                try {
                    return m.invoke(receiver, args);
                } catch (InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }
        }
        throw new NoSuchMethodException("No compatible method '" + name + "' with " + args.length + " args on " + cls.getName());
    }

    public static Object invokeAccept(Object shape, Object visitor, String acceptName) throws Throwable {
        for (Method m : shape.getClass().getMethods()) {
            if (m.getName().equals(acceptName) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isInstance(visitor)) {
                try {
                    return m.invoke(shape, visitor);
                } catch (InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }
        }
        throw new NoSuchMethodException("No accept method '" + acceptName + "' accepting " + visitor.getClass().getName());
    }

    public static Object findSymbolConstant(Class<?> symbolClass, String symbolName) {
        String upper = symbolName.toUpperCase(Locale.ROOT);
        if (symbolClass.isEnum()) {
            for (Object c : symbolClass.getEnumConstants()) {
                if (((Enum<?>) c).name().equalsIgnoreCase(symbolName)) {
                    return c;
                }
            }
        }
        for (var f : symbolClass.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getName().equalsIgnoreCase(upper)) {
                try {
                    return f.get(null);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * Synthesizes a fresh Java source file in a temporary directory and compiles it against
     * the candidate's compiled classes (without modifying the candidate source tree).
     */
    public static ExtensionCompileResult compileSyntheticExtension(
            WorkspaceAnalyzer.CodebaseModel model,
            String className,
            String javaSource) throws IOException {
        Path extDir = Files.createTempDirectory("oopproof-ext-");
        Path srcFile = extDir.resolve(className + ".java");
        Files.writeString(srcFile, javaSource, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        List<String> messages = new ArrayList<>();
        boolean ok;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> fos = fm.getJavaFileObjectsFromPaths(List.of(srcFile));
            List<String> options = List.of(
                    "-cp", model.tempClassesDir().toString(),
                    "-d", extDir.toString());
            ok = Boolean.TRUE.equals(compiler.getTask(null, fm, diags, options, null, fos).call());
            for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    messages.add(d.getKind() + " line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT));
                }
            }
        }
        return new ExtensionCompileResult(ok, messages, extDir);
    }

    public static URLClassLoader createExtensionClassLoader(WorkspaceAnalyzer.CodebaseModel model, Path extDir)
            throws IOException {
        return new URLClassLoader(
                new URL[]{extDir.toUri().toURL(), model.tempClassesDir().toUri().toURL()},
                RuntimeBridge.class.getClassLoader());
    }

    public static void deleteTempDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static boolean allNumeric(Class<?>[] params) {
        if (params.length == 0) {
            return false;
        }
        for (Class<?> p : params) {
            if (!(p == double.class || p == Double.class || p == float.class || p == Float.class
                    || p == int.class || p == Integer.class || p == long.class || p == Long.class)) {
                return false;
            }
        }
        return true;
    }

    private static Object[] convertNumericArgs(Class<?>[] params, double[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> p = params[i];
            if (p == double.class || p == Double.class) {
                out[i] = args[i];
            } else if (p == float.class || p == Float.class) {
                out[i] = (float) args[i];
            } else if (p == int.class || p == Integer.class) {
                out[i] = (int) Math.round(args[i]);
            } else if (p == long.class || p == Long.class) {
                out[i] = Math.round(args[i]);
            }
        }
        return out;
    }

    private static boolean argsCompatible(Class<?>[] paramTypes, Object[] args) {
        for (int i = 0; i < paramTypes.length; i++) {
            Object arg = args[i];
            Class<?> p = paramTypes[i];
            if (arg == null) {
                if (p.isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (p.isPrimitive()) {
                p = boxedType(p);
            }
            if (!p.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxedType(Class<?> p) {
        if (p == int.class) return Integer.class;
        if (p == long.class) return Long.class;
        if (p == double.class) return Double.class;
        if (p == float.class) return Float.class;
        if (p == boolean.class) return Boolean.class;
        if (p == byte.class) return Byte.class;
        if (p == short.class) return Short.class;
        if (p == char.class) return Character.class;
        return p;
    }
}
