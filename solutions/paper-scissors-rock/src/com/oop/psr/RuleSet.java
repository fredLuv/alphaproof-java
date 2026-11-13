package com.oop.psr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable directed dominance graph defining which {@link Symbol} beats which
 * and the descriptive action verb (e.g., "wraps", "blunts", "cuts").
 * Enforces irreflexivity and antisymmetry at build time.
 */
public final class RuleSet {

    private final Map<Symbol, Map<Symbol, String>> dominance;
    private final List<Symbol> symbols;

    private RuleSet(Map<Symbol, Map<Symbol, String>> dominance, List<Symbol> symbols) {
        this.dominance = dominance;
        this.symbols = symbols;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RuleSet standardPaperScissorsRock() {
        return builder()
                .addRule(StandardSymbol.PAPER, StandardSymbol.ROCK, "wraps")
                .addRule(StandardSymbol.ROCK, StandardSymbol.SCISSORS, "blunts")
                .addRule(StandardSymbol.SCISSORS, StandardSymbol.PAPER, "cuts")
                .build();
    }

    public List<Symbol> symbols() {
        return symbols;
    }

    public boolean supports(Symbol symbol) {
        return dominance.containsKey(symbol) || symbols.contains(symbol);
    }

    public Optional<String> winningVerb(Symbol winner, Symbol loser) {
        Map<Symbol, String> beaten = dominance.get(winner);
        if (beaten == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(beaten.get(loser));
    }

    public RoundResult adjudicate(Symbol playerOneMove, Symbol playerTwoMove) {
        Objects.requireNonNull(playerOneMove, "playerOneMove must not be null");
        Objects.requireNonNull(playerTwoMove, "playerTwoMove must not be null");
        if (!supports(playerOneMove)) {
            throw new IllegalArgumentException("Unsupported symbol for player 1: " + playerOneMove.id());
        }
        if (!supports(playerTwoMove)) {
            throw new IllegalArgumentException("Unsupported symbol for player 2: " + playerTwoMove.id());
        }
        if (playerOneMove.equals(playerTwoMove)) {
            return new RoundResult(
                    playerOneMove, playerTwoMove, Outcome.DRAW,
                    "both played " + playerOneMove.id());
        }
        Optional<String> p1BeatsP2 = winningVerb(playerOneMove, playerTwoMove);
        if (p1BeatsP2.isPresent()) {
            return new RoundResult(
                    playerOneMove, playerTwoMove, Outcome.PLAYER_ONE_WIN,
                    playerOneMove.id() + " " + p1BeatsP2.get() + " " + playerTwoMove.id());
        }
        Optional<String> p2BeatsP1 = winningVerb(playerTwoMove, playerOneMove);
        if (p2BeatsP1.isPresent()) {
            return new RoundResult(
                    playerOneMove, playerTwoMove, Outcome.PLAYER_TWO_WIN,
                    playerTwoMove.id() + " " + p2BeatsP1.get() + " " + playerOneMove.id());
        }
        return new RoundResult(
                playerOneMove, playerTwoMove, Outcome.DRAW,
                "no dominance relation between " + playerOneMove.id() + " and " + playerTwoMove.id());
    }

    public static final class Builder {
        private final Map<Symbol, Map<Symbol, String>> edges = new LinkedHashMap<>();
        private final Set<Symbol> registered = new LinkedHashSet<>();

        private Builder() {}

        public Builder addRule(Symbol winner, Symbol loser, String verb) {
            Objects.requireNonNull(winner, "winner must not be null");
            Objects.requireNonNull(loser, "loser must not be null");
            Objects.requireNonNull(verb, "verb must not be null");
            if (winner.equals(loser)) {
                throw new IllegalArgumentException("A symbol cannot beat itself: " + winner.id());
            }
            Map<Symbol, String> reverse = edges.get(loser);
            if (reverse != null && reverse.containsKey(winner)) {
                throw new IllegalStateException(
                        "Contradictory rule: " + loser.id() + " already beats " + winner.id());
            }
            registered.add(winner);
            registered.add(loser);
            edges.computeIfAbsent(winner, k -> new LinkedHashMap<>()).put(loser, verb);
            return this;
        }

        public RuleSet build() {
            if (registered.isEmpty()) {
                throw new IllegalStateException("RuleSet must contain at least one rule");
            }
            Map<Symbol, Map<Symbol, String>> frozen = new LinkedHashMap<>();
            for (Map.Entry<Symbol, Map<Symbol, String>> entry : edges.entrySet()) {
                frozen.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
            }
            return new RuleSet(
                    Collections.unmodifiableMap(frozen),
                    Collections.unmodifiableList(new ArrayList<>(registered)));
        }
    }
}
