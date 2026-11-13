package dev.oopproof.tactics;

import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.RuntimeBridge;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.lang.model.element.TypeElement;

/**
 * Verifies mathematical correctness, domain precondition enforcement, positivity,
 * symmetry, numerical stability, and degree-2 scaling homogeneity of AreaVisitor
 * across Circle, Rectangle, and Triangle.
 */
public final class GeometryAreaProofTactic implements Tactic {

    @Override
    public ProofStep apply(String stepId, WorkspaceAnalyzer.CodebaseModel model, BindingEnv env) {
        TypeElement circleEl = env.type("CIRCLE").orElse(null);
        TypeElement rectEl = env.type("RECTANGLE").orElse(null);
        TypeElement triEl = env.type("TRIANGLE").orElse(null);
        TypeElement visitorEl = env.type("AREA_VISITOR").orElse(null);
        String acceptName = env.symbol("ACCEPT_METHOD").orElse("accept");

        Map<String, Object> evidence = new LinkedHashMap<>();
        List<String> witnesses = new ArrayList<>();

        if (circleEl == null || rectEl == null || triEl == null || visitorEl == null) {
            return refuted(stepId, evidence, List.of("Unbound shape/visitor types"),
                    "Cannot run geometry proof without bound Shape and AreaVisitor types.");
        }

        try {
            Class<?> circleCls = model.loadClass(circleEl.getQualifiedName().toString());
            Class<?> rectCls = model.loadClass(rectEl.getQualifiedName().toString());
            Class<?> triCls = model.loadClass(triEl.getQualifiedName().toString());
            Class<?> visitorCls = model.loadClass(visitorEl.getQualifiedName().toString());

            // 1. Precondition / Domain Soundness checks
            int preconditionCases = 0;
            String preErr = checkInvalidRejection(circleCls, new double[]{0.0}, "Circle(0)")
                    .orElseGet(() -> checkInvalidRejection(circleCls, new double[]{-3.0}, "Circle(-3)").orElse(null));
            preconditionCases += 2;
            if (preErr == null) {
                preErr = checkInvalidRejection(circleCls, new double[]{Double.NaN}, "Circle(NaN)").orElse(null);
                preconditionCases++;
            }
            if (preErr == null) {
                preErr = checkInvalidRejection(rectCls, new double[]{0.0, 5.0}, "Rectangle(0, 5)")
                        .orElseGet(() -> checkInvalidRejection(rectCls, new double[]{4.0, -2.0}, "Rectangle(4, -2)").orElse(null));
                preconditionCases += 2;
            }
            if (preErr == null) {
                preErr = checkInvalidRejection(triCls, new double[]{1.0, 2.0, 10.0}, "Triangle(1, 2, 10) [triangle inequality]")
                        .orElseGet(() -> checkInvalidRejection(triCls, new double[]{1.0, 2.0, 3.0}, "Triangle(1, 2, 3) [degenerate]")
                                .orElseGet(() -> checkInvalidRejection(triCls, new double[]{-3.0, 4.0, 5.0}, "Triangle(-3, 4, 5)").orElse(null)));
                preconditionCases += 3;
            }
            if (preErr != null) {
                return refuted(stepId, evidence, List.of(preErr),
                        "Domain precondition lemma failed: " + preErr);
            }

            // 2. Exact Formula + Positivity + Symmetry + Scaling Homogeneity
            int exactCases = 0;
            int homogeneityChecks = 0;
            Random rng = new Random(42L);

            // Canonical + extreme numerical stability test vectors
            double[] radii = new double[23];
            radii[0] = 1.0;
            radii[1] = 2.5;
            radii[2] = 1e-4;
            for (int i = 3; i < radii.length; i++) {
                radii[i] = 0.05 + rng.nextDouble() * 500.0;
            }
            for (double r : radii) {
                Object c = RuntimeBridge.instantiateNumeric(circleCls, r);
                double area = evalArea(c, visitorCls, acceptName);
                double expected = Math.PI * r * r;
                if (!approxEqual(area, expected)) {
                    return refuted(stepId, evidence,
                            List.of("Circle(" + r + ") returned " + area + ", expected " + expected),
                            "Circle area mismatch at r=" + r);
                }
                if (!(area > 0.0)) {
                    return refuted(stepId, evidence, List.of("Circle(" + r + ") area <= 0: " + area),
                            "Positivity violated for Circle(" + r + ")");
                }
                double scaledArea = evalArea(RuntimeBridge.instantiateNumeric(circleCls, 2.5 * r), visitorCls, acceptName);
                if (!approxEqual(scaledArea, 6.25 * area)) {
                    return refuted(stepId, evidence,
                            List.of("Scaling homogeneity failed for Circle(" + r + ") * 2.5"),
                            "Degree-2 scaling homogeneity violated on Circle.");
                }
                exactCases++;
                homogeneityChecks++;
            }

            double[][] rects = new double[23][2];
            rects[0] = new double[]{3.0, 4.0};
            rects[1] = new double[]{1e5, 1e-3};
            rects[2] = new double[]{7.25, 7.25};
            for (int i = 3; i < rects.length; i++) {
                rects[i] = new double[]{0.1 + rng.nextDouble() * 200.0, 0.1 + rng.nextDouble() * 200.0};
            }
            for (double[] wh : rects) {
                double w = wh[0], h = wh[1];
                double area = evalArea(RuntimeBridge.instantiateNumeric(rectCls, w, h), visitorCls, acceptName);
                double swapped = evalArea(RuntimeBridge.instantiateNumeric(rectCls, h, w), visitorCls, acceptName);
                double expected = w * h;
                if (!approxEqual(area, expected) || !approxEqual(area, swapped)) {
                    return refuted(stepId, evidence,
                            List.of("Rectangle(" + w + "," + h + ")=" + area + ", swapped=" + swapped + ", expected=" + expected),
                            "Rectangle area or symmetry invariant failed.");
                }
                double scaled = evalArea(RuntimeBridge.instantiateNumeric(rectCls, 3.0 * w, 3.0 * h), visitorCls, acceptName);
                if (!approxEqual(scaled, 9.0 * area)) {
                    return refuted(stepId, evidence, List.of("Scaling homogeneity failed for Rectangle"), "Rectangle scaling failed.");
                }
                exactCases++;
                homogeneityChecks++;
            }

            List<double[]> tris = new ArrayList<>();
            tris.add(new double[]{3.0, 4.0, 5.0});
            tris.add(new double[]{5.0, 12.0, 13.0});
            tris.add(new double[]{10.0, 10.0, 10.0});
            tris.add(new double[]{1e5, 1e5, 1.0}); // needle triangle testing Heron stability
            tris.add(new double[]{1e-4, 1e-4, 1.5e-4});
            for (int i = 0; i < 20; i++) {
                double a = 1.0 + rng.nextDouble() * 100.0;
                double b = 1.0 + rng.nextDouble() * 100.0;
                double minC = Math.abs(a - b) + 0.05;
                double maxC = (a + b) - 0.05;
                double c = minC + rng.nextDouble() * Math.max(0.01, maxC - minC);
                tris.add(new double[]{a, b, c});
            }
            for (double[] sides : tris) {
                double a = sides[0], b = sides[1], c = sides[2];
                double area = evalArea(RuntimeBridge.instantiateNumeric(triCls, a, b, c), visitorCls, acceptName);
                double permuted = evalArea(RuntimeBridge.instantiateNumeric(triCls, b, c, a), visitorCls, acceptName);
                double expected = stableHeron(a, b, c);
                if (!approxEqual(area, expected) || !approxEqual(area, permuted)) {
                    return refuted(stepId, evidence,
                            List.of("Triangle(" + a + "," + b + "," + c + ")=" + area + ", permuted=" + permuted + ", expected=" + expected),
                            "Triangle area or permutation symmetry failed.");
                }
                double scaled = evalArea(RuntimeBridge.instantiateNumeric(triCls, 2.0 * a, 2.0 * b, 2.0 * c), visitorCls, acceptName);
                if (!approxEqual(scaled, 4.0 * area)) {
                    return refuted(stepId, evidence, List.of("Scaling homogeneity failed for Triangle"), "Triangle scaling failed.");
                }
                exactCases++;
                homogeneityChecks++;
            }

            evidence.put("preconditionChecks", preconditionCases);
            evidence.put("exactAreaChecks", exactCases);
            evidence.put("scalingHomogeneityChecks", homogeneityChecks);
            witnesses.add("Preconditions rejected " + preconditionCases + "/" + preconditionCases + " invalid shapes");
            witnesses.add("Exactness + symmetry + k^2 scaling proved across " + exactCases + " shapes (including needle triangle 1e5,1e5,1)");

            return new ProofStep(
                    stepId,
                    "GeometryAreaAndInvariants",
                    "STRATUM_3_BEHAVIOR",
                    "GeometryAreaProofTactic",
                    "AreaVisitor satisfies exact formulas, precondition guards, positivity, symmetry, and k^2 scaling homogeneity",
                    ProofStatus.QED,
                    "Proved domain guards (" + preconditionCases + " invalid inputs), exact area formulas, symmetry, and k^2 scaling over " + exactCases + " instances.",
                    evidence,
                    witnesses,
                    List.of());
        } catch (Throwable t) {
            return refuted(stepId, evidence, List.of(t.getClass().getName() + ": " + t.getMessage()),
                    "Geometry verification threw unexpected error: " + t.getMessage());
        }
    }

    private static java.util.Optional<String> checkInvalidRejection(Class<?> cls, double[] args, String label) {
        try {
            Object instance = RuntimeBridge.instantiateNumeric(cls, args);
            return java.util.Optional.of(label + " was constructed instead of throwing IllegalArgumentException (got " + instance + ")");
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return java.util.Optional.empty();
        } catch (Throwable other) {
            return java.util.Optional.of(label + " threw unexpected exception " + other.getClass().getName() + ": " + other.getMessage());
        }
    }

    private static double evalArea(Object shape, Class<?> visitorCls, String acceptName) throws Throwable {
        Object visitor = RuntimeBridge.instantiateZeroArg(visitorCls);
        Object res = RuntimeBridge.invokeAccept(shape, visitor, acceptName);
        if (res instanceof Number n) {
            return n.doubleValue();
        }
        // Fallback for stateful void visitors exposing getArea()/area()/getResult()
        for (String getter : List.of("getArea", "area", "getResult", "getTotalArea", "value")) {
            try {
                Method m = visitorCls.getMethod(getter);
                Object v = m.invoke(visitor);
                if (v instanceof Number n) {
                    return n.doubleValue();
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new IllegalStateException("AreaVisitor neither returned a Number from " + acceptName + " nor exposed a numeric getter");
    }

    private static double stableHeron(double a, double b, double c) {
        double[] s = {a, b, c};
        Arrays.sort(s);
        double x = s[2], y = s[1], z = s[0]; // x >= y >= z
        double prod = (x + (y + z)) * (z - (x - y)) * (z + (x - y)) * (x + (y - z));
        return 0.25 * Math.sqrt(Math.max(0.0, prod));
    }

    private static boolean approxEqual(double actual, double expected) {
        if (Double.isNaN(actual) || Double.isInfinite(actual)) {
            return false;
        }
        double tol = 1e-7 * Math.max(1.0, Math.abs(expected));
        return Math.abs(actual - expected) <= tol;
    }

    private static ProofStep refuted(
            String stepId, Map<String, Object> evidence, List<String> witnesses, String summary) {
        return new ProofStep(
                stepId,
                "GeometryAreaAndInvariants",
                "STRATUM_3_BEHAVIOR",
                "GeometryAreaProofTactic",
                "AreaVisitor satisfies exact formulas, precondition guards, positivity, symmetry, and k^2 scaling homogeneity",
                ProofStatus.REFUTED,
                summary,
                evidence,
                witnesses,
                List.of());
    }
}
