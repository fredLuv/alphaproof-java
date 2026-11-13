package dev.oopproof;

import dev.oopproof.core.ProofCertificate;
import dev.oopproof.core.ProofKernel;
import dev.oopproof.core.ProofSearchEngine;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.WorkspaceAnalyzer;
import dev.oopproof.spec.BuiltinSpecs;
import dev.oopproof.spec.FormalSpec;
import dev.oopproof.spec.GenericJsonSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry point for OOPProof-Java: formal verification & proof certification
 * for Java OOP design and algorithm implementations.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String cmd = args[0];
        switch (cmd) {
            case "specs" -> runListSpecs();
            case "prove" -> {
                int code = runProve(args);
                System.exit(code);
            }
            case "verify" -> {
                int code = runVerify(args);
                System.exit(code);
            }
            case "demo" -> {
                int code = runDemo(args);
                System.exit(code);
            }
            default -> {
                System.err.println("Unknown command: " + cmd);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void printUsage() {
        System.out.println("""
                OOPProof-Java - Formal Proof Engine for Java OOP Designs
                Usage:
                  specs
                  prove  --spec <spec-id|path.spec.json> --target <dir> [--cert <out.proof.json>] [--trace <out.proof.lean>]
                  verify --cert <file.proof.json> [--target <dir>]
                  demo   [--root <OOPProof-Java-dir>]
                """);
    }

    private static void runListSpecs() {
        System.out.println("Built-in formal specifications:");
        for (FormalSpec s : BuiltinSpecs.all()) {
            System.out.printf("  - %-26s : %s%n", s.id(), s.title());
        }
        System.out.println("Plus arbitrary declarative specs via --spec <path/to/problem.spec.json>");
    }

    private static int runProve(String[] args) throws IOException {
        String specArg = flag(args, "--spec");
        String targetArg = flag(args, "--target");
        String certOut = flag(args, "--cert");
        String traceOut = flag(args, "--trace");

        if (specArg == null || targetArg == null) {
            System.err.println("Missing required --spec or --target");
            return 2;
        }

        FormalSpec spec = resolveSpec(specArg);
        Path targetDir = Path.of(targetArg).toAbsolutePath().normalize();

        WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer();
        ProofSearchEngine engine = new ProofSearchEngine();
        ProofKernel kernel = new ProofKernel();

        try (WorkspaceAnalyzer.CodebaseModel model = analyzer.analyze(targetDir)) {
            ProofCertificate cert = engine.prove(spec, model);
            System.out.println(cert.toLeanStyleTrace());
            System.out.println("Search telemetry: " + cert.searchTelemetry());

            if (certOut != null) {
                Path cp = Path.of(certOut);
                if (cp.getParent() != null) Files.createDirectories(cp.getParent());
                Files.writeString(cp, cert.toJson(), StandardCharsets.UTF_8);
                System.out.println("Wrote ProofCertificate -> " + cp);
            }
            if (traceOut != null) {
                Path tp = Path.of(traceOut);
                if (tp.getParent() != null) Files.createDirectories(tp.getParent());
                Files.writeString(tp, cert.toLeanStyleTrace(), StandardCharsets.UTF_8);
                System.out.println("Wrote Lean-style proof trace -> " + tp);
            }

            if (cert.rootStep().status() == ProofStatus.QED) {
                ProofKernel.KernelVerdict verdict = kernel.verify(cert, targetDir);
                System.out.println("ProofKernel: " + verdict.summary());
                if (!verdict.accepted()) {
                    verdict.diagnostics().forEach(d -> System.err.println("  [KERNEL] " + d));
                    return 1;
                }
                return 0;
            } else {
                System.out.println("ProofKernel: skipped (theorem not QED; status=" + cert.rootStep().status() + ")");
                return 1;
            }
        }
    }

    private static int runVerify(String[] args) throws IOException {
        String certArg = flag(args, "--cert");
        String targetArg = flag(args, "--target");
        if (certArg == null) {
            System.err.println("Missing --cert");
            return 2;
        }
        String json = Files.readString(Path.of(certArg), StandardCharsets.UTF_8);
        ProofCertificate cert = ProofCertificate.fromJson(json);
        Path targetDir = targetArg != null ? Path.of(targetArg).toAbsolutePath().normalize() : null;
        ProofKernel kernel = new ProofKernel();
        ProofKernel.KernelVerdict verdict = kernel.verify(cert, targetDir);
        System.out.println(verdict.summary());
        for (String d : verdict.diagnostics()) {
            System.err.println("  - " + d);
        }
        return verdict.accepted() ? 0 : 1;
    }

    private static int runDemo(String[] args) throws IOException {
        String rootArg = flag(args, "--root");
        Path root = rootArg != null ? Path.of(rootArg).toAbsolutePath().normalize() : Path.of("").toAbsolutePath().normalize();

        record Case(String label, String specRef, Path target, boolean expectQed) {}
        List<Case> cases = List.of(
                new Case("Classic OOP 1 (Visitor)  - Clean Solution", "oop-visitor-shapes",
                        root.resolve("solutions/visitor-shapes"), true),
                new Case("Classic OOP 2 (PSR)      - Clean Solution", "oop-paper-scissors-rock",
                        root.resolve("solutions/paper-scissors-rock"), true),
                new Case("Generic Spec (LRU Cache) - Declarative Spec", root.resolve("specs/lru-cache.spec.json").toString(),
                        root.resolve("solutions/lru-cache"), true),
                new Case("Counterexample 1         - instanceof Cheat", "oop-visitor-shapes",
                        root.resolve("solutions/counterexamples/test1-instanceof-cheat"), false),
                new Case("Counterexample 2         - Broken Triangle Guard", "oop-visitor-shapes",
                        root.resolve("solutions/counterexamples/test1-broken-triangle"), false),
                new Case("Counterexample 3         - Hardcoded Enum Switch", "oop-paper-scissors-rock",
                        root.resolve("solutions/counterexamples/test2-hardcoded-switch"), false)
        );

        WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer();
        ProofSearchEngine engine = new ProofSearchEngine();
        ProofKernel kernel = new ProofKernel();
        Path certDir = root.resolve("build/certificates");
        Files.createDirectories(certDir);

        int mismatches = 0;
        System.out.println("==========================================================================");
        System.out.println("OOPProof-Java Benchmark Suite");
        System.out.println("==========================================================================");

        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            System.out.println("\n[" + (i + 1) + "/" + cases.size() + "] " + c.label());
            System.out.println("Target: " + root.relativize(c.target()));
            FormalSpec spec = resolveSpec(c.specRef());
            try (WorkspaceAnalyzer.CodebaseModel model = analyzer.analyze(c.target())) {
                ProofCertificate cert = engine.prove(spec, model);
                ProofStatus status = cert.rootStep().status();
                boolean isQed = status == ProofStatus.QED;

                String safeName = c.label().toLowerCase().replaceAll("[^a-z0-9]+", "-");
                Path certFile = certDir.resolve(safeName + ".proof.json");
                Files.writeString(certFile, cert.toJson(), StandardCharsets.UTF_8);

                System.out.println(cert.toLeanStyleTrace());
                if (isQed) {
                    ProofKernel.KernelVerdict verdict = kernel.verify(cert, c.target());
                    System.out.println("Kernel: " + verdict.summary());
                    if (!verdict.accepted()) {
                        mismatches++;
                    }
                }
                if (isQed != c.expectQed()) {
                    System.err.println("UNEXPECTED RESULT: expected expectQed=" + c.expectQed() + " but got " + status);
                    mismatches++;
                } else {
                    System.out.println("Benchmark check: MATCHED EXPECTED (" + status + ")");
                }
            }
        }

        System.out.println("\n==========================================================================");
        System.out.println("Demo completed. Mismatches: " + mismatches);
        System.out.println("Certificates saved in: " + certDir);
        System.out.println("==========================================================================");
        return mismatches == 0 ? 0 : 1;
    }

    private static FormalSpec resolveSpec(String specArg) throws IOException {
        FormalSpec builtin = BuiltinSpecs.byId(specArg);
        if (builtin != null) {
            return builtin;
        }
        Path p = Path.of(specArg);
        if (Files.isRegularFile(p)) {
            return GenericJsonSpec.load(p);
        }
        throw new IllegalArgumentException("Unknown spec ID or missing file: " + specArg);
    }

    private static String flag(String[] args, String name) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return null;
    }
}
