package dev.oopproof.tactics;

import dev.oopproof.core.AstQueries;
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
 * Proves encapsulation (no exposed non-private instance fields) and modular OOP decomposition
 * (rejecting cramped single-class minimalist shortcuts).
 */
public final class EncapsulationTactic implements Tactic {

    private final int minDeclaredTypes;

    public EncapsulationTactic(int minDeclaredTypes) {
        this.minDeclaredTypes = minDeclaredTypes;
    }

    @Override
    public ProofStep apply(String stepId, WorkspaceAnalyzer.CodebaseModel model, BindingEnv env) {
        List<AstQueries.ViolationLocation> fieldViolations = AstQueries.findEncapsulationViolations(model);
        int typeCount = model.declaredTypes().size();
        int lineCount = AstQueries.countLines(model);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("declaredTypes", typeCount);
        evidence.put("minDeclaredTypes", minDeclaredTypes);
        evidence.put("totalSourceLines", lineCount);
        evidence.put("encapsulationViolations", fieldViolations.size());

        List<String> witnesses = new ArrayList<>();
        if (!fieldViolations.isEmpty()) {
            for (AstQueries.ViolationLocation v : fieldViolations) {
                witnesses.add(v.kind() + " @ " + v.location() + ": " + v.detail());
            }
            return new ProofStep(
                    stepId,
                    "EncapsulationAndModularity",
                    "STRATUM_2_STRUCTURE",
                    "EncapsulationTactic",
                    "All instance fields are private and abstractions are cleanly modularized",
                    ProofStatus.REFUTED,
                    "Found " + fieldViolations.size() + " encapsulation violation(s).",
                    evidence,
                    witnesses,
                    List.of());
        }

        if (typeCount < minDeclaredTypes) {
            witnesses.add("declaredTypes=" + typeCount + " < required " + minDeclaredTypes);
            return new ProofStep(
                    stepId,
                    "EncapsulationAndModularity",
                    "STRATUM_2_STRUCTURE",
                    "EncapsulationTactic",
                    "All instance fields are private and abstractions are cleanly modularized",
                    ProofStatus.REFUTED,
                    "Insufficient OOP modularization: found only " + typeCount + " types (expected >= " + minDeclaredTypes + ").",
                    evidence,
                    witnesses,
                    List.of());
        }

        witnesses.add("0 exposed instance fields across " + typeCount + " types (" + lineCount + " lines)");
        return new ProofStep(
                stepId,
                "EncapsulationAndModularity",
                "STRATUM_2_STRUCTURE",
                "EncapsulationTactic",
                "All instance fields are private and abstractions are cleanly modularized",
                ProofStatus.QED,
                "Verified strict private field encapsulation and clean decomposition across " + typeCount + " types.",
                evidence,
                witnesses,
                List.of());
    }
}
