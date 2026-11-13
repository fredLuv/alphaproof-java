package dev.oopproof.core;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles, type-checks, and parses a Java candidate directory into a typed AST
 * and isolated runtime classloader using the official JDK Compiler API.
 */
public final class WorkspaceAnalyzer {

    private static final Set<String> FORBIDDEN_BUILD_EXTENSIONS = Set.of(
            ".class", ".jar", ".war", ".ear", ".o", ".obj", ".so", ".dylib", ".dll", ".exe"
    );

    public static final class CodebaseModel implements Closeable {
        private final Path rootDir;
        private final List<Path> javaFiles;
        private final List<String> forbiddenArtifacts;
        private final Map<String, String> sourceSha256Manifest;
        private final boolean compiledCleanly;
        private final List<String> compilerDiagnostics;
        private final Path tempClassesDir;
        private final List<CompilationUnitTree> compilationUnits;
        private final Trees trees;
        private final Elements elements;
        private final Types types;
        private final List<TypeElement> declaredTypes;
        private URLClassLoader classLoader;

        CodebaseModel(
                Path rootDir,
                List<Path> javaFiles,
                List<String> forbiddenArtifacts,
                Map<String, String> sourceSha256Manifest,
                boolean compiledCleanly,
                List<String> compilerDiagnostics,
                Path tempClassesDir,
                List<CompilationUnitTree> compilationUnits,
                Trees trees,
                Elements elements,
                Types types,
                List<TypeElement> declaredTypes) {
            this.rootDir = rootDir;
            this.javaFiles = List.copyOf(javaFiles);
            this.forbiddenArtifacts = List.copyOf(forbiddenArtifacts);
            this.sourceSha256Manifest = Map.copyOf(sourceSha256Manifest);
            this.compiledCleanly = compiledCleanly;
            this.compilerDiagnostics = List.copyOf(compilerDiagnostics);
            this.tempClassesDir = tempClassesDir;
            this.compilationUnits = compilationUnits == null ? List.of() : List.copyOf(compilationUnits);
            this.trees = trees;
            this.elements = elements;
            this.types = types;
            this.declaredTypes = declaredTypes == null ? List.of() : List.copyOf(declaredTypes);
        }

        public Path rootDir() { return rootDir; }
        public List<Path> javaFiles() { return javaFiles; }
        public List<String> forbiddenArtifacts() { return forbiddenArtifacts; }
        public Map<String, String> sourceSha256Manifest() { return sourceSha256Manifest; }
        public boolean compiledCleanly() { return compiledCleanly; }
        public List<String> compilerDiagnostics() { return compilerDiagnostics; }
        public Path tempClassesDir() { return tempClassesDir; }
        public List<CompilationUnitTree> compilationUnits() { return compilationUnits; }
        public Trees trees() { return trees; }
        public Elements elements() { return elements; }
        public Types types() { return types; }
        public List<TypeElement> declaredTypes() { return declaredTypes; }

        public synchronized ClassLoader classLoader() {
            if (classLoader == null) {
                try {
                    URL url = tempClassesDir.toUri().toURL();
                    classLoader = new URLClassLoader(new URL[]{url}, CodebaseModel.class.getClassLoader());
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }
            return classLoader;
        }

        public Class<?> loadClass(String binaryName) throws ClassNotFoundException {
            return Class.forName(binaryName, true, classLoader());
        }

        @Override
        public void close() throws IOException {
            if (classLoader != null) {
                classLoader.close();
            }
            if (tempClassesDir != null && Files.exists(tempClassesDir)) {
                try (var walk = Files.walk(tempClassesDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        }
    }

    public CodebaseModel analyze(Path rootDir) throws IOException {
        Path normalizedRoot = rootDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalArgumentException("Target is not a directory: " + normalizedRoot);
        }

        List<Path> javaFiles = new ArrayList<>();
        List<String> forbiddenArtifacts = new ArrayList<>();
        try (var stream = Files.walk(normalizedRoot)) {
            stream.filter(Files::isRegularFile).sorted().forEach(p -> {
                String rel = normalizedRoot.relativize(p).toString();
                String lower = rel.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".java")) {
                    javaFiles.add(p);
                } else {
                    for (String ext : FORBIDDEN_BUILD_EXTENSIONS) {
                        if (lower.endsWith(ext)) {
                            forbiddenArtifacts.add(rel);
                            break;
                        }
                    }
                }
            });
        }

        Map<String, String> manifest = new LinkedHashMap<>();
        for (Path jf : javaFiles) {
            String rel = normalizedRoot.relativize(jf).toString();
            String content = Files.readString(jf, StandardCharsets.UTF_8);
            manifest.put(rel, ProofStep.sha256Hex(content));
        }

        Path tempDir = Files.createTempDirectory("oopproof-build-");
        if (javaFiles.isEmpty()) {
            return new CodebaseModel(
                    normalizedRoot, javaFiles, forbiddenArtifacts, manifest,
                    false, List.of("No .java source files found in " + normalizedRoot),
                    tempDir, List.of(), null, null, null, List.of());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK JavaCompiler unavailable (running on JRE instead of JDK?)");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> diagMessages = new ArrayList<>();
        boolean compiledOk;
        List<CompilationUnitTree> units = new ArrayList<>();
        Trees trees = null;
        Elements elements = null;
        Types types = null;
        List<TypeElement> declaredTypes = new ArrayList<>();

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> fileObjects = fm.getJavaFileObjectsFromPaths(javaFiles);
            List<String> options = List.of("-d", tempDir.toString(), "-Xlint:all", "-parameters");

            // First: run full parse + semantic analyze via JavacTask to obtain typed ASTs
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, options, null, fileObjects);
            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            for (CompilationUnitTree cu : parsed) {
                units.add(cu);
            }
            Iterable<? extends javax.lang.model.element.Element> analyzedElements = task.analyze();
            for (javax.lang.model.element.Element el : analyzedElements) {
                if (el instanceof TypeElement te) {
                    declaredTypes.add(te);
                }
            }
            trees = Trees.instance(task);
            elements = task.getElements();
            types = task.getTypes();

            boolean hasErrors = false;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                String msg = d.getKind() + ": " + d.getMessage(Locale.ROOT);
                if (d.getSource() != null) {
                    msg = d.getSource().getName() + ":" + d.getLineNumber() + ": " + msg;
                }
                diagMessages.add(msg);
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    hasErrors = true;
                }
            }

            if (!hasErrors) {
                // Emit bytecode into isolated tempDir
                DiagnosticCollector<JavaFileObject> emitDiags = new DiagnosticCollector<>();
                JavacTask emitTask = (JavacTask) compiler.getTask(null, fm, emitDiags, options, null, fileObjects);
                compiledOk = Boolean.TRUE.equals(emitTask.call());
                for (Diagnostic<? extends JavaFileObject> d : emitDiags.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        compiledOk = false;
                        diagMessages.add("EMIT_ERROR: " + d.getMessage(Locale.ROOT));
                    }
                }
            } else {
                compiledOk = false;
            }
        }

        return new CodebaseModel(
                normalizedRoot, javaFiles, forbiddenArtifacts, manifest,
                compiledOk, diagMessages, tempDir, units, trees, elements, types, declaredTypes);
    }
}
