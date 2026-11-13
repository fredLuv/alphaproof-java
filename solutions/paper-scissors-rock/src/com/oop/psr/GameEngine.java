package com.oop.psr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Extensible two-player simultaneous game engine parameterized by an arbitrary {@link RuleSet}.
 */
public final class GameEngine {

    private final RuleSet ruleSet;

    public GameEngine(RuleSet ruleSet) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet must not be null");
    }

    public RuleSet ruleSet() {
        return ruleSet;
    }

    public RoundResult playRound(Symbol playerOneMove, Symbol playerTwoMove) {
        return ruleSet.adjudicate(playerOneMove, playerTwoMove);
    }

    public MatchSummary playMatch(Player playerOne, Player playerTwo, int rounds) {
        Objects.requireNonNull(playerOne, "playerOne must not be null");
        Objects.requireNonNull(playerTwo, "playerTwo must not be null");
        if (rounds <= 0) {
            throw new IllegalArgumentException("rounds must be > 0, got: " + rounds);
        }
        List<RoundResult> history = new ArrayList<>(rounds);
        int p1Wins = 0;
        int p2Wins = 0;
        int draws = 0;
        List<Symbol> symbols = ruleSet.symbols();

        for (int i = 0; i < rounds; i++) {
            List<RoundResult> roHistory = Collections.unmodifiableList(history);
            Symbol m1 = playerOne.nextMove(symbols, roHistory);
            Symbol m2 = playerTwo.nextMove(symbols, roHistory);
            RoundResult res = playRound(m1, m2);
            history.add(res);
            switch (res.outcome()) {
                case PLAYER_ONE_WIN -> p1Wins++;
                case PLAYER_TWO_WIN -> p2Wins++;
                case DRAW -> draws++;
            }
        }
        return new MatchSummary(
                playerOne.name(), playerTwo.name(),
                p1Wins, p2Wins, draws,
                Collections.unmodifiableList(history));
    }

    public record MatchSummary(
            String playerOneName,
            String playerTwoName,
            int playerOneWins,
            int playerTwoWins,
            int draws,
            List<RoundResult> rounds) {}
}
