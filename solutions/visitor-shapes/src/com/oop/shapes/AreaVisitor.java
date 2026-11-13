package com.oop.shapes;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stateless, thread-safe {@link ShapeVisitor} computing exact Euclidean area.
 */
public final class AreaVisitor implements ShapeVisitor<Double> {

    @Override
    public Double visitCircle(Circle circle) {
        Objects.requireNonNull(circle, "circle must not be null");
        double r = circle.radius();
        return Math.PI * r * r;
    }

    @Override
    public Double visitRectangle(Rectangle rectangle) {
        Objects.requireNonNull(rectangle, "rectangle must not be null");
        return rectangle.width() * rectangle.height();
    }

    @Override
    public Double visitTriangle(Triangle triangle) {
        Objects.requireNonNull(triangle, "triangle must not be null");
        // Kahan's numerically stable sorted Heron formula avoids catastrophic cancellation
        // on needle-like triangles with extreme side-length ratios.
        double[] sides = {triangle.sideA(), triangle.sideB(), triangle.sideC()};
        Arrays.sort(sides);
        double a = sides[2]; // longest
        double b = sides[1];
        double c = sides[0]; // shortest
        double term = (a + (b + c)) * (c - (a - b)) * (c + (a - b)) * (a + (b - c));
        return 0.25 * Math.sqrt(Math.max(0.0, term));
    }
}
