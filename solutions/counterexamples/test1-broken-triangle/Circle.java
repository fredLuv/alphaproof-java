package broken.shapes;

public final class Circle implements Shape {
    private final double radius;
    public Circle(double radius) {
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException();
        this.radius = radius;
    }
    public double radius() { return radius; }
    @Override public double accept(ShapeVisitor v) { return v.visitCircle(this); }
}
