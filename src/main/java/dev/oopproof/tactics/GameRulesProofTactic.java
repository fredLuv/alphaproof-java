package dev.oopproof.tactics;

import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.RuntimeBridge;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.element.TypeElement;

/**
 * Exhaustive finite-domain model checker for 2-player Paper-Scissors-Rock:
 * verifies all 9 (m1, m2) pairs for totality, draw reflexivity, zero-sum
 * antisymmetry, determinism, and exact dominance schema (paper>rock>scissors>paper).
 */
public final class GameRulesProofTactic implements Tactic {

    public enum CanonicalOutcome { P1_WIN, P2_WIN, DRAW }

    @Override
    public ProofStep apply(String stepId, WorkspaceAnalyzer.CodebaseModel model, BindingEnv env) {
        TypeElement symbolType = env.type("SYMBOL_TYPE").orElse(null);
        TypeElement symbolProvider = env.type("SYMBOL_PROVIDER").orElse(symbolType);
        TypeElement adjudicatorType = env.type("ADJUDICATOR_TYPE").orElse(null);
        TypeElement ruleSetFactory = env.type("RULESET_PROVIDER").orElse(null);
        String adjudicateMethod = env.symbol("ADJUDICATE_METHOD").orElse(null);

        Map<String, Object> evidence = new LinkedHashMap<>();
        List<String> witnesses = new ArrayList<>();

        if (symbolType == null || adjudicatorType == null) {
            return refuted(stepId, evidence, List.of("Unbound SYMBOL_TYPE or ADJUDICATOR_TYPE"),
                    "Cannot model-check Paper-Scissors-Rock without bound symbol and adjudicator types.");
        }

        try {
            Class<?> symCls = model.loadClass(symbolType.getQualifiedName().toString());
            Class<?> provCls = symbolProvider != null
                    ? model.loadClass(symbolProvider.getQualifiedName().toString())
                    : symCls;
            Class<?> adjCls = model.loadClass(adjudicatorType.getQualifiedName().toString());

            Object rock = resolveSymbol(symCls, provCls, "ROCK");
            Object paper = resolveSymbol(symCls, provCls, "PAPER");
            Object scissors = resolveSymbol(symCls, provCls, "SCISSORS");
            if (rock == null || paper == null || scissors == null) {
                return refuted(stepId, evidence,
                        List.of("rock=" + rock + ", paper=" + paper + ", scissors=" + scissors),
                        "Could not resolve ROCK, PAPER, and SCISSORS symbol instances.");
            }

            Object adjudicator = instantiateAdjudicator(adjCls, ruleSetFactory, model);
            Method evalMethod = findEvalMethod(adjCls, symCls, adjudicateMethod);
            if (evalMethod == null) {
                return refuted(stepId, evidence,
                        List.of("No 2-symbol evaluation method found on " + adjCls.getName()),
                        "No method accepting two " + symCls.getSimpleName() + " instances found on " + adjCls.getSimpleName());
            }

            List<Object> moves = List.of(rock, paper, scissors);
            List<String> names = List.of("ROCK", "PAPER", "SCISSORS");
            CanonicalOutcome[][] matrix = new CanonicalOutcome[3][3];

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    Object raw = invokeEval(evalMethod, adjudicator, moves.get(i), moves.get(j));
                    CanonicalOutcome co = normalizeOutcome(raw, moves.get(i), moves.get(j));
                    if (co == null) {
                        return refuted(stepId, evidence,
                                List.of("Pair (" + names.get(i) + ", " + names.get(j) + ") returned unrecognized outcome: " + raw),
                                "Totality failed on (" + names.get(i) + ", " + names.get(j) + ").");
                    }
                    // Determinism check
                    for (int rep = 0; rep < 3; rep++) {
                        Object again = invokeEval(evalMethod, adjudicator, moves.get(i), moves.get(j));
                        if (normalizeOutcome(again, moves.get(i), moves.get(j)) != co) {
                            return refuted(stepId, evidence,
                                    List.of("Non-deterministic outcome on (" + names.get(i) + ", " + names.get(j) + ")"),
                                    "Determinism violated.");
                        }
                    }
                    matrix[i][j] = co;
                }
            }

            // Axiom 1: Reflexivity of Draw
            for (int i = 0; i < 3; i++) {
                if (matrix[i][i] != CanonicalOutcome.DRAW) {
                    return refuted(stepId, evidence,
                            List.of("Self-play (" + names.get(i) + ", " + names.get(i) + ") -> " + matrix[i][i] + " (expected DRAW)"),
                            "Draw reflexivity violated on " + names.get(i));
                }
            }

            // Axiom 2: Zero-sum Antisymmetry
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (matrix[i][j] == CanonicalOutcome.P1_WIN && matrix[j][i] != CanonicalOutcome.P2_WIN) {
                        return refuted(stepId, evidence,
                                List.of("Antisymmetry violated: (" + names.get(i) + "," + names.get(j) + ")=" + matrix[i][j]
                                        + " vs (" + names.get(j) + "," + names.get(i) + ")=" + matrix[j][i]),
                                "Zero-sum antisymmetry violated.");
                    }
                    if (matrix[i][j] == CanonicalOutcome.P2_WIN && matrix[j][i] != CanonicalOutcome.P1_WIN) {
                        return refuted(stepId, evidence,
                                List.of("Antisymmetry violated on (" + names.get(i) + "," + names.get(j) + ")"),
                                "Zero-sum antisymmetry violated.");
                    }
                }
            }

            // Axiom 3: Exact dominance schema
            // 0=ROCK, 1=PAPER, 2=SCISSORS
            if (matrix[1][0] != CanonicalOutcome.P1_WIN
                    || matrix[0][2] != CanonicalOutcome.P1_WIN
                    || matrix[2][1] != CanonicalOutcome.P1_WIN) {
                return refuted(stepId, evidence,
                        List.of("PAPER vs ROCK=" + matrix[1][0]
                                + ", ROCK vs SCISSORS=" + matrix[0][2]
                                + ", SCISSORS vs PAPER=" + matrix[2][1]),
                        "Game outcome violates Paper-Scissors-Rock dominance schema.");
            }

            evidence.put("adjudicator", adjCls.getName() + "." + evalMethod.getName());
            evidence.put("pairsChecked", 9);
            evidence.put("reflexivity", "QED");
            evidence.put("antisymmetry", "QED");
            evidence.put("determinism", "QED");
            witnesses.add("All 9 pairs exhaustive: PAPER>ROCK=" + matrix[1][0]
                    + ", ROCK>SCISSORS=" + matrix[0][2] + ", SCISSORS>PAPER=" + matrix[2][1]);
            witnesses.add("Reflexivity(m,m)=DRAW and Antisymmetry(m1,m2)=-Antisymmetry(m2,m1) verified for all 9 pairs");

            return new ProofStep(
                    stepId,
                    "GameRulesExhaustiveProof",
                    "STRATUM_3_BEHAVIOR",
                    "GameRulesProofTactic",
                    "All 3x3 symbol pairs satisfy totality, draw reflexivity, antisymmetry, determinism, and PSR schema",
                    ProofStatus.QED,
                    "Exhaustively proved all 9 (m1, m2) pairs satisfy totality, reflexivity, antisymmetry, determinism, and PSR dominance rules.",
                    evidence,
                    witnesses,
                    List.of());
        } catch (Throwable t) {
            return refuted(stepId, evidence, List.of(t.getClass().getName() + ": " + t.getMessage()),
                    "Game rules verification failed: " + t.getMessage());
        }
    }

    public static Object resolveSymbol(Class<?> symCls, Class<?> provCls, String name) {
        Object direct = RuntimeBridge.findSymbolConstant(symCls, name);
        if (direct != null) {
            return direct;
        }
        return RuntimeBridge.findSymbolConstant(provCls, name);
    }

    public static Object instantiateAdjudicator(
            Class<?> adjCls, TypeElement ruleSetProvider, WorkspaceAnalyzer.CodebaseModel model) throws Throwable {
        // 1. Try static factory returning adjCls or zero-arg constructor
        for (Constructor<?> c : adjCls.getConstructors()) {
            if (c.getParameterCount() == 0) {
                return c.newInstance();
            }
        }
        // 2. If constructor takes a RuleSet/dependency, obtain it from ruleSetProvider or paramType itself
        for (Constructor<?> c : adjCls.getConstructors()) {
            if (c.getParameterCount() == 1) {
                Class<?> paramType = c.getParameterTypes()[0];
                List<Class<?>> providers = new ArrayList<>();
                if (ruleSetProvider != null) {
                    providers.add(model.loadClass(ruleSetProvider.getQualifiedName().toString()));
                }
                providers.add(paramType);
                for (Class<?> provCls : providers) {
                    for (Method m : provCls.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                                && paramType.isAssignableFrom(m.getReturnType())) {
                            return c.newInstance(m.invoke(null));
                        }
                    }
                    for (var f : provCls.getFields()) {
                        if (Modifier.isStatic(f.getModifiers())) {
                            Object val = f.get(null);
                            if (val != null && paramType.isInstance(val)) {
                                return c.newInstance(val);
                            }
                        }
                    }
                }
            }
        }
        // 3. Try static methods on adjCls returning adjCls
        for (Method m : adjCls.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                    && adjCls.isAssignableFrom(m.getReturnType())) {
                return m.invoke(null);
            }
        }
        throw new IllegalStateException("Cannot instantiate adjudicator " + adjCls.getName());
    }

    public static Method findEvalMethod(Class<?> adjCls, Class<?> symCls, String preferredName) {
        if (preferredName != null) {
            for (Method m : adjCls.getMethods()) {
                if (m.getName().equals(preferredName) && m.getParameterCount() == 2
                        && m.getParameterTypes()[0].isAssignableFrom(symCls)
                        && m.getParameterTypes()[1].isAssignableFrom(symCls)) {
                    return m;
                }
            }
        }
        for (Method m : adjCls.getMethods()) {
            if (m.getParameterCount() == 2
                    && m.getParameterTypes()[0].isAssignableFrom(symCls)
                    && m.getParameterTypes()[1].isAssignableFrom(symCls)
                    && !m.getReturnType().equals(void.class)) {
                return m;
            }
        }
        return null;
    }

    public static Object invokeEval(Method m, Object target, Object s1, Object s2) throws Throwable {
        Object receiver = Modifier.isStatic(m.getModifiers()) ? null : target;
        return m.invoke(receiver, s1, s2);
    }

    public static CanonicalOutcome normalizeOutcome(Object raw, Object s1, Object s2) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer i) {
            if (i > 0) return CanonicalOutcome.P1_WIN;
            if (i < 0) return CanonicalOutcome.P2_WIN;
            return CanonicalOutcome.DRAW;
        }
        // Check if raw is a RoundResult / MatchOutcome wrapper with an outcome()/result()/winner() method
        String str = raw.toString().toUpperCase(Locale.ROOT);
        if (raw instanceof Enum<?> e) {
            str = e.name().toUpperCase(Locale.ROOT);
        } else {
            for (String accessor : List.of("outcome", "getOutcome", "result", "getResult")) {
                try {
                    Method m = raw.getClass().getMethod(accessor);
                    Object inner = m.invoke(raw);
                    if (inner != null && inner != raw) {
                        CanonicalOutcome nested = normalizeOutcome(inner, s1, s2);
                        if (nested != null) {
                            return nested;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (str.contains("DRAW") || str.contains("TIE")) {
            return CanonicalOutcome.DRAW;
        }
        if (str.contains("PLAYER_TWO") || str.contains("PLAYER2") || str.contains("P2_WIN")
                || str.contains("LOSE") || str.contains("LOSS") || str.contains("SECOND_WINS")) {
            return CanonicalOutcome.P2_WIN;
        }
        if (str.contains("PLAYER_ONE") || str.contains("PLAYER1") || str.contains("P1_WIN")
                || str.equals("WIN") || str.equals("FIRST_WINS") || str.contains("WIN")) {
            return CanonicalOutcome.P1_WIN;
        }
        return null;
    }

    private static ProofStep refuted(
            String stepId, Map<String, Object> evidence, List<String> witnesses, String summary) {
        return new ProofStep(
                stepId,
                "GameRulesExhaustiveProof",
                "STRATUM_3_BEHAVIOR",
                "GameRulesProofTactic",
                "All 3x3 symbol pairs satisfy totality, draw reflexivity, antisymmetry, determinism, and PSR schema",
                ProofStatus.REFUTED,
                summary,
                evidence,
                witnesses,
                List.of());
    }
}
