package broken.shapes;

public final class Triangle implements Shape {
    private final double a, b, c;
    public Triangle(double a, double b, double c) {
        // BUG: omits triangle inequality check (e.g. 1, 2, 10 succeeds!)
        if (a <= 0 || b <= 0 || c <= 0) throw new IllegalArgumentException();
        this.a = a; this.b = b; this.c = c;
    }
    public double a() { return a; }
    public double b() { return b; }
    public double c() { return c; }
    @Override public double accept(ShapeVisitor v) { return v.visitTriangle(this); }
}
