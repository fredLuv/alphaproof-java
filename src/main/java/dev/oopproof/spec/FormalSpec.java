package dev.oopproof.spec;

import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import java.util.List;

/**
 * Formal specification for a Java OOP or systems design problem.
 * Decomposes a specification into:
 * 1. Candidate concept-binding hypothesis generation (for ProofSearchEngine).
 * 2. Ordered formal proof obligations across Hygiene, Structure, Behavior, and Extensibility strata.
 */
public interface FormalSpec {

    record ScoredBinding(BindingEnv env, double priorScore, String rationale) {}

    record Obligation(
            String id,
            String lemmaName,
            String stratum,
            String proposition,
            Tactic tactic,
            boolean stopOnRefutation) {}

    String id();

    String title();

    String description();

    List<ScoredBinding> proposeBindings(WorkspaceAnalyzer.CodebaseModel model);

    List<Obligation> obligations();
}
