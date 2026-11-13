package com.oop.psr;

import java.util.List;
import java.util.Random;

/**
 * Demonstration running both classic Paper-Scissors-Rock and an extended
 * 5-symbol Rock-Paper-Scissors-Lizard-Spock variant on the same {@link GameEngine}.
 */
public final class PaperScissorsRockDemo {

    private PaperScissorsRockDemo() {}

    public static void main(String[] args) {
        RuleSet classicRules = RuleSet.standardPaperScissorsRock();
        GameEngine classicEngine = new GameEngine(classicRules);

        Random rng = new Random(2026L);
        Player alice = new Player("Alice", (symbols, history) -> symbols.get(history.size() % symbols.size()));
        Player bob = new Player("Bob", (symbols, history) -> symbols.get(rng.nextInt(symbols.size())));

        GameEngine.MatchSummary summary = classicEngine.playMatch(alice, bob, 6);
        System.out.println("=== Classic Paper-Scissors-Rock ===");
        for (int i = 0; i < summary.rounds().size(); i++) {
            RoundResult r = summary.rounds().get(i);
            System.out.printf("Round %d: %-8s vs %-8s -> %-14s (%s)%n",
                    i + 1, r.playerOneMove().id(), r.playerTwoMove().id(), r.outcome(), r.explanation());
        }
        System.out.printf("Score: %s=%d, %s=%d, Draws=%d%n%n",
                summary.playerOneName(), summary.playerOneWins(),
                summary.playerTwoName(), summary.playerTwoWins(),
                summary.draws());

        // Demonstrate zero-modification generalization to 5-symbol RPSLS
        record CustomSymbol(String id) implements Symbol {}
        Symbol lizard = new CustomSymbol("lizard");
        Symbol spock = new CustomSymbol("spock");
        RuleSet rpslsRules = RuleSet.builder()
                .addRule(StandardSymbol.PAPER, StandardSymbol.ROCK, "covers")
                .addRule(StandardSymbol.ROCK, StandardSymbol.SCISSORS, "crushes")
                .addRule(StandardSymbol.ROCK, lizard, "crushes")
                .addRule(spock, StandardSymbol.ROCK, "vaporizes")
                .addRule(StandardSymbol.PAPER, spock, "disproves")
                .addRule(StandardSymbol.SCISSORS, StandardSymbol.PAPER, "cuts")
                .addRule(StandardSymbol.SCISSORS, lizard, "decapitates")
                .addRule(lizard, spock, "poisons")
                .addRule(lizard, StandardSymbol.PAPER, "eats")
                .addRule(spock, StandardSymbol.SCISSORS, "smashes")
                .build();

        GameEngine rpslsEngine = new GameEngine(rpslsRules);
        System.out.println("=== Generalized 5-Symbol RPSLS ===");
        List<RoundResult> showcase = List.of(
                rpslsEngine.playRound(spock, StandardSymbol.SCISSORS),
                rpslsEngine.playRound(lizard, spock),
                rpslsEngine.playRound(StandardSymbol.PAPER, spock));
        for (RoundResult r : showcase) {
            System.out.printf("%s vs %s -> %s (%s)%n",
                    r.playerOneMove().id(), r.playerTwoMove().id(), r.outcome(), r.explanation());
        }
    }
}
