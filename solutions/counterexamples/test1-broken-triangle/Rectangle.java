package broken.shapes;

public final class Rectangle implements Shape {
    private final double width, height;
    public Rectangle(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        this.width = width;
        this.height = height;
    }
    public double width() { return width; }
    public double height() { return height; }
    @Override public double accept(ShapeVisitor v) { return v.visitRectangle(this); }
}
