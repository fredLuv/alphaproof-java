package com.oop.psr;

/**
 * Open abstraction representing a hand gesture / symbol in a two-player simultaneous game.
 * Implementing this interface allows introducing new symbols without modifying {@link GameEngine}.
 */
public interface Symbol {

    /**
     * Canonical identifier for this symbol (used as key in {@link RuleSet} dominance maps).
     */
    String id();
}
