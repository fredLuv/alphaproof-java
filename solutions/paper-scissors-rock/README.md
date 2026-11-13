# Classic OOP 2: Generalized Paper Scissors Rock

## Design & Trade-Off Notes

1. **Data-Driven Dominance Graph (`RuleSet`) Instead of Hardcoded `switch` Logic**:
   - Encoding the dominance schema as an immutable directed graph `(winner, loser) -> verb` eliminates `switch`/`if-else` branching and validates **irreflexivity** (`A` cannot beat `A`) and **antisymmetry** (contradictory `A > B` & `B > A` pairs rejected at build time).
2. **Open `Symbol` Interface + Enum Convenience**:
   - `StandardSymbol` gives compile-time safety for `ROCK`, `PAPER`, `SCISSORS`, while `Symbol` allows external callers to introduce new symbols (e.g., `lizard`, `spock`) without modifying `GameEngine` or `StandardSymbol`.
3. **Strategy Pattern for Players (`MoveStrategy`)**:
   - Decouples `Player` identity from move-selection logic (cyclic, random, adaptive, or interactive CLI), keeping `GameEngine` deterministic and easy to unit-test.
4. **Rich `RoundResult` Value Object**:
   - Returning `(playerOneMove, playerTwoMove, outcome, explanation)` preserves auditability ("paper wraps rock") rather than collapsing the result to a bare integer.

## Compile & Run (without leaving build artifacts in source tree)
```bash
javac -d /tmp/oop-psr $(find src -name "*.java")
java -cp /tmp/oop-psr com.oop.psr.PaperScissorsRockDemo
```
