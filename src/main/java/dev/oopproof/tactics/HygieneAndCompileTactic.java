package dev.oopproof.tactics;

import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves submission hygiene (no build outputs like .class or .jar delivered)
 * and clean compilation via javac.
 */
public final class HygieneAndCompileTactic implements Tactic {

    private final boolean failOnWarnings;

    public HygieneAndCompileTactic(boolean failOnWarnings) {
        this.failOnWarnings = failOnWarnings;
    }

    @Override
    public ProofStep apply(String stepId, WorkspaceAnalyzer.CodebaseModel model, BindingEnv env) {
        List<String> witnesses = new ArrayList<>();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("javaFileCount", model.javaFiles().size());
        evidence.put("forbiddenArtifactCount", model.forbiddenArtifacts().size());
        evidence.put("compiledCleanly", model.compiledCleanly());

        if (!model.forbiddenArtifacts().isEmpty()) {
            witnesses.addAll(model.forbiddenArtifacts());
            return new ProofStep(
                    stepId,
                    "HygieneAndCompile",
                    "STRATUM_1_HYGIENE",
                    "HygieneAndCompileTactic",
                    "Deliverable contains zero build outputs (.class/.jar/.o) and compiles cleanly",
                    ProofStatus.REFUTED,
                    "Found forbidden build artifacts in submission: " + model.forbiddenArtifacts(),
                    evidence,
                    witnesses,
                    List.of());
        }

        if (model.javaFiles().isEmpty() || !model.compiledCleanly()) {
            witnesses.addAll(model.compilerDiagnostics());
            return new ProofStep(
                    stepId,
                    "HygieneAndCompile",
                    "STRATUM_1_HYGIENE",
                    "HygieneAndCompileTactic",
                    "Deliverable contains zero build outputs (.class/.jar/.o) and compiles cleanly",
                    ProofStatus.REFUTED,
                    "Compilation failed with " + model.compilerDiagnostics().size() + " diagnostic(s)",
                    evidence,
                    witnesses,
                    List.of());
        }

        if (failOnWarnings && !model.compilerDiagnostics().isEmpty()) {
            witnesses.addAll(model.compilerDiagnostics());
            return new ProofStep(
                    stepId,
                    "HygieneAndCompile",
                    "STRATUM_1_HYGIENE",
                    "HygieneAndCompileTactic",
                    "Deliverable contains zero build outputs (.class/.jar/.o) and compiles cleanly",
                    ProofStatus.REFUTED,
                    "Compiler emitted -Xlint warnings: " + model.compilerDiagnostics(),
                    evidence,
                    witnesses,
                    List.of());
        }

        witnesses.add("javaFiles=" + model.javaFiles().size() + ", zero build outputs, javac exit=0");
        return new ProofStep(
                stepId,
                "HygieneAndCompile",
                "STRATUM_1_HYGIENE",
                "HygieneAndCompileTactic",
                "Deliverable contains zero build outputs (.class/.jar/.o) and compiles cleanly",
                ProofStatus.QED,
                "Verified " + model.javaFiles().size() + " Java source files with zero build artifacts and clean javac compilation.",
                evidence,
                witnesses,
                List.of());
    }
}
