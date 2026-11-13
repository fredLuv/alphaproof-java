package broken.shapes;

public interface Shape {
    double accept(ShapeVisitor visitor);
}
