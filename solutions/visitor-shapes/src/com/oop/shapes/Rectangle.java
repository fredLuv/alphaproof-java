package com.oop.shapes;

import java.util.Objects;

/**
 * Immutable axis-aligned rectangle defined by strictly positive finite width and height.
 */
public final class Rectangle implements Shape {

    private final double width;
    private final double height;

    public Rectangle(double width, double height) {
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("Rectangle width must be finite and > 0, got: " + width);
        }
        if (!Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("Rectangle height must be finite and > 0, got: " + height);
        }
        this.width = width;
        this.height = height;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    @Override
    public <R> R accept(ShapeVisitor<R> visitor) {
        Objects.requireNonNull(visitor, "visitor must not be null");
        return visitor.visitRectangle(this);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Rectangle other)
                && Double.compare(width, other.width) == 0
                && Double.compare(height, other.height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return String.format("Rectangle[width=%.4f, height=%.4f]", width, height);
    }
}
