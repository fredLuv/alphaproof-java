package dev.oopproof.tactics;

import dev.oopproof.core.AstQueries;
import dev.oopproof.core.BindingEnv;
import dev.oopproof.core.ProofStatus;
import dev.oopproof.core.ProofStep;
import dev.oopproof.core.Tactic;
import dev.oopproof.core.WorkspaceAnalyzer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/**
 * Proves at the AST and semantic type level that a codebase implements the genuine
 * GoF Visitor pattern (interface/abstract base, concrete subtypes, accept->visit(this)
 * double dispatch) without instanceof, getClass(), or pattern-switch shortcuts.
 */
public final class VisitorDoubleDispatchTactic implements Tactic {

    @Override
    public ProofStep apply(String stepId, WorkspaceAnalyzer.CodebaseModel model, BindingEnv env) {
        TypeElement shapeBase = env.type("SHAPE_BASE").orElse(null);
        TypeElement circle = env.type("CIRCLE").orElse(null);
        TypeElement rectangle = env.type("RECTANGLE").orElse(null);
        TypeElement triangle = env.type("TRIANGLE").orElse(null);
        TypeElement visitorBase = env.type("VISITOR_BASE").orElse(null);
        TypeElement areaVisitor = env.type("AREA_VISITOR").orElse(null);
        String acceptName = env.symbol("ACCEPT_METHOD").orElse("accept");

        Map<String, Object> evidence = new LinkedHashMap<>();
        List<String> witnesses = new ArrayList<>();

        if (shapeBase == null || circle == null || rectangle == null || triangle == null
                || visitorBase == null || areaVisitor == null) {
            return new ProofStep(
                    stepId,
                    "VisitorDoubleDispatch",
                    "STRATUM_2_STRUCTURE",
                    "VisitorDoubleDispatchTactic",
                    "Data model implements genuine Visitor double dispatch without instanceof/type-tag dispatch",
                    ProofStatus.REFUTED,
                    "Missing required Visitor pattern roles in codebase (bound=" + env.toDisplayMap() + ")",
                    evidence,
                    List.of("Missing role binding"),
                    List.of());
        }

        if (!AstQueries.isInterfaceOrAbstract(shapeBase)) {
            return refuted(stepId, evidence, List.of(shapeBase.getQualifiedName() + " is not an interface or abstract class"),
                    "Shape base '" + shapeBase.getSimpleName() + "' must be an interface or abstract class.");
        }
        if (!AstQueries.isInterfaceOrAbstract(visitorBase)) {
            return refuted(stepId, evidence, List.of(visitorBase.getQualifiedName() + " is not an interface or abstract class"),
                    "Visitor base '" + visitorBase.getSimpleName() + "' must be an interface or abstract class.");
        }
        if (!AstQueries.isConcreteClass(areaVisitor) || !AstQueries.isSubtype(model, areaVisitor, visitorBase)) {
            return refuted(stepId, evidence, List.of(areaVisitor.getQualifiedName() + " does not implement " + visitorBase.getSimpleName()),
                    "AreaVisitor must be a concrete implementation of " + visitorBase.getSimpleName() + ".");
        }

        List<TypeElement> shapes = List.of(circle, rectangle, triangle);
        List<AstQueries.DispatchWitness> dispatchWitnesses = new ArrayList<>();
        for (TypeElement concrete : shapes) {
            if (!AstQueries.isConcreteClass(concrete) || !AstQueries.isSubtype(model, concrete, shapeBase)) {
                return refuted(stepId, evidence,
                        List.of(concrete.getQualifiedName() + " is not a concrete subtype of " + shapeBase.getSimpleName()),
                        concrete.getSimpleName() + " must be a concrete subtype of " + shapeBase.getSimpleName() + ".");
            }
            AstQueries.DispatchWitness dw = AstQueries.verifyDoubleDispatch(model, concrete, visitorBase, acceptName);
            if (dw == null) {
                return refuted(stepId, evidence,
                        List.of(concrete.getQualifiedName() + "." + acceptName + "(" + visitorBase.getSimpleName() + ") lacks visitor.visit*(this) AST call"),
                        "Double dispatch missing in " + concrete.getSimpleName() + "." + acceptName + "().");
            }
            dispatchWitnesses.add(dw);
            witnesses.add(dw.concreteType() + "." + acceptName + " -> " + dw.invokedVisitorMethod() + " @ " + dw.location());
        }

        // Anti-cheat: verify NO instanceof / getClass / pattern-switch on Shape hierarchy
        Set<TypeElement> hierarchy = new LinkedHashSet<>(shapes);
        hierarchy.add(shapeBase);
        List<AstQueries.ViolationLocation> cheatViolations = AstQueries.findForbiddenTypeDispatch(model, hierarchy);
        if (!cheatViolations.isEmpty()) {
            List<String> cv = new ArrayList<>();
            for (AstQueries.ViolationLocation v : cheatViolations) {
                cv.add(v.kind() + " @ " + v.location() + ": " + v.detail());
            }
            return refuted(stepId, evidence, cv,
                    "Detected forbidden type-tag dispatch (" + cheatViolations.get(0).kind() + ") instead of Visitor polymorphism.");
        }

        evidence.put("shapeBase", shapeBase.getQualifiedName().toString());
        evidence.put("visitorBase", visitorBase.getQualifiedName().toString());
        evidence.put("areaVisitor", areaVisitor.getQualifiedName().toString());
        evidence.put("acceptMethod", acceptName);
        evidence.put("verifiedDoubleDispatchCount", dispatchWitnesses.size());
        evidence.put("forbiddenTypeDispatchViolations", 0);

        return new ProofStep(
                stepId,
                "VisitorDoubleDispatch",
                "STRATUM_2_STRUCTURE",
                "VisitorDoubleDispatchTactic",
                "Data model implements genuine Visitor double dispatch without instanceof/type-tag dispatch",
                ProofStatus.QED,
                "Proved interface/abstract hierarchy, accept->visit(this) AST double dispatch for all 3 shapes, and zero instanceof/getClass shortcuts.",
                evidence,
                witnesses,
                List.of());
    }

    private static ProofStep refuted(
            String stepId, Map<String, Object> evidence, List<String> witnesses, String summary) {
        return new ProofStep(
                stepId,
                "VisitorDoubleDispatch",
                "STRATUM_2_STRUCTURE",
                "VisitorDoubleDispatchTactic",
                "Data model implements genuine Visitor double dispatch without instanceof/type-tag dispatch",
                ProofStatus.REFUTED,
                summary,
                evidence,
                witnesses,
                List.of());
    }
}
