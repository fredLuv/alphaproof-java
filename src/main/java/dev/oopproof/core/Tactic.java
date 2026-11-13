package dev.oopproof.core;

/**
 * A formal proof tactic that attempts to discharge (or refute) a proof goal
 * over a compiled Java CodebaseModel and a candidate BindingEnv.
 */
@FunctionalInterface
public interface Tactic {

    /**
     * Evaluates this tactic against the candidate codebase and concept bindings.
     * Must return a ProofStep with status QED (proved with evidence/witnesses),
     * REFUTED (falsified with concrete counterexample), or INCOMPLETE.
     */
    ProofStep apply(String stepId, WorkspaceAnalyzer.CodebaseModel model, BindingEnv env);
}
