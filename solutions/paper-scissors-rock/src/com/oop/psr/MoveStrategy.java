package com.oop.psr;

import java.util.List;

/**
 * Strategy abstraction for how a player selects a {@link Symbol} for the next round.
 */
@FunctionalInterface
public interface MoveStrategy {

    Symbol chooseMove(List<Symbol> availableSymbols, List<RoundResult> history);
}
