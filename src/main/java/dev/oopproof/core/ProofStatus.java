package dev.oopproof.core;

/**
 * Formal verification outcome for a proof goal, lemma, or theorem.
 */
public enum ProofStatus {
    /** Goal and all child obligations discharged with valid evidence. */
    QED,
    /** Concrete counterexample or structural violation falsifies the goal. */
    REFUTED,
    /** Search budget exhausted or dependency unresolved without refutation. */
    INCOMPLETE
}
