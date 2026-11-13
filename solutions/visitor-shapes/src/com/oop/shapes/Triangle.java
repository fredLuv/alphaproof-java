package com.oop.shapes;

import java.util.Objects;

/**
 * Immutable triangle defined by three strictly positive finite side lengths
 * satisfying the strict triangle inequality.
 */
public final class Triangle implements Shape {

    private final double sideA;
    private final double sideB;
    private final double sideC;

    public Triangle(double sideA, double sideB, double sideC) {
        validateSide("sideA", sideA);
        validateSide("sideB", sideB);
        validateSide("sideC", sideC);
        if (sideA + sideB <= sideC || sideA + sideC <= sideB || sideB + sideC <= sideA) {
            throw new IllegalArgumentException(String.format(
                    "Sides (%.4f, %.4f, %.4f) violate the strict triangle inequality", sideA, sideB, sideC));
        }
        this.sideA = sideA;
        this.sideB = sideB;
        this.sideC = sideC;
    }

    private static void validateSide(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Triangle " + name + " must be finite and > 0, got: " + value);
        }
    }

    public double sideA() {
        return sideA;
    }

    public double sideB() {
        return sideB;
    }

    public double sideC() {
        return sideC;
    }

    @Override
    public <R> R accept(ShapeVisitor<R> visitor) {
        Objects.requireNonNull(visitor, "visitor must not be null");
        return visitor.visitTriangle(this);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Triangle other)
                && Double.compare(sideA, other.sideA) == 0
                && Double.compare(sideB, other.sideB) == 0
                && Double.compare(sideC, other.sideC) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sideA, sideB, sideC);
    }

    @Override
    public String toString() {
        return String.format("Triangle[sideA=%.4f, sideB=%.4f, sideC=%.4f]", sideA, sideB, sideC);
    }
}
