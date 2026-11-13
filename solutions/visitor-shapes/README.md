# Classic OOP 1: Shape Hierarchy & Visitor Pattern

## Design & Trade-Off Notes

1. **Generic Return-Value Visitor (`ShapeVisitor<R>` / `<R> R accept(ShapeVisitor<R>)`)**:
   - Instead of `void accept(Visitor)` mutating internal accumulator state on the visitor, returning `R` keeps `AreaVisitor` completely **stateless and thread-safe**, and makes adding future visitors (`PerimeterVisitor`, `SvgExportVisitor`, `BoundingBoxVisitor`) trivial without modifying any `Shape` implementation.
2. **Immutable, Self-Validating Value Objects**:
   - `Circle`, `Rectangle`, and `Triangle` are `public final` immutable classes that enforce geometric domain invariants in their constructors (finite positive dimensions and strict triangle inequality). Invalid shapes fail fast rather than propagating `NaN` or negative areas.
3. **Numerically Stable Triangle Area (Kahan's Sorted Heron Formula)**:
   - Standard `sqrt(s(s-a)(s-b)(s-c))` suffers catastrophic cancellation on thin "needle" triangles. Sorting `a >= b >= c` and parenthesizing `(a + (b + c)) * (c - (a - b)) * (c + (a - b)) * (a + (b - c))` preserves floating-point precision.
4. **Extensibility Trade-Off**:
   - The classic GoF Visitor makes adding new **operations** O(1) without touching existing classes, at the cost that adding a new **shape** requires adding a `visit*` method to `ShapeVisitor<R>`.

## Compile & Run (without leaving build artifacts in source tree)
```bash
javac -d /tmp/oop-visitor-shapes $(find src -name "*.java")
java -cp /tmp/oop-visitor-shapes com.oop.shapes.ShapeAreaDemo
```
