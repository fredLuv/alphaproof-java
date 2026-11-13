package dev.oopproof.spec;

import dev.oopproof.core.AstQueries;
import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import dev.oopproof.tactics.EncapsulationTactic;
import dev.oopproof.tactics.ExtensibilityInjectionTactic;
import dev.oopproof.tactics.GameRulesProofTactic;
import dev.oopproof.tactics.GeometryAreaProofTactic;
import dev.oopproof.tactics.HygieneAndCompileTactic;
import dev.oopproof.tactics.VisitorDoubleDispatchTactic;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/**
 * Built-in formal specifications for classic OOP designs (Visitor Shape Hierarchy & Generalized Paper-Scissors-Rock).
 */
public final class BuiltinSpecs {

    private BuiltinSpecs() {}

    public static List<FormalSpec> all() {
        return List.of(visitorShapes(), paperScissorsRock());
    }

    public static FormalSpec byId(String id) {
        return all().stream()
                .filter(s -> s.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public static FormalSpec visitorShapes() {
        return new FormalSpec() {
            @Override
            public String id() { return "oop-visitor-shapes"; }

            @Override
            public String title() { return "Classic OOP: Shape Hierarchy, Visitor Pattern & AreaVisitor"; }

            @Override
            public String description() {
                return "Proves Shape interface/abstract base, Circle/Rectangle/Triangle subtypes, "
                        + "AST double-dispatch Visitor without instanceof, domain guards, exact area & scaling invariants, "
                        + "runnable demo, and Open-Closed extensibility via synthetic PerimeterVisitor.";
            }

            @Override
            public List<ScoredBinding> proposeBindings(WorkspaceAnalyzer.CodebaseModel model) {
                List<TypeElement> bases = rankTypes(model, List.of("shape"), true);
                List<TypeElement> circles = rankTypes(model, List.of("circle"), false);
                List<TypeElement> rects = rankTypes(model, List.of("rectangle", "rect"), false);
                List<TypeElement> tris = rankTypes(model, List.of("triangle", "tri"), false);
                List<TypeElement> visitors = rankTypes(model, List.of("shapevisitor", "visitor"), true);
                List<TypeElement> areaVisitors = rankTypes(model, List.of("areavisitor"), false);
                List<TypeElement> mains = findMainTypes(model);

                List<ScoredBinding> out = new ArrayList<>();
                for (TypeElement base : top(bases, 2)) {
                    for (TypeElement vis : top(visitors, 2)) {
                        for (TypeElement av : top(areaVisitors, 2)) {
                            for (TypeElement c : top(circles, 2)) {
                                for (TypeElement r : top(rects, 2)) {
                                    for (TypeElement t : top(tris, 2)) {
                                        String acceptName = findAcceptName(base, vis, model);
                                        BindingEnv env = new BindingEnv()
                                                .withType("SHAPE_BASE", base)
                                                .withType("CIRCLE", c)
                                                .withType("RECTANGLE", r)
                                                .withType("TRIANGLE", t)
                                                .withType("VISITOR_BASE", vis)
                                                .withType("AREA_VISITOR", av)
                                                .withSymbol("ACCEPT_METHOD", acceptName);
                                        if (!mains.isEmpty()) {
                                            env = env.withType("DEMO_MAIN", mains.get(0));
                                        }
                                        double score = computeVisitorPrior(model, base, vis, av, c, r, t);
                                        out.add(new ScoredBinding(env, score,
                                                "Shape=" + base.getSimpleName() + ", Visitor=" + vis.getSimpleName()
                                                        + ", AreaVisitor=" + av.getSimpleName()));
                                    }
                                }
                            }
                        }
                    }
                }
                if (out.isEmpty()) {
                    BindingEnv fallback = new BindingEnv();
                    if (!bases.isEmpty()) fallback = fallback.withType("SHAPE_BASE", bases.get(0));
                    if (!circles.isEmpty()) fallback = fallback.withType("CIRCLE", circles.get(0));
                    if (!rects.isEmpty()) fallback = fallback.withType("RECTANGLE", rects.get(0));
                    if (!tris.isEmpty()) fallback = fallback.withType("TRIANGLE", tris.get(0));
                    if (!visitors.isEmpty()) fallback = fallback.withType("VISITOR_BASE", visitors.get(0));
                    if (!areaVisitors.isEmpty()) fallback = fallback.withType("AREA_VISITOR", areaVisitors.get(0));
                    if (!mains.isEmpty()) fallback = fallback.withType("DEMO_MAIN", mains.get(0));
                    out.add(new ScoredBinding(fallback, 0.25, "Partial fallback binding"));
                }
                out.sort((a, b) -> Double.compare(b.priorScore(), a.priorScore()));
                return out;
            }

            @Override
            public List<Obligation> obligations() {
                return List.of(
                        new Obligation(
                                "hygiene-compile",
                                "HygieneAndCompile",
                                "STRATUM_1_HYGIENE",
                                "Zero build outputs (.class/.jar) and clean -Xlint:all javac compilation",
                                new HygieneAndCompileTactic(true),
                                true),
                        new Obligation(
                                "visitor-double-dispatch",
                                "VisitorDoubleDispatch",
                                "STRATUM_2_STRUCTURE",
                                "Interface/abstract Shape & Visitor with AST accept->visit(this) double dispatch and zero instanceof",
                                new VisitorDoubleDispatchTactic(),
                                true),
                        new Obligation(
                                "encapsulation",
                                "EncapsulationAndModularity",
                                "STRATUM_2_STRUCTURE",
                                "Strict private encapsulation and clean multi-class OOP decomposition (>= 6 types)",
                                new EncapsulationTactic(6),
                                false),
                        new Obligation(
                                "geometry-area-proof",
                                "GeometryAreaAndInvariants",
                                "STRATUM_3_BEHAVIOR",
                                "Domain guards, exact formulas, positivity, symmetry, and k^2 scaling homogeneity",
                                new GeometryAreaProofTactic(),
                                false),
                        new Obligation(
                                "runnable-demo",
                                "RunnableProgramExecution",
                                "STRATUM_3_BEHAVIOR",
                                "Runnable main program constructs arbitrary shape instances and applies AreaVisitor",
                                runnableMainTactic("DEMO_MAIN", 3),
                                false),
                        new Obligation(
                                "visitor-extensibility",
                                "VisitorExtensibility",
                                "STRATUM_4_EXTENSIBILITY",
                                "Open-Closed Principle: synthetic PerimeterVisitor compiles and runs with zero edits to candidate",
                                ExtensibilityInjectionTactic.visitorPerimeterExtension(),
                                false)
                );
            }
        };
    }

    public static FormalSpec paperScissorsRock() {
        return new FormalSpec() {
            @Override
            public String id() { return "oop-paper-scissors-rock"; }

            @Override
            public String title() { return "Classic OOP: Generalized Extensible Paper-Scissors-Rock"; }

            @Override
            public String description() {
                return "Proves two-player Paper-Scissors-Rock rules (totality, draw reflexivity, "
                        + "zero-sum antisymmetry, determinism, paper>rock>scissors>paper), OOP encapsulation, "
                        + "runnable simulation, and Open-Closed generalization via synthetic 5-symbol RPSLS extension.";
            }

            @Override
            public List<ScoredBinding> proposeBindings(WorkspaceAnalyzer.CodebaseModel model) {
                List<TypeElement> symbols = rankTypes(model, List.of("symbol", "gesture", "move", "hand", "choice"), false);
                List<TypeElement> providers = rankTypes(model, List.of("standardsymbol", "standard", "symbol", "move", "gesture"), false);
                List<TypeElement> adjudicators = rankTypes(model, List.of("gameengine", "engine", "game", "adjudicator", "match", "ruleset", "psr"), false);
                List<TypeElement> ruleSets = rankTypes(model, List.of("ruleset", "rules", "rulebook"), false);
                List<TypeElement> mains = findMainTypes(model);

                List<ScoredBinding> out = new ArrayList<>();
                for (TypeElement sym : top(symbols, 3)) {
                    for (TypeElement adj : top(adjudicators, 4)) {
                        String evalMethod = findTwoArgEvalName(adj, sym, model);
                        if (evalMethod == null) {
                            continue;
                        }
                        TypeElement prov = providers.isEmpty() ? sym : providers.get(0);
                        TypeElement rs = ruleSets.isEmpty() ? null : ruleSets.get(0);
                        BindingEnv env = new BindingEnv()
                                .withType("SYMBOL_TYPE", sym)
                                .withType("SYMBOL_PROVIDER", prov)
                                .withType("ADJUDICATOR_TYPE", adj)
                                .withSymbol("ADJUDICATE_METHOD", evalMethod);
                        if (rs != null) {
                            env = env.withType("RULESET_TYPE", rs).withType("RULESET_PROVIDER", rs);
                            List<TypeElement> rsProvs = rankTypes(model, List.of("ruleset", "standard", "classic", "rules"), false);
                            for (TypeElement rp : rsProvs) {
                                if (hasStaticRuleSetFactory(rp, rs, model)) {
                                    env = env.withType("RULESET_PROVIDER", rp);
                                    break;
                                }
                            }
                        }
                        if (!mains.isEmpty()) {
                            env = env.withType("DEMO_MAIN", mains.get(0));
                        }
                        double score = 0.5 + (AstQueries.isInterfaceOrAbstract(sym) ? 0.25 : 0.0)
                                + (rs != null ? 0.2 : 0.0);
                        out.add(new ScoredBinding(env, Math.min(1.0, score),
                                "Symbol=" + sym.getSimpleName() + ", Adjudicator=" + adj.getSimpleName()
                                        + "." + evalMethod));
                    }
                }
                if (out.isEmpty()) {
                    BindingEnv fb = new BindingEnv();
                    if (!symbols.isEmpty()) fb = fb.withType("SYMBOL_TYPE", symbols.get(0));
                    if (!adjudicators.isEmpty()) fb = fb.withType("ADJUDICATOR_TYPE", adjudicators.get(0));
                    if (!mains.isEmpty()) fb = fb.withType("DEMO_MAIN", mains.get(0));
                    out.add(new ScoredBinding(fb, 0.2, "Fallback PSR binding"));
                }
                out.sort((a, b) -> Double.compare(b.priorScore(), a.priorScore()));
                return out;
            }

            @Override
            public List<Obligation> obligations() {
                return List.of(
                        new Obligation(
                                "hygiene-compile",
                                "HygieneAndCompile",
                                "STRATUM_1_HYGIENE",
                                "Zero build outputs (.class/.jar) and clean -Xlint:all javac compilation",
                                new HygieneAndCompileTactic(true),
                                true),
                        new Obligation(
                                "encapsulation",
                                "EncapsulationAndModularity",
                                "STRATUM_2_STRUCTURE",
                                "Strict private encapsulation and clean multi-class OOP abstraction (>= 5 types)",
                                new EncapsulationTactic(5),
                                false),
                        new Obligation(
                                "game-rules-exhaustive",
                                "GameRulesExhaustiveProof",
                                "STRATUM_3_BEHAVIOR",
                                "All 3x3 symbol pairs satisfy totality, draw reflexivity, antisymmetry, determinism, and PSR schema",
                                new GameRulesProofTactic(),
                                false),
                        new Obligation(
                                "runnable-demo",
                                "RunnableProgramExecution",
                                "STRATUM_3_BEHAVIOR",
                                "Runnable main program simulates two-player matches cleanly",
                                runnableMainTactic("DEMO_MAIN", 3),
                                false),
                        new Obligation(
                                "game-extensibility",
                                "GameExtensibility",
                                "STRATUM_4_EXTENSIBILITY",
                                "Open-Closed Principle: 5-symbol RPSLS extension compiles and passes 25-pair model check without editing engine",
                                ExtensibilityInjectionTactic.psrFiveMoveExtension(),
                                false)
                );
            }
        };
    }

    private static Tactic runnableMainTactic(String mainRole, int minOutputLines) {
        return (stepId, model, env) -> {
            TypeElement mainType = env.type(mainRole).orElse(null);
            Map<String, Object> evidence = new LinkedHashMap<>();
            if (mainType == null) {
                return new ProofStep(stepId, "RunnableProgramExecution", "STRATUM_3_BEHAVIOR",
                        "BuiltinSpecs.MainRunner", "Deliverable contains a runnable main(String[] args) entry point",
                        ProofStatus.REFUTED, "No public static void main(String[] args) entry point found.",
                        evidence, List.of("Missing main()"), List.of());
            }
            PrintStream origOut = System.out;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream capture = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
                System.setOut(capture);
                Class<?> cls = model.loadClass(mainType.getQualifiedName().toString());
                Method main = cls.getMethod("main", String[].class);
                main.invoke(null, (Object) new String[0]);
            } catch (Throwable t) {
                Throwable root = t.getCause() != null ? t.getCause() : t;
                return new ProofStep(stepId, "RunnableProgramExecution", "STRATUM_3_BEHAVIOR",
                        "BuiltinSpecs.MainRunner", "Deliverable contains a runnable main(String[] args) entry point",
                        ProofStatus.REFUTED, "main() threw " + root.getClass().getSimpleName() + ": " + root.getMessage(),
                        evidence, List.of(root.toString()), List.of());
            } finally {
                System.setOut(origOut);
            }
            String output = baos.toString(StandardCharsets.UTF_8).trim();
            String[] lines = output.isEmpty() ? new String[0] : output.split("\\R");
            evidence.put("mainClass", mainType.getQualifiedName().toString());
            evidence.put("outputLineCount", lines.length);
            if (lines.length < minOutputLines) {
                return new ProofStep(stepId, "RunnableProgramExecution", "STRATUM_3_BEHAVIOR",
                        "BuiltinSpecs.MainRunner", "Deliverable contains a runnable main(String[] args) entry point",
                        ProofStatus.REFUTED,
                        "main() produced only " + lines.length + " line(s) of output (expected >= " + minOutputLines + ").",
                        evidence, List.of(output), List.of());
            }
            return new ProofStep(stepId, "RunnableProgramExecution", "STRATUM_3_BEHAVIOR",
                    "BuiltinSpecs.MainRunner", "Deliverable contains a runnable main(String[] args) entry point",
                    ProofStatus.QED,
                    "Executed " + mainType.getSimpleName() + ".main() cleanly (" + lines.length + " output lines).",
                    evidence, List.of("mainClass=" + mainType.getQualifiedName() + ", lines=" + lines.length), List.of());
        };
    }

    private static List<TypeElement> rankTypes(
            WorkspaceAnalyzer.CodebaseModel model, List<String> keywords, boolean preferAbstractOrInterface) {
        List<Map.Entry<TypeElement, Double>> scored = new ArrayList<>();
        for (TypeElement te : model.declaredTypes()) {
            String name = te.getSimpleName().toString().toLowerCase(Locale.ROOT);
            double s = 0.0;
            for (int i = 0; i < keywords.size(); i++) {
                String kw = keywords.get(i);
                if (name.equals(kw)) {
                    s += 1.0 - i * 0.05;
                    break;
                } else if (name.contains(kw)) {
                    s += 0.65 - i * 0.05;
                    break;
                }
            }
            if (preferAbstractOrInterface && AstQueries.isInterfaceOrAbstract(te)) {
                s += 0.3;
            }
            if (s > 0.0) {
                scored.add(Map.entry(te, s));
            }
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<TypeElement> out = new ArrayList<>();
        for (var e : scored) out.add(e.getKey());
        return out;
    }

    private static List<TypeElement> findMainTypes(WorkspaceAnalyzer.CodebaseModel model) {
        List<TypeElement> out = new ArrayList<>();
        for (TypeElement te : model.declaredTypes()) {
            for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
                if (m.getSimpleName().contentEquals("main")
                        && m.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC)
                        && m.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)
                        && m.getParameters().size() == 1) {
                    out.add(te);
                    break;
                }
            }
        }
        return out;
    }

    private static String findAcceptName(
            TypeElement base, TypeElement vis, WorkspaceAnalyzer.CodebaseModel model) {
        var methods = AstQueries.findAcceptMethods(model, base, vis);
        return methods.isEmpty() ? "accept" : methods.get(0).getSimpleName().toString();
    }

    private static String findTwoArgEvalName(
            TypeElement adj, TypeElement sym, WorkspaceAnalyzer.CodebaseModel model) {
        for (ExecutableElement m : ElementFilter.methodsIn(adj.getEnclosedElements())) {
            if (m.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC)
                    && m.getParameters().size() == 2
                    && model.types().isAssignable(
                            model.types().erasure(sym.asType()),
                            model.types().erasure(m.getParameters().get(0).asType()))
                    && model.types().isAssignable(
                            model.types().erasure(sym.asType()),
                            model.types().erasure(m.getParameters().get(1).asType()))) {
                return m.getSimpleName().toString();
            }
        }
        return null;
    }

    private static boolean hasStaticRuleSetFactory(
            TypeElement provider, TypeElement ruleSet, WorkspaceAnalyzer.CodebaseModel model) {
        for (ExecutableElement m : ElementFilter.methodsIn(provider.getEnclosedElements())) {
            if (m.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC)
                    && m.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)
                    && m.getParameters().isEmpty()
                    && model.types().isAssignable(
                            model.types().erasure(m.getReturnType()),
                            model.types().erasure(ruleSet.asType()))) {
                return true;
            }
        }
        for (var f : ElementFilter.fieldsIn(provider.getEnclosedElements())) {
            if (f.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC)
                    && f.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)
                    && model.types().isAssignable(
                            model.types().erasure(f.asType()),
                            model.types().erasure(ruleSet.asType()))) {
                return true;
            }
        }
        return false;
    }

    private static double computeVisitorPrior(
            WorkspaceAnalyzer.CodebaseModel model,
            TypeElement base, TypeElement vis, TypeElement av,
            TypeElement c, TypeElement r, TypeElement t) {
        double score = 0.2;
        if (AstQueries.isInterfaceOrAbstract(base)) score += 0.15;
        if (AstQueries.isInterfaceOrAbstract(vis)) score += 0.15;
        if (AstQueries.isSubtype(model, av, vis)) score += 0.15;
        if (AstQueries.isSubtype(model, c, base)) score += 0.1;
        if (AstQueries.isSubtype(model, r, base)) score += 0.1;
        if (AstQueries.isSubtype(model, t, base)) score += 0.15;
        return Math.min(1.0, score);
    }

    private static <T> List<T> top(List<T> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }
}
