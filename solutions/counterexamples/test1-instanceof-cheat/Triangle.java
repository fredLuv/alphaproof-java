package bad.shapes;

public final class Triangle implements Shape {
    private final double a, b, c;
    public Triangle(double a, double b, double c) {
        if (a <= 0 || b <= 0 || c <= 0 || a + b <= c || a + c <= b || b + c <= a) {
            throw new IllegalArgumentException();
        }
        this.a = a; this.b = b; this.c = c;
    }
    public double a() { return a; }
    public double b() { return b; }
    public double c() { return c; }
    @Override public double accept(ShapeVisitor visitor) { return visitor.visitShape(this); }
}
