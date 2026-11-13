package broken.shapes;

public interface ShapeVisitor {
    double visitCircle(Circle c);
    double visitRectangle(Rectangle r);
    double visitTriangle(Triangle t);
}
