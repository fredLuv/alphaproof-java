package bad.shapes;

public interface Shape {
    double accept(ShapeVisitor visitor);
}
