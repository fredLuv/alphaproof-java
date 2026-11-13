package dev.oopproof.tactics;

import dev.oopproof.core.AstQueries;
import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.RuntimeBridge;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Meta-compilation Open-Closed Principle prover.
 * Synthesizes new Java extension code (e.g., PerimeterVisitor or a 5-symbol RPSLS
 * ruleset), compiles it against the candidate's public API in isolation without
 * modifying any candidate file, and verifies the extended behavior at runtime.
 */
public final class ExtensibilityInjectionTactic {

    private ExtensibilityInjectionTactic() {}

    public static Tactic visitorPerimeterExtension() {
        return (stepId, model, env) -> {
            TypeElement visitorBase = env.type("VISITOR_BASE").orElse(null);
            TypeElement circleEl = env.type("CIRCLE").orElse(null);
            TypeElement rectEl = env.type("RECTANGLE").orElse(null);
            TypeElement triEl = env.type("TRIANGLE").orElse(null);
            String acceptName = env.symbol("ACCEPT_METHOD").orElse("accept");

            Map<String, Object> evidence = new LinkedHashMap<>();
            if (visitorBase == null || circleEl == null || rectEl == null || triEl == null) {
                return refuted(stepId, "VisitorExtensibility", "Open-Closed extensibility via synthetic PerimeterVisitor",
                        evidence, List.of("Missing visitor or shape bindings"),
                        "Cannot synthesize PerimeterVisitor without bound visitor and shape types.");
            }

            try {
                boolean genericVisitor = !visitorBase.getTypeParameters().isEmpty();
                String visitorImplHeader = genericVisitor
                        ? visitorBase.getQualifiedName() + "<Double>"
                        : visitorBase.getQualifiedName().toString();
                String implementsClause = visitorBase.getKind() == ElementKind.INTERFACE
                        ? "implements " + visitorImplHeader
                        : "extends " + visitorImplHeader;

                String circleGetter = findSingleNumericGetter(circleEl, List.of("radius", "getRadius", "r"));
                List<String> rectGetters = findTwoNumericGetters(rectEl, List.of("width", "getWidth", "w"), List.of("height", "getHeight", "h"));
                List<String> triGetters = findThreeNumericGetters(triEl);

                if (circleGetter == null || rectGetters.size() != 2 || triGetters.size() != 3) {
                    return refuted(stepId, "VisitorExtensibility", "Open-Closed extensibility via synthetic PerimeterVisitor",
                            evidence, List.of("Could not discover dimension accessors on Circle/Rectangle/Triangle"),
                            "Shapes do not expose clean public dimension accessors for external visitors.");
                }

                StringBuilder methods = new StringBuilder();
                for (ExecutableElement m : ElementFilter.methodsIn(visitorBase.getEnclosedElements())) {
                    if (!m.getModifiers().contains(Modifier.ABSTRACT) && visitorBase.getKind() != ElementKind.INTERFACE) {
                        continue;
                    }
                    if (m.getParameters().size() != 1) {
                        continue;
                    }
                    TypeMirror pType = m.getParameters().get(0).asType();
                    String pName = model.types().erasure(pType).toString();
                    String retType = resolveVisitorReturnType(m, genericVisitor);
                    if ("void".equals(retType)) {
                        return refuted(stepId, "VisitorExtensibility", "Open-Closed extensibility via synthetic PerimeterVisitor",
                                evidence, List.of("Void-returning visitor interface is less composable; expected value-returning visitor"),
                                "Visitor interface returns void.");
                    }

                    String bodyExpr;
                    if (pName.equals(circleEl.getQualifiedName().toString())) {
                        bodyExpr = "2.0 * Math.PI * s." + circleGetter + "()";
                    } else if (pName.equals(rectEl.getQualifiedName().toString())) {
                        bodyExpr = "2.0 * (s." + rectGetters.get(0) + "() + s." + rectGetters.get(1) + "())";
                    } else if (pName.equals(triEl.getQualifiedName().toString())) {
                        bodyExpr = "s." + triGetters.get(0) + "() + s." + triGetters.get(1) + "() + s." + triGetters.get(2) + "()";
                    } else {
                        continue;
                    }

                    methods.append("  @Override public ").append(retType).append(' ')
                            .append(m.getSimpleName()).append('(').append(pName).append(" s) {\n")
                            .append("    return ").append(bodyExpr).append(";\n")
                            .append("  }\n");
                }

                String src = "public final class SyntheticPerimeterVisitor " + implementsClause + " {\n"
                        + methods
                        + "}\n";

                RuntimeBridge.ExtensionCompileResult comp =
                        RuntimeBridge.compileSyntheticExtension(model, "SyntheticPerimeterVisitor", src);
                try {
                    if (!comp.success()) {
                        return refuted(stepId, "VisitorExtensibility", "Open-Closed extensibility via synthetic PerimeterVisitor",
                                evidence, comp.diagnostics(),
                                "SyntheticPerimeterVisitor failed to compile against candidate API.");
                    }

                    try (URLClassLoader extCl = RuntimeBridge.createExtensionClassLoader(model, comp.extensionClassesDir())) {
                        Class<?> pvCls = Class.forName("SyntheticPerimeterVisitor", true, extCl);
                        Object pv = pvCls.getConstructor().newInstance();
                        Class<?> cCls = Class.forName(circleEl.getQualifiedName().toString(), true, extCl);
                        Class<?> rCls = Class.forName(rectEl.getQualifiedName().toString(), true, extCl);
                        Class<?> tCls = Class.forName(triEl.getQualifiedName().toString(), true, extCl);

                        double cp = ((Number) RuntimeBridge.invokeAccept(RuntimeBridge.instantiateNumeric(cCls, 3.0), pv, acceptName)).doubleValue();
                        double rp = ((Number) RuntimeBridge.invokeAccept(RuntimeBridge.instantiateNumeric(rCls, 4.0, 5.0), pv, acceptName)).doubleValue();
                        double tp = ((Number) RuntimeBridge.invokeAccept(RuntimeBridge.instantiateNumeric(tCls, 3.0, 4.0, 5.0), pv, acceptName)).doubleValue();

                        if (Math.abs(cp - 6.0 * Math.PI) > 1e-7 || Math.abs(rp - 18.0) > 1e-7 || Math.abs(tp - 12.0) > 1e-7) {
                            return refuted(stepId, "VisitorExtensibility", "Open-Closed extensibility via synthetic PerimeterVisitor",
                                    evidence, List.of("cp=" + cp + ", rp=" + rp + ", tp=" + tp),
                                    "SyntheticPerimeterVisitor execution produced unexpected perimeter values.");
                        }

                        evidence.put("syntheticClass", "SyntheticPerimeterVisitor");
                        evidence.put("compiledAgainstCandidate", true);
                        evidence.put("circle3Perimeter", cp);
                        evidence.put("rect4x5Perimeter", rp);
                        evidence.put("tri345Perimeter", tp);

                        return new ProofStep(
                                stepId,
                                "VisitorExtensibility",
                                "STRATUM_4_EXTENSIBILITY",
                                "ExtensibilityInjectionTactic",
                                "New operations (SyntheticPerimeterVisitor) compile and execute without modifying candidate code",
                                ProofStatus.QED,
                                "Synthesized, compiled, and executed SyntheticPerimeterVisitor against candidate Shape API with zero edits to candidate source.",
                                evidence,
                                List.of("SyntheticPerimeterVisitor compiled cleanly and verified Circle(3)=18.8496, Rect(4,5)=18.0, Tri(3,4,5)=12.0"),
                                List.of());
                    }
                } finally {
                    RuntimeBridge.deleteTempDir(comp.extensionClassesDir());
                }
            } catch (Throwable t) {
                return refuted(stepId, "VisitorExtensibility", "Open-Closed extensibility via synthetic PerimeterVisitor",
                        evidence, List.of(t.getClass().getName() + ": " + t.getMessage()),
                        "Extensibility proof failed: " + t.getMessage());
            }
        };
    }

    public static Tactic psrFiveMoveExtension() {
        return (stepId, model, env) -> {
            TypeElement symbolType = env.type("SYMBOL_TYPE").orElse(null);
            TypeElement adjudicatorType = env.type("ADJUDICATOR_TYPE").orElse(null);
            TypeElement ruleSetType = env.type("RULESET_TYPE").orElse(null);
            String adjudicateMethod = env.symbol("ADJUDICATE_METHOD").orElse(null);

            Map<String, Object> evidence = new LinkedHashMap<>();
            if (symbolType == null || adjudicatorType == null) {
                return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                        evidence, List.of("Unbound SYMBOL_TYPE or ADJUDICATOR_TYPE"),
                        "Cannot test extensibility without bound symbol and adjudicator types.");
            }

            // 1. Check if symbolType is a closed enum AND adjudicator switches directly on it
            if (symbolType.getKind() == ElementKind.ENUM) {
                List<AstQueries.ViolationLocation> switches = AstQueries.findSwitchOnTypesOrEnums(model, symbolType);
                if (!switches.isEmpty() || ruleSetType == null) {
                    List<String> w = new ArrayList<>();
                    w.add("SYMBOL_TYPE '" + symbolType.getQualifiedName() + "' is a closed enum (cannot be extended externally without editing source)");
                    for (AstQueries.ViolationLocation v : switches) {
                        w.add(v.kind() + " @ " + v.location() + ": " + v.detail());
                    }
                    return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                            evidence, w,
                            "Closed enum symbol + hardcoded switch cannot be generalized to new symbols (e.g. Lizard/Spock) without modifying engine code.");
                }
            }

            // 2. Synthesize a 5-symbol Rock-Paper-Scissors-Lizard-Spock ruleset against the candidate API!
            if (ruleSetType == null) {
                return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                        evidence, List.of("No separate RuleSet abstraction discovered"),
                        "No separate RuleSet abstraction found to inject a 5-move extension.");
            }

            try {
                String symFqn = symbolType.getQualifiedName().toString();
                String adjFqn = adjudicatorType.getQualifiedName().toString();
                String rsFqn = ruleSetType.getQualifiedName().toString();

                String probeSource = buildRpslsProbeSource(model, symbolType, adjudicatorType, ruleSetType, adjudicateMethod);
                if (probeSource == null) {
                    return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                            evidence, List.of("Could not discover an external builder/constructor on " + rsFqn + " and " + symFqn),
                            "RuleSet/Symbol API does not expose an external construction path for adding new symbols.");
                }

                RuntimeBridge.ExtensionCompileResult comp =
                        RuntimeBridge.compileSyntheticExtension(model, "SyntheticRpslsProbe", probeSource);
                try {
                    if (!comp.success()) {
                        return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                                evidence, comp.diagnostics(),
                                "Synthetic 5-symbol RPSLS extension failed to compile against candidate API.");
                    }

                    try (URLClassLoader extCl = RuntimeBridge.createExtensionClassLoader(model, comp.extensionClassesDir())) {
                        Class<?> probeCls = Class.forName("SyntheticRpslsProbe", true, extCl);
                        Method run = probeCls.getMethod("verify25Pairs");
                        @SuppressWarnings("unchecked")
                        List<String> errors = (List<String>) run.invoke(null);
                        if (!errors.isEmpty()) {
                            return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                                    evidence, errors,
                                    "5-symbol RPSLS extension failed behavioral invariants.");
                        }

                        evidence.put("symbolBase", symFqn);
                        evidence.put("ruleSetType", rsFqn);
                        evidence.put("adjudicatorType", adjFqn);
                        evidence.put("rpslsPairsVerified", 25);

                        return new ProofStep(
                                stepId,
                                "GameExtensibility",
                                "STRATUM_4_EXTENSIBILITY",
                                "ExtensibilityInjectionTactic",
                                "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                                ProofStatus.QED,
                                "Synthesized 5-symbol Rock-Paper-Scissors-Lizard-Spock ruleset externally, compiled against candidate API, and verified all 25 pairs.",
                                evidence,
                                List.of("25/25 RPSLS pairs satisfied totality, draw reflexivity, zero-sum antisymmetry, and 10 beat rules"),
                                List.of());
                    }
                } finally {
                    RuntimeBridge.deleteTempDir(comp.extensionClassesDir());
                }
            } catch (Throwable t) {
                return refuted(stepId, "GameExtensibility", "Game engine generalizes to 5-symbol RPSLS without editing engine code",
                        evidence, List.of(t.getClass().getName() + ": " + t.getMessage()),
                        "PSR extensibility proof failed: " + t.getMessage());
            }
        };
    }

    private static String buildRpslsProbeSource(
            WorkspaceAnalyzer.CodebaseModel model,
            TypeElement symbolType,
            TypeElement adjudicatorType,
            TypeElement ruleSetType,
            String adjudicateMethodName) {
        String symFqn = symbolType.getQualifiedName().toString();
        String adjFqn = adjudicatorType.getQualifiedName().toString();
        String rsFqn = ruleSetType.getQualifiedName().toString();

        // Check how to construct a Symbol (e.g., interface with id()/displayName(), or class(String) / static of(String))
        String symDecl;
        if (symbolType.getKind() == ElementKind.INTERFACE) {
            // Check abstract methods on symbol interface
            List<ExecutableElement> absMethods = new ArrayList<>();
            for (ExecutableElement m : ElementFilter.methodsIn(symbolType.getEnclosedElements())) {
                if (m.getModifiers().contains(Modifier.ABSTRACT)) {
                    absMethods.add(m);
                }
            }
            if (absMethods.size() > 2) {
                return null;
            }
            StringBuilder implMethods = new StringBuilder();
            for (ExecutableElement m : absMethods) {
                if (m.getReturnType().toString().equals("java.lang.String") && m.getParameters().isEmpty()) {
                    implMethods.append("    @Override public String ").append(m.getSimpleName()).append("() { return id; }\n");
                } else {
                    return null;
                }
            }
            symDecl = "  static final class CustomSym implements " + symFqn + " {\n"
                    + "    private final String id;\n"
                    + "    CustomSym(String id) { this.id = id; }\n"
                    + implMethods
                    + "    @Override public String toString() { return id; }\n"
                    + "    @Override public boolean equals(Object o) { return (o instanceof CustomSym c) && id.equals(c.id); }\n"
                    + "    @Override public int hashCode() { return id.hashCode(); }\n"
                    + "  }\n"
                    + "  private static " + symFqn + " sym(String n) { return new CustomSym(n); }\n";
        } else if (!symbolType.getModifiers().contains(Modifier.FINAL) || hasStringConstructorOrOf(symbolType)) {
            if (hasStringConstructorOrOf(symbolType)) {
                boolean hasOf = hasStaticFactoryString(symbolType);
                symDecl = hasOf
                        ? "  private static " + symFqn + " sym(String n) { return " + symFqn + ".of(n); }\n"
                        : "  private static " + symFqn + " sym(String n) { return new " + symFqn + "(n); }\n";
            } else {
                return null;
            }
        } else {
            return null;
        }

        // Discover RuleSet builder pattern: e.g. RuleSet.builder() with .addRule(winner, loser, verb) or .beats(winner, loser)
        String builderInit = discoverRuleSetBuilderCode(model, ruleSetType, symFqn);
        if (builderInit == null) {
            return null;
        }

        String evalCall = adjudicateMethodName != null ? adjudicateMethodName : "playRound";
        // Verify adjudicator has constructor accepting ruleSetType
        boolean hasRuleSetCtor = false;
        for (ExecutableElement c : ElementFilter.constructorsIn(adjudicatorType.getEnclosedElements())) {
            if (c.getParameters().size() == 1
                    && model.types().isAssignable(
                            model.types().erasure(ruleSetType.asType()),
                            model.types().erasure(c.getParameters().get(0).asType()))) {
                hasRuleSetCtor = true;
                break;
            }
        }
        if (!hasRuleSetCtor) {
            return null;
        }

        return "import java.util.*;\n"
                + "public final class SyntheticRpslsProbe {\n"
                + symDecl
                + "  public static List<String> verify25Pairs() {\n"
                + "    List<String> errors = new ArrayList<>();\n"
                + "    " + symFqn + " rock = sym(\"ROCK\");\n"
                + "    " + symFqn + " paper = sym(\"PAPER\");\n"
                + "    " + symFqn + " scissors = sym(\"SCISSORS\");\n"
                + "    " + symFqn + " lizard = sym(\"LIZARD\");\n"
                + "    " + symFqn + " spock = sym(\"SPOCK\");\n"
                + builderInit
                + "    " + adjFqn + " engine = new " + adjFqn + "(rules);\n"
                + "    List<" + symFqn + "> all = List.of(rock, paper, scissors, lizard, spock);\n"
                + "    int[][] wins = {\n"
                + "      {0,0}, {1,0}, {0,2}, {0,3}, {4,0},\n"
                + "      {1,4}, {2,1}, {2,3}, {3,4}, {3,1},\n"
                + "      {4,2}\n"
                + "    };\n"
                + "    for (int i = 0; i < 5; i++) {\n"
                + "      String d = String.valueOf(engine." + evalCall + "(all.get(i), all.get(i))).toUpperCase(Locale.ROOT);\n"
                + "      if (!d.contains(\"DRAW\") && !d.contains(\"TIE\")) errors.add(\"Self-play not draw: \" + i + \" -> \" + d);\n"
                + "    }\n"
                + "    int[][] rulesIdx = {{1,0},{0,2},{0,3},{4,0},{1,4},{2,1},{2,3},{3,4},{3,1},{4,2}};\n"
                + "    for (int[] pair : rulesIdx) {\n"
                + "      String w = String.valueOf(engine." + evalCall + "(all.get(pair[0]), all.get(pair[1]))).toUpperCase(Locale.ROOT);\n"
                + "      String l = String.valueOf(engine." + evalCall + "(all.get(pair[1]), all.get(pair[0]))).toUpperCase(Locale.ROOT);\n"
                + "      if (!isP1Win(w)) errors.add(\"Expected P1 win for \" + pair[0] + \" vs \" + pair[1] + \", got \" + w);\n"
                + "      if (!isP2Win(l)) errors.add(\"Expected P2 win for \" + pair[1] + \" vs \" + pair[0] + \", got \" + l);\n"
                + "    }\n"
                + "    return errors;\n"
                + "  }\n"
                + "  private static boolean isP1Win(String s) {\n"
                + "    return (s.contains(\"PLAYER_ONE\") || s.contains(\"PLAYER1\") || s.contains(\"P1\") || s.contains(\"WIN\")) && !isP2Win(s) && !s.contains(\"DRAW\");\n"
                + "  }\n"
                + "  private static boolean isP2Win(String s) {\n"
                + "    return s.contains(\"PLAYER_TWO\") || s.contains(\"PLAYER2\") || s.contains(\"P2\") || s.contains(\"LOSE\") || s.contains(\"LOSS\");\n"
                + "  }\n"
                + "}\n";
    }

    private static boolean hasStringConstructorOrOf(TypeElement te) {
        for (ExecutableElement c : ElementFilter.constructorsIn(te.getEnclosedElements())) {
            if (c.getModifiers().contains(Modifier.PUBLIC) && c.getParameters().size() == 1
                    && c.getParameters().get(0).asType().toString().equals("java.lang.String")) {
                return true;
            }
        }
        return hasStaticFactoryString(te);
    }

    private static boolean hasStaticFactoryString(TypeElement te) {
        for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
            if (m.getModifiers().contains(Modifier.PUBLIC) && m.getModifiers().contains(Modifier.STATIC)
                    && m.getSimpleName().contentEquals("of")
                    && m.getParameters().size() == 1
                    && m.getParameters().get(0).asType().toString().equals("java.lang.String")) {
                return true;
            }
        }
        return false;
    }

    private static String discoverRuleSetBuilderCode(
            WorkspaceAnalyzer.CodebaseModel model, TypeElement ruleSetType, String symFqn) {
        String rsFqn = ruleSetType.getQualifiedName().toString();
        // Look for static builder() returning a Builder with addRule(S, S, String) or beats(S, S, String) or beats(S, S)
        for (ExecutableElement m : ElementFilter.methodsIn(ruleSetType.getEnclosedElements())) {
            if (m.getModifiers().contains(Modifier.PUBLIC) && m.getModifiers().contains(Modifier.STATIC)
                    && m.getSimpleName().contentEquals("builder") && m.getParameters().isEmpty()) {
                TypeElement builderType = (TypeElement) model.types().asElement(m.getReturnType());
                if (builderType != null) {
                    String addMethod = null;
                    int arity = 0;
                    String buildMethod = null;
                    for (ExecutableElement bm : ElementFilter.methodsIn(builderType.getEnclosedElements())) {
                        if (!bm.getModifiers().contains(Modifier.PUBLIC)) continue;
                        if (bm.getParameters().isEmpty() && bm.getSimpleName().contentEquals("build")) {
                            buildMethod = "build";
                        }
                        if (bm.getParameters().size() == 3
                                && model.types().isSameType(model.types().erasure(bm.getParameters().get(0).asType()),
                                        model.types().erasure(model.elements().getTypeElement(symFqn).asType()))) {
                            addMethod = bm.getSimpleName().toString();
                            arity = 3;
                        } else if (addMethod == null && bm.getParameters().size() == 2
                                && model.types().isSameType(model.types().erasure(bm.getParameters().get(0).asType()),
                                        model.types().erasure(model.elements().getTypeElement(symFqn).asType()))) {
                            addMethod = bm.getSimpleName().toString();
                            arity = 2;
                        }
                    }
                    if (addMethod != null && buildMethod != null) {
                        String[][] edges = {
                            {"paper", "rock", "wraps"}, {"rock", "scissors", "blunts"},
                            {"rock", "lizard", "crushes"}, {"spock", "rock", "vaporizes"},
                            {"paper", "spock", "disproves"}, {"scissors", "paper", "cuts"},
                            {"scissors", "lizard", "decapitates"}, {"lizard", "spock", "poisons"},
                            {"lizard", "paper", "eats"}, {"spock", "scissors", "smashes"}
                        };
                        StringBuilder sb = new StringBuilder();
                        sb.append("    ").append(rsFqn).append(" rules = ").append(rsFqn).append(".builder()\n");
                        for (String[] e : edges) {
                            if (arity == 3) {
                                sb.append("      .").append(addMethod).append('(').append(e[0]).append(", ").append(e[1])
                                        .append(", \"").append(e[2]).append("\")\n");
                            } else {
                                sb.append("      .").append(addMethod).append('(').append(e[0]).append(", ").append(e[1]).append(")\n");
                            }
                        }
                        sb.append("      .").append(buildMethod).append("();\n");
                        return sb.toString();
                    }
                }
            }
        }
        return null;
    }

    private static String resolveVisitorReturnType(ExecutableElement m, boolean genericVisitor) {
        TypeMirror rt = m.getReturnType();
        if (rt.getKind() == TypeKind.VOID) {
            return "void";
        }
        if (rt.getKind() == TypeKind.DOUBLE) {
            return "double";
        }
        if (genericVisitor || rt.getKind() == TypeKind.TYPEVAR || rt.toString().equals("java.lang.Double")) {
            return "Double";
        }
        return rt.toString();
    }

    private static String findSingleNumericGetter(TypeElement te, List<String> preferred) {
        for (String name : preferred) {
            for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
                if (m.getModifiers().contains(Modifier.PUBLIC) && m.getParameters().isEmpty()
                        && isNumeric(m.getReturnType()) && m.getSimpleName().contentEquals(name)) {
                    return name;
                }
            }
        }
        for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
            if (m.getModifiers().contains(Modifier.PUBLIC) && m.getParameters().isEmpty()
                    && isNumeric(m.getReturnType()) && !m.getSimpleName().contentEquals("hashCode")) {
                return m.getSimpleName().toString();
            }
        }
        return null;
    }

    private static List<String> findTwoNumericGetters(TypeElement te, List<String> first, List<String> second) {
        String g1 = findMatchingGetter(te, first);
        String g2 = findMatchingGetter(te, second);
        if (g1 != null && g2 != null && !g1.equals(g2)) {
            return List.of(g1, g2);
        }
        return collectAllNumericGetters(te, 2);
    }

    private static List<String> findThreeNumericGetters(TypeElement te) {
        List<String> preferred = new ArrayList<>();
        for (String n : List.of("sideA", "getSideA", "a", "side1", "sideB", "getSideB", "b", "side2", "sideC", "getSideC", "c", "side3")) {
            String found = findMatchingGetter(te, List.of(n));
            if (found != null && !preferred.contains(found)) {
                preferred.add(found);
            }
        }
        if (preferred.size() == 3) {
            return preferred;
        }
        return collectAllNumericGetters(te, 3);
    }

    private static String findMatchingGetter(TypeElement te, List<String> candidates) {
        for (String c : candidates) {
            for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
                if (m.getModifiers().contains(Modifier.PUBLIC) && m.getParameters().isEmpty()
                        && isNumeric(m.getReturnType()) && m.getSimpleName().contentEquals(c)) {
                    return c;
                }
            }
        }
        return null;
    }

    private static List<String> collectAllNumericGetters(TypeElement te, int count) {
        List<String> getters = new ArrayList<>();
        for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
            if (m.getModifiers().contains(Modifier.PUBLIC) && m.getParameters().isEmpty()
                    && isNumeric(m.getReturnType()) && !m.getSimpleName().contentEquals("hashCode")) {
                getters.add(m.getSimpleName().toString());
                if (getters.size() == count) {
                    return getters;
                }
            }
        }
        return getters;
    }

    private static boolean isNumeric(TypeMirror tm) {
        TypeKind k = tm.getKind();
        if (k == TypeKind.DOUBLE || k == TypeKind.FLOAT || k == TypeKind.INT || k == TypeKind.LONG) {
            return true;
        }
        String s = tm.toString();
        return s.equals("java.lang.Double") || s.equals("java.lang.Integer") || s.equals("java.lang.Long");
    }

    private static ProofStep refuted(
            String stepId, String lemma, String prop, Map<String, Object> evidence, List<String> witnesses, String summary) {
        return new ProofStep(
                stepId, lemma, "STRATUM_4_EXTENSIBILITY", "ExtensibilityInjectionTactic",
                prop, ProofStatus.REFUTED, summary, evidence, witnesses, List.of());
    }
}
