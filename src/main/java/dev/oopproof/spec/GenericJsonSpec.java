package dev.oopproof.spec;

import dev.oopproof.core.AstQueries;
import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.JsonUtil;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.RuntimeBridge;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import dev.oopproof.tactics.EncapsulationTactic;
import dev.oopproof.tactics.HygieneAndCompileTactic;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/**
 * Question-agnostic declarative specification loaded from a .spec.json file.
 * Enables proving arbitrary Java OOP and systems designs (LRU Cache, Order Book, Rate Limiter, etc.)
 * via role binding search, AST structural lemmas, JSON call-sequence oracles, and
 * parameterized Java property-checking theorem probes.
 */
public final class GenericJsonSpec implements FormalSpec {

    private final String id;
    private final String title;
    private final String description;
    private final Map<String, Object> rawSpec;

    private GenericJsonSpec(String id, String title, String description, Map<String, Object> rawSpec) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.rawSpec = rawSpec;
    }

    public static GenericJsonSpec load(Path specFile) throws IOException {
        String content = Files.readString(specFile, StandardCharsets.UTF_8);
        Map<String, Object> obj = JsonUtil.parseObject(content);
        String id = stringOr(obj, "id", specFile.getFileName().toString());
        String title = stringOr(obj, "title", id);
        String description = stringOr(obj, "description", "");
        return new GenericJsonSpec(id, title, description, obj);
    }

    @Override
    public String id() { return id; }

    @Override
    public String title() { return title; }

    @Override
    public String description() { return description; }

    @Override
    @SuppressWarnings("unchecked")
    public List<ScoredBinding> proposeBindings(WorkspaceAnalyzer.CodebaseModel model) {
        List<Map<String, Object>> roleDefs = listOfMaps(rawSpec.get("roles"));
        if (roleDefs.isEmpty()) {
            return List.of(new ScoredBinding(new BindingEnv(), 0.5, "No role bindings required"));
        }

        // Backtracking search over top candidates per role to build valid BindingEnvs
        List<List<Map.Entry<TypeElement, Double>>> perRoleCandidates = new ArrayList<>();
        List<String> roleNames = new ArrayList<>();

        for (Map<String, Object> rd : roleDefs) {
            String roleName = (String) rd.get("name");
            String kind = stringOr(rd, "kind", "any");
            List<String> hints = listOfStrings(rd.get("nameHints"));
            List<String> requiredMethods = listOfStrings(rd.get("requiredMethods"));
            roleNames.add(roleName);

            List<Map.Entry<TypeElement, Double>> scored = new ArrayList<>();
            for (TypeElement te : model.declaredTypes()) {
                if ("interface_or_abstract".equals(kind) && !AstQueries.isInterfaceOrAbstract(te)) {
                    continue;
                }
                if ("concrete_class".equals(kind) && !AstQueries.isConcreteClass(te)) {
                    continue;
                }
                double s = scoreType(te, hints, requiredMethods);
                if (s > 0.05) {
                    scored.add(Map.entry(te, s));
                }
            }
            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            if (scored.size() > 3) {
                scored = scored.subList(0, 3);
            }
            if (scored.isEmpty()) {
                return List.of(new ScoredBinding(new BindingEnv(), 0.1, "Could not match role " + roleName));
            }
            perRoleCandidates.add(scored);
        }

        List<ScoredBinding> results = new ArrayList<>();
        buildCombinations(roleNames, perRoleCandidates, 0, new BindingEnv(), 0.0, results);

        // Also bind symbol roles (method names) if specified
        List<Map<String, Object>> methodBindings = listOfMaps(rawSpec.get("methodBindings"));
        if (!methodBindings.isEmpty()) {
            List<ScoredBinding> enriched = new ArrayList<>();
            for (ScoredBinding sb : results) {
                BindingEnv env = sb.env();
                for (Map<String, Object> mb : methodBindings) {
                    String role = (String) mb.get("name");
                    String ownerRole = (String) mb.get("ownerRole");
                    List<String> hints = listOfStrings(mb.get("nameHints"));
                    TypeElement owner = env.type(ownerRole).orElse(null);
                    if (owner != null) {
                        String bestMethod = findBestMethodName(owner, hints);
                        if (bestMethod != null) {
                            env = env.withSymbol(role, bestMethod);
                        }
                    }
                }
                enriched.add(new ScoredBinding(env, sb.priorScore(), sb.rationale()));
            }
            results = enriched;
        }

        results.sort((a, b) -> Double.compare(b.priorScore(), a.priorScore()));
        return results.size() > 16 ? results.subList(0, 16) : results;
    }

    private static void buildCombinations(
            List<String> names,
            List<List<Map.Entry<TypeElement, Double>>> cands,
            int idx,
            BindingEnv cur,
            double scoreSum,
            List<ScoredBinding> out) {
        if (out.size() >= 32) {
            return;
        }
        if (idx == names.size()) {
            double avg = scoreSum / Math.max(1, names.size());
            out.add(new ScoredBinding(cur, avg, "Matched " + names.size() + " declarative spec roles"));
            return;
        }
        for (Map.Entry<TypeElement, Double> entry : cands.get(idx)) {
            buildCombinations(
                    names, cands, idx + 1,
                    cur.withType(names.get(idx), entry.getKey()),
                    scoreSum + entry.getValue(),
                    out);
        }
    }

    private static double scoreType(TypeElement te, List<String> hints, List<String> requiredMethods) {
        String simple = te.getSimpleName().toString().toLowerCase(Locale.ROOT);
        double score = 0.1;
        for (int i = 0; i < hints.size(); i++) {
            String h = hints.get(i).toLowerCase(Locale.ROOT);
            if (simple.equals(h)) {
                score += 0.7 - i * 0.05;
                break;
            } else if (simple.contains(h)) {
                score += 0.45 - i * 0.03;
                break;
            }
        }
        if (!requiredMethods.isEmpty()) {
            int matched = 0;
            for (String rm : requiredMethods) {
                for (ExecutableElement m : ElementFilter.methodsIn(te.getEnclosedElements())) {
                    if (m.getSimpleName().toString().equalsIgnoreCase(rm)) {
                        matched++;
                        break;
                    }
                }
            }
            score += 0.3 * ((double) matched / requiredMethods.size());
        }
        return Math.min(1.0, score);
    }

    private static String findBestMethodName(TypeElement owner, List<String> hints) {
        for (String h : hints) {
            for (ExecutableElement m : ElementFilter.methodsIn(owner.getEnclosedElements())) {
                if (m.getModifiers().contains(Modifier.PUBLIC)
                        && m.getSimpleName().toString().equalsIgnoreCase(h)) {
                    return m.getSimpleName().toString();
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Obligation> obligations() {
        List<Obligation> obs = new ArrayList<>();
        Map<String, Object> hygiene = rawSpec.get("hygiene") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        boolean failOnWarnings = Boolean.TRUE.equals(hygiene.get("failOnWarnings"));
        int minTypes = hygiene.get("minDeclaredTypes") instanceof Number n ? n.intValue() : 1;

        obs.add(new Obligation(
                "hygiene-compile",
                "HygieneAndCompile",
                "STRATUM_1_HYGIENE",
                "Zero build outputs and clean javac compilation",
                new HygieneAndCompileTactic(failOnWarnings),
                true));

        obs.add(new Obligation(
                "encapsulation",
                "EncapsulationAndModularity",
                "STRATUM_2_STRUCTURE",
                "Strict private field encapsulation and modular design",
                new EncapsulationTactic(minTypes),
                false));

        // Structural subtype & anti-instanceof obligations
        List<Map<String, Object>> roleDefs = listOfMaps(rawSpec.get("roles"));
        obs.add(new Obligation(
                "declarative-structure",
                "DeclarativeStructuralContracts",
                "STRATUM_2_STRUCTURE",
                "All declared roles, subtype relations, and required methods hold",
                structuralTactic(roleDefs, Boolean.TRUE.equals(hygiene.get("forbidInstanceof"))),
                true));

        // Declarative oracle call sequences
        List<Map<String, Object>> scenarios = listOfMaps(rawSpec.get("oracleScenarios"));
        if (!scenarios.isEmpty()) {
            obs.add(new Obligation(
                    "oracle-scenarios",
                    "DeclarativeOracleScenarios",
                    "STRATUM_3_BEHAVIOR",
                    "Execution traces match declarative input/output specifications",
                    oracleScenarioTactic(scenarios),
                    false));
        }

        // Parameterized Java formal theorem probes (compiled & executed against candidate API)
        List<Map<String, Object>> probes = listOfMaps(rawSpec.get("theoremProbes"));
        for (int i = 0; i < probes.size(); i++) {
            Map<String, Object> p = probes.get(i);
            String probeName = stringOr(p, "name", "TheoremProbe" + (i + 1));
            String proposition = stringOr(p, "proposition", "Formal property probe holds");
            String stratum = stringOr(p, "stratum", "STRATUM_3_BEHAVIOR");
            List<String> lines = listOfStrings(p.get("javaLines"));
            String javaTemplate = !lines.isEmpty()
                    ? String.join("\n", lines)
                    : stringOr(p, "javaSource", "");
            obs.add(new Obligation(
                    "probe-" + (i + 1),
                    probeName,
                    stratum,
                    proposition,
                    javaProbeTactic(probeName, stratum, proposition, javaTemplate),
                    false));
        }

        return obs;
    }

    private static Tactic structuralTactic(List<Map<String, Object>> roleDefs, boolean forbidInstanceof) {
        return (stepId, model, env) -> {
            Map<String, Object> evidence = new LinkedHashMap<>();
            List<String> witnesses = new ArrayList<>();
            for (Map<String, Object> rd : roleDefs) {
                String role = (String) rd.get("name");
                TypeElement te = env.type(role).orElse(null);
                if (te == null) {
                    return new ProofStep(stepId, "DeclarativeStructuralContracts", "STRATUM_2_STRUCTURE",
                            "GenericJsonSpec.Structure", "All declared roles and subtype contracts hold",
                            ProofStatus.REFUTED, "Role '" + role + "' could not be bound.", evidence,
                            List.of("Unbound role: " + role), List.of());
                }
                String superRole = (String) rd.get("subtypeOf");
                if (superRole != null) {
                    TypeElement sup = env.type(superRole).orElse(null);
                    if (sup == null || !AstQueries.isSubtype(model, te, sup)) {
                        return new ProofStep(stepId, "DeclarativeStructuralContracts", "STRATUM_2_STRUCTURE",
                                "GenericJsonSpec.Structure", "All declared roles and subtype contracts hold",
                                ProofStatus.REFUTED,
                                te.getSimpleName() + " is not a subtype of " + superRole,
                                evidence, List.of("Subtype check failed: " + te.getQualifiedName()), List.of());
                    }
                }
                for (String reqMethod : listOfStrings(rd.get("requiredMethods"))) {
                    boolean found = ElementFilter.methodsIn(te.getEnclosedElements()).stream()
                            .anyMatch(m -> m.getModifiers().contains(Modifier.PUBLIC)
                                    && m.getSimpleName().toString().equalsIgnoreCase(reqMethod));
                    if (!found) {
                        return new ProofStep(stepId, "DeclarativeStructuralContracts", "STRATUM_2_STRUCTURE",
                                "GenericJsonSpec.Structure", "All declared roles and subtype contracts hold",
                                ProofStatus.REFUTED,
                                te.getSimpleName() + " missing required public method '" + reqMethod + "'",
                                evidence, List.of("Missing method: " + reqMethod), List.of());
                    }
                }
                witnesses.add(role + " := " + te.getQualifiedName());
            }
            if (forbidInstanceof) {
                List<AstQueries.ViolationLocation> violations = AstQueries.findForbiddenTypeDispatch(model, Set.of());
                if (!violations.isEmpty()) {
                    return new ProofStep(stepId, "DeclarativeStructuralContracts", "STRATUM_2_STRUCTURE",
                            "GenericJsonSpec.Structure", "All declared roles and subtype contracts hold",
                            ProofStatus.REFUTED,
                            "Forbidden instanceof/getClass detected at " + violations.get(0).location(),
                            evidence, List.of(violations.get(0).detail()), List.of());
                }
            }
            evidence.put("verifiedRoles", roleDefs.size());
            return new ProofStep(stepId, "DeclarativeStructuralContracts", "STRATUM_2_STRUCTURE",
                    "GenericJsonSpec.Structure", "All declared roles and subtype contracts hold",
                    ProofStatus.QED,
                    "Verified all " + roleDefs.size() + " declarative roles, subtype edges, and method contracts.",
                    evidence, witnesses, List.of());
        };
    }

    @SuppressWarnings("unchecked")
    private static Tactic oracleScenarioTactic(List<Map<String, Object>> scenarios) {
        return (stepId, model, env) -> {
            Map<String, Object> evidence = new LinkedHashMap<>();
            List<String> witnesses = new ArrayList<>();
            try {
                for (Map<String, Object> sc : scenarios) {
                    String scName = stringOr(sc, "name", "scenario");
                    String targetRole = (String) sc.get("targetRole");
                    TypeElement te = env.type(targetRole).orElse(null);
                    if (te == null) {
                        return new ProofStep(stepId, "DeclarativeOracleScenarios", "STRATUM_3_BEHAVIOR",
                                "GenericJsonSpec.Oracle", "Execution traces match declarative specifications",
                                ProofStatus.REFUTED, "Unbound targetRole " + targetRole, evidence, List.of(), List.of());
                    }
                    Class<?> cls = model.loadClass(te.getQualifiedName().toString());
                    List<Object> ctorArgs = sc.get("constructorArgs") instanceof List<?> l
                            ? (List<Object>) l : List.of();
                    Object instance = instantiateWithJsonArgs(cls, ctorArgs);

                    List<Map<String, Object>> steps = listOfMaps(sc.get("steps"));
                    for (int i = 0; i < steps.size(); i++) {
                        Map<String, Object> st = steps.get(i);
                        String method = (String) st.get("method");
                        List<Object> args = st.get("args") instanceof List<?> l ? (List<Object>) l : List.of();
                        String expectException = (String) st.get("expectException");
                        if (expectException != null) {
                            try {
                                invokeWithJsonArgs(instance, method, args);
                                return new ProofStep(stepId, "DeclarativeOracleScenarios", "STRATUM_3_BEHAVIOR",
                                        "GenericJsonSpec.Oracle", "Execution traces match declarative specifications",
                                        ProofStatus.REFUTED,
                                        scName + " step " + i + " expected " + expectException + " but succeeded",
                                        evidence, List.of(scName), List.of());
                            } catch (Throwable thrown) {
                                if (!thrown.getClass().getSimpleName().contains(expectException)) {
                                    return new ProofStep(stepId, "DeclarativeOracleScenarios", "STRATUM_3_BEHAVIOR",
                                            "GenericJsonSpec.Oracle", "Execution traces match declarative specifications",
                                            ProofStatus.REFUTED,
                                            scName + " step " + i + " threw " + thrown.getClass().getSimpleName() + " instead of " + expectException,
                                            evidence, List.of(scName), List.of());
                                }
                            }
                        } else {
                            Object actual = invokeWithJsonArgs(instance, method, args);
                            if (st.containsKey("expect")) {
                                Object expected = st.get("expect");
                                if (!valuesEqual(expected, actual)) {
                                    return new ProofStep(stepId, "DeclarativeOracleScenarios", "STRATUM_3_BEHAVIOR",
                                            "GenericJsonSpec.Oracle", "Execution traces match declarative specifications",
                                            ProofStatus.REFUTED,
                                            scName + " step " + i + " (" + method + "): expected=" + expected + ", actual=" + actual,
                                            evidence, List.of(scName), List.of());
                                }
                            }
                        }
                    }
                    witnesses.add("Scenario '" + scName + "' (" + steps.size() + " steps) verified");
                }
                evidence.put("scenariosVerified", scenarios.size());
                return new ProofStep(stepId, "DeclarativeOracleScenarios", "STRATUM_3_BEHAVIOR",
                        "GenericJsonSpec.Oracle", "Execution traces match declarative specifications",
                        ProofStatus.QED,
                        "Verified all " + scenarios.size() + " declarative behavioral scenario(s).",
                        evidence, witnesses, List.of());
            } catch (Throwable t) {
                return new ProofStep(stepId, "DeclarativeOracleScenarios", "STRATUM_3_BEHAVIOR",
                        "GenericJsonSpec.Oracle", "Execution traces match declarative specifications",
                        ProofStatus.REFUTED, "Oracle scenario execution error: " + t.getMessage(),
                        evidence, List.of(t.toString()), List.of());
            }
        };
    }

    private static Tactic javaProbeTactic(String probeName, String stratum, String proposition, String template) {
        return (stepId, model, env) -> {
            Map<String, Object> evidence = new LinkedHashMap<>();
            String expanded = template;
            for (Map.Entry<String, TypeElement> e : env.allTypes().entrySet()) {
                expanded = expanded.replace("{{" + e.getKey() + "}}", e.getValue().getQualifiedName().toString());
            }
            for (Map.Entry<String, String> e : env.allSymbols().entrySet()) {
                expanded = expanded.replace("{{" + e.getKey() + "}}", e.getValue());
            }

            String className = "SyntheticProbe_" + probeName.replaceAll("[^A-Za-z0-9_]", "_");
            String source = expanded.replace("{{PROBE_CLASS}}", className);

            try {
                RuntimeBridge.ExtensionCompileResult comp =
                        RuntimeBridge.compileSyntheticExtension(model, className, source);
                try {
                    if (!comp.success()) {
                        return new ProofStep(stepId, probeName, stratum, "GenericJsonSpec.JavaProbe", proposition,
                                ProofStatus.REFUTED, "Probe compilation failed against candidate API",
                                evidence, comp.diagnostics(), List.of());
                    }
                    try (URLClassLoader cl = RuntimeBridge.createExtensionClassLoader(model, comp.extensionClassesDir())) {
                        Class<?> cls = Class.forName(className, true, cl);
                        Method verify = cls.getMethod("prove");
                        Object res = verify.invoke(null);
                        if (res instanceof List<?> errors && !errors.isEmpty()) {
                            List<String> w = new ArrayList<>();
                            for (Object o : errors) w.add(String.valueOf(o));
                            return new ProofStep(stepId, probeName, stratum, "GenericJsonSpec.JavaProbe", proposition,
                                    ProofStatus.REFUTED, "Theorem probe found counterexample: " + w.get(0),
                                    evidence, w, List.of());
                        }
                        evidence.put("probeClass", className);
                        evidence.put("status", "QED");
                        return new ProofStep(stepId, probeName, stratum, "GenericJsonSpec.JavaProbe", proposition,
                                ProofStatus.QED, "Compiled and proved formal probe '" + probeName + "' with 0 counterexamples.",
                                evidence, List.of(className + ".prove() == []"), List.of());
                    }
                } finally {
                    RuntimeBridge.deleteTempDir(comp.extensionClassesDir());
                }
            } catch (Throwable t) {
                Throwable root = t.getCause() != null ? t.getCause() : t;
                return new ProofStep(stepId, probeName, stratum, "GenericJsonSpec.JavaProbe", proposition,
                        ProofStatus.REFUTED, "Probe execution threw " + root.getClass().getSimpleName() + ": " + root.getMessage(),
                        evidence, List.of(root.toString()), List.of());
            }
        };
    }

    private static Object instantiateWithJsonArgs(Class<?> cls, List<Object> args) throws Throwable {
        if (args.isEmpty()) {
            return RuntimeBridge.instantiateZeroArg(cls);
        }
        for (var c : cls.getConstructors()) {
            if (c.getParameterCount() == args.size()) {
                Object[] converted = convertArgs(c.getParameterTypes(), args);
                if (converted != null) {
                    try {
                        return c.newInstance(converted);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        throw ite.getCause();
                    }
                }
            }
        }
        throw new NoSuchMethodException("No matching constructor on " + cls.getName());
    }

    private static Object invokeWithJsonArgs(Object instance, String methodName, List<Object> args) throws Throwable {
        for (Method m : instance.getClass().getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.size()) {
                Object[] converted = convertArgs(m.getParameterTypes(), args);
                if (converted != null) {
                    try {
                        return m.invoke(instance, converted);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        throw ite.getCause();
                    }
                }
            }
        }
        throw new NoSuchMethodException("No compatible method " + methodName + " on " + instance.getClass().getName());
    }

    private static Object[] convertArgs(Class<?>[] paramTypes, List<Object> args) {
        Object[] out = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Object v = args.get(i);
            Class<?> p = paramTypes[i];
            if (v == null) {
                if (p.isPrimitive()) return null;
                out[i] = null;
            } else if (p == int.class || p == Integer.class) {
                if (!(v instanceof Number n)) return null;
                out[i] = n.intValue();
            } else if (p == long.class || p == Long.class) {
                if (!(v instanceof Number n)) return null;
                out[i] = n.longValue();
            } else if (p == double.class || p == Double.class) {
                if (!(v instanceof Number n)) return null;
                out[i] = n.doubleValue();
            } else if (p == String.class) {
                out[i] = String.valueOf(v);
            } else if (p.isInstance(v) || p == Object.class) {
                out[i] = v;
            } else {
                return null;
            }
        }
        return out;
    }

    private static boolean valuesEqual(Object expected, Object actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        if (expected instanceof Number e && actual instanceof Number a) {
            return Math.abs(e.doubleValue() - a.doubleValue()) < 1e-9;
        }
        return String.valueOf(expected).equals(String.valueOf(actual));
    }

    private static String stringOr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object raw) {
        if (!(raw instanceof List<?> l)) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static List<String> listOfStrings(Object raw) {
        if (!(raw instanceof List<?> l)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Object o : l) out.add(String.valueOf(o));
        return out;
    }
}
