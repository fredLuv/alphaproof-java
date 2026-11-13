package com.oop.shapes;

import java.util.Objects;

/**
 * Immutable circle defined by a strictly positive finite radius.
 */
public final class Circle implements Shape {

    private final double radius;

    public Circle(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Circle radius must be finite and > 0, got: " + radius);
        }
        this.radius = radius;
    }

    public double radius() {
        return radius;
    }

    @Override
    public <R> R accept(ShapeVisitor<R> visitor) {
        Objects.requireNonNull(visitor, "visitor must not be null");
        return visitor.visitCircle(this);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Circle other) && Double.compare(radius, other.radius) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(radius);
    }

    @Override
    public String toString() {
        return String.format("Circle[radius=%.4f]", radius);
    }
}
