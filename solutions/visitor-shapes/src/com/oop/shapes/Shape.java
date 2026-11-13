package com.oop.shapes;

/**
 * Abstraction for geometric shapes supporting double-dispatch operations
 * via {@link ShapeVisitor}.
 */
public interface Shape {

    /**
     * Accepts a visitor and dispatches to the visitor method matching this shape's concrete type.
     *
     * @param visitor operation to apply to this shape
     * @param <R> result type produced by the visitor
     * @return result computed by the visitor
     */
    <R> R accept(ShapeVisitor<R> visitor);
}
