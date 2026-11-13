package com.oop.psr;

import java.util.Objects;

/**
 * Immutable record capturing a single evaluated round and its rule explanation.
 */
public record RoundResult(
        Symbol playerOneMove,
        Symbol playerTwoMove,
        Outcome outcome,
        String explanation) {

    public RoundResult {
        Objects.requireNonNull(playerOneMove, "playerOneMove must not be null");
        Objects.requireNonNull(playerTwoMove, "playerTwoMove must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
    }
}
