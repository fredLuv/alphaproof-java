package com.oop.psr;

/**
 * Standard Paper-Scissors-Rock symbols implementing the open {@link Symbol} interface.
 */
public enum StandardSymbol implements Symbol {
    ROCK("rock", "closed fist"),
    PAPER("paper", "open hand"),
    SCISSORS("scissors", "index and middle finger");

    private final String id;
    private final String handShape;

    StandardSymbol(String id, String handShape) {
        this.id = id;
        this.handShape = handShape;
    }

    @Override
    public String id() {
        return id;
    }

    public String handShape() {
        return handShape;
    }
}
