package com.oop.shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Demonstration program constructing canonical and arbitrary Shape instances
 * and computing their areas via {@link AreaVisitor}.
 */
public final class ShapeAreaDemo {

    private ShapeAreaDemo() {}

    public static void main(String[] args) {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Circle(3.0));
        shapes.add(new Rectangle(4.0, 5.5));
        shapes.add(new Triangle(3.0, 4.0, 5.0));

        // Generate arbitrary valid shapes deterministically for reproducible runs
        Random rng = new Random(2026L);
        for (int i = 0; i < 6; i++) {
            switch (i % 3) {
                case 0 -> shapes.add(new Circle(1.0 + rng.nextDouble() * 10.0));
                case 1 -> shapes.add(new Rectangle(1.0 + rng.nextDouble() * 10.0, 1.0 + rng.nextDouble() * 10.0));
                default -> {
                    double a = 2.0 + rng.nextDouble() * 8.0;
                    double b = 2.0 + rng.nextDouble() * 8.0;
                    double minC = Math.abs(a - b) + 0.1;
                    double maxC = (a + b) - 0.1;
                    double c = minC + rng.nextDouble() * (maxC - minC);
                    shapes.add(new Triangle(a, b, c));
                }
            }
        }

        ShapeVisitor<Double> areaVisitor = new AreaVisitor();
        double totalArea = 0.0;
        for (Shape shape : shapes) {
            double area = shape.accept(areaVisitor);
            totalArea += area;
            System.out.printf("%-52s -> area = %.6f%n", shape, area);
        }
        System.out.printf("Total area across %d shapes = %.6f%n", shapes.size(), totalArea);
    }
}
