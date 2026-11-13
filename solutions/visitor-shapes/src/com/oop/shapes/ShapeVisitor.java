package com.oop.shapes;

/**
 * Visitor interface defining type-safe operations over all concrete {@link Shape} variants.
 *
 * @param <R> return type produced by visiting a shape
 */
public interface ShapeVisitor<R> {

    R visitCircle(Circle circle);

    R visitRectangle(Rectangle rectangle);

    R visitTriangle(Triangle triangle);
}
