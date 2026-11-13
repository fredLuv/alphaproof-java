package com.oop.psr;

import java.util.List;
import java.util.Objects;

/**
 * Named player delegating gesture selection to an interchangeable {@link MoveStrategy}.
 */
public final class Player {

    private final String name;
    private final MoveStrategy strategy;

    public Player(String name, MoveStrategy strategy) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public String name() {
        return name;
    }

    public Symbol nextMove(List<Symbol> availableSymbols, List<RoundResult> history) {
        return strategy.chooseMove(availableSymbols, history);
    }
}
