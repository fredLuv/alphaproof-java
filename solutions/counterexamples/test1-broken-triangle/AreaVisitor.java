package broken.shapes;

public final class AreaVisitor implements ShapeVisitor {
    @Override public double visitCircle(Circle c) { return Math.PI * c.radius() * c.radius(); }
    @Override public double visitRectangle(Rectangle r) { return r.width() * r.height(); }
    @Override public double visitTriangle(Triangle t) {
        double s = (t.a() + t.b() + t.c()) / 2.0;
        return Math.sqrt(s * (s - t.a()) * (s - t.b()) * (s - t.c()));
    }

    public static void main(String[] args) {
        AreaVisitor av = new AreaVisitor();
        System.out.println(new Circle(2).accept(av));
        System.out.println(new Rectangle(3, 4).accept(av));
        System.out.println(new Triangle(3, 4, 5).accept(av));
    }
}
