package bad.psr;

public final class Game {
    public Outcome play(Move p1, Move p2) {
        if (p1 == p2) return Outcome.DRAW;
        return switch (p1) {
            case ROCK -> p2 == Move.SCISSORS ? Outcome.PLAYER_ONE_WIN : Outcome.PLAYER_TWO_WIN;
            case PAPER -> p2 == Move.ROCK ? Outcome.PLAYER_ONE_WIN : Outcome.PLAYER_TWO_WIN;
            case SCISSORS -> p2 == Move.PAPER ? Outcome.PLAYER_ONE_WIN : Outcome.PLAYER_TWO_WIN;
        };
    }

    public static void main(String[] args) {
        Game g = new Game();
        System.out.println("ROCK vs SCISSORS: " + g.play(Move.ROCK, Move.SCISSORS));
        System.out.println("PAPER vs ROCK: " + g.play(Move.PAPER, Move.ROCK));
        System.out.println("SCISSORS vs PAPER: " + g.play(Move.SCISSORS, Move.PAPER));
    }
}
