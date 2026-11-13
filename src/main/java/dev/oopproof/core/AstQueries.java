package dev.oopproof.core;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Pure AST and semantic type-system queries backed by javac's CompilationUnitTree
 * and javax.lang.model, replacing brittle regex matching with compiler-grade proofs.
 */
public final class AstQueries {

    private AstQueries() {}

    public record DispatchWitness(
            String concreteType,
            String acceptSignature,
            String invokedVisitorMethod,
            String location) {}

    public record ViolationLocation(String kind, String detail, String location) {}

    public static boolean isInterfaceOrAbstract(TypeElement te) {
        return te.getKind() == ElementKind.INTERFACE || te.getModifiers().contains(Modifier.ABSTRACT);
    }

    public static boolean isConcreteClass(TypeElement te) {
        return (te.getKind() == ElementKind.CLASS || te.getKind() == ElementKind.RECORD)
                && !te.getModifiers().contains(Modifier.ABSTRACT);
    }

    public static boolean isSubtype(WorkspaceAnalyzer.CodebaseModel model, TypeElement sub, TypeElement sup) {
        if (sub.equals(sup)) {
            return true;
        }
        TypeMirror subErased = model.types().erasure(sub.asType());
        TypeMirror supErased = model.types().erasure(sup.asType());
        return model.types().isSubtype(subErased, supErased);
    }

    public static List<ExecutableElement> findAcceptMethods(
            WorkspaceAnalyzer.CodebaseModel model, TypeElement elementType, TypeElement visitorType) {
        List<ExecutableElement> matches = new ArrayList<>();
        TypeMirror visitorErased = model.types().erasure(visitorType.asType());
        for (ExecutableElement m : ElementFilter.methodsIn(elementType.getEnclosedElements())) {
            if (m.getParameters().size() == 1) {
                TypeMirror paramErased = model.types().erasure(m.getParameters().get(0).asType());
                if (model.types().isSameType(paramErased, visitorErased)
                        || model.types().isSubtype(visitorErased, paramErased)) {
                    matches.add(m);
                }
            }
        }
        return matches;
    }

    /**
     * Proves that concreteElement's accept(visitor) method performs genuine double dispatch:
     * its AST body invokes visitor.visit*(this) on a method declared by visitorType whose
     * parameter accepts concreteElement.
     */
    public static DispatchWitness verifyDoubleDispatch(
            WorkspaceAnalyzer.CodebaseModel model,
            TypeElement concreteElement,
            TypeElement visitorType,
            String acceptMethodName) {
        Trees trees = model.trees();
        TypeMirror visitorErased = model.types().erasure(visitorType.asType());
        TypeMirror concreteMirror = concreteElement.asType();

        for (ExecutableElement method : ElementFilter.methodsIn(concreteElement.getEnclosedElements())) {
            if (!method.getSimpleName().toString().equals(acceptMethodName)) {
                continue;
            }
            if (method.getParameters().size() != 1) {
                continue;
            }
            TypeMirror paramErased = model.types().erasure(method.getParameters().get(0).asType());
            if (!model.types().isSameType(paramErased, visitorErased)) {
                continue;
            }

            TreePath methodPath = trees.getPath(method);
            if (methodPath == null) {
                continue;
            }

            List<DispatchWitness> found = new ArrayList<>();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    Element invoked = trees.getElement(getCurrentPath());
                    if (invoked instanceof ExecutableElement exec) {
                        Element owner = exec.getEnclosingElement();
                        if (owner instanceof TypeElement ownerType) {
                            boolean ownedByVisitor = model.types().isSubtype(
                                    visitorErased, model.types().erasure(ownerType.asType()));
                            if (ownedByVisitor && exec.getParameters().size() == 1 && node.getArguments().size() == 1) {
                                TypeMirror targetParam = exec.getParameters().get(0).asType();
                                if (model.types().isAssignable(concreteMirror, targetParam)
                                        && isThisExpression(node.getArguments().get(0))) {
                                    String loc = formatLocation(methodPath.getCompilationUnit(), trees, node);
                                    found.add(new DispatchWitness(
                                            concreteElement.getQualifiedName().toString(),
                                            method.toString(),
                                            ownerType.getSimpleName() + "." + exec.toString(),
                                            loc));
                                }
                            }
                        }
                    }
                    return super.visitMethodInvocation(node, unused);
                }
            }.scan(methodPath, null);

            if (!found.isEmpty()) {
                return found.get(0);
            }
        }
        return null;
    }

    private static boolean isThisExpression(Tree arg) {
        return arg instanceof IdentifierTree id && id.getName().contentEquals("this");
    }

    /**
     * Scans the entire codebase for anti-OOP type-tag dispatching (instanceof, pattern switch, getClass())
     * outside of standard Object.equals(Object) implementations.
     */
    public static List<ViolationLocation> findForbiddenTypeDispatch(
            WorkspaceAnalyzer.CodebaseModel model, Set<TypeElement> targetHierarchyTypes) {
        List<ViolationLocation> violations = new ArrayList<>();
        Trees trees = model.trees();

        for (CompilationUnitTree cu : model.compilationUnits()) {
            new TreePathScanner<Void, Void>() {
                private boolean inEqualsMethod = false;

                @Override
                public Void visitMethod(MethodTree node, Void unused) {
                    boolean prev = inEqualsMethod;
                    inEqualsMethod = isObjectEqualsMethod(node);
                    try {
                        return super.visitMethod(node, unused);
                    } finally {
                        inEqualsMethod = prev;
                    }
                }

                @Override
                public Void visitInstanceOf(InstanceOfTree node, Void unused) {
                    if (!inEqualsMethod) {
                        TreePath typePath = new TreePath(getCurrentPath(), node.getType());
                        TypeMirror checkedType = trees.getTypeMirror(typePath);
                        if (isRelevantHierarchyType(model, checkedType, targetHierarchyTypes)) {
                            violations.add(new ViolationLocation(
                                    "INSTANCEOF_DISPATCH",
                                    "Forbidden instanceof check on " + checkedType + " outside equals()",
                                    formatLocation(cu, trees, node)));
                        }
                    }
                    return super.visitInstanceOf(node, unused);
                }

                @Override
                public Void visitPatternCaseLabel(PatternCaseLabelTree node, Void unused) {
                    TypeMirror patType = trees.getTypeMirror(new TreePath(getCurrentPath(), node.getPattern()));
                    if (isRelevantHierarchyType(model, patType, targetHierarchyTypes)) {
                        violations.add(new ViolationLocation(
                                "PATTERN_SWITCH_DISPATCH",
                                "Forbidden type-pattern switch case on " + patType + " bypassing double dispatch",
                                formatLocation(cu, trees, node)));
                    }
                    return super.visitPatternCaseLabel(node, unused);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    if (!inEqualsMethod) {
                        Element el = trees.getElement(getCurrentPath());
                        if (el instanceof ExecutableElement exec) {
                            String name = exec.getSimpleName().toString();
                            String owner = exec.getEnclosingElement().toString();
                            if ("getClass".equals(name) && "java.lang.Object".equals(owner)) {
                                violations.add(new ViolationLocation(
                                        "REFLECTIVE_GETCLASS",
                                        "Forbidden getClass() runtime type inspection outside equals()",
                                        formatLocation(cu, trees, node)));
                            } else if ("forName".equals(name) && "java.lang.Class".equals(owner)) {
                                violations.add(new ViolationLocation(
                                        "REFLECTIVE_FORNAME",
                                        "Forbidden Class.forName() dynamic dispatch",
                                        formatLocation(cu, trees, node)));
                            }
                        }
                    }
                    return super.visitMethodInvocation(node, unused);
                }
            }.scan(cu, null);
        }
        return violations;
    }

    private static boolean isObjectEqualsMethod(MethodTree node) {
        return node.getName().contentEquals("equals") && node.getParameters().size() == 1;
    }

    private static boolean isRelevantHierarchyType(
            WorkspaceAnalyzer.CodebaseModel model, TypeMirror tm, Set<TypeElement> hierarchy) {
        if (hierarchy.isEmpty() || tm == null) {
            return true;
        }
        if (tm instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            for (TypeElement h : hierarchy) {
                if (isSubtype(model, te, h) || isSubtype(model, h, te)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks encapsulation: flags non-private instance fields (excluding public static final constants
     * and enum constants).
     */
    public static List<ViolationLocation> findEncapsulationViolations(WorkspaceAnalyzer.CodebaseModel model) {
        List<ViolationLocation> violations = new ArrayList<>();
        Trees trees = model.trees();
        for (TypeElement te : model.declaredTypes()) {
            if (te.getKind() == ElementKind.INTERFACE || te.getKind() == ElementKind.RECORD) {
                continue;
            }
            for (VariableElement field : ElementFilter.fieldsIn(te.getEnclosedElements())) {
                if (field.getKind() == ElementKind.ENUM_CONSTANT) {
                    continue;
                }
                Set<Modifier> mods = field.getModifiers();
                if (mods.contains(Modifier.STATIC) && mods.contains(Modifier.FINAL)) {
                    continue;
                }
                if (!mods.contains(Modifier.PRIVATE)) {
                    TreePath path = trees.getPath(field);
                    String loc = path != null
                            ? formatLocation(path.getCompilationUnit(), trees, path.getLeaf())
                            : te.getQualifiedName().toString();
                    violations.add(new ViolationLocation(
                            "NON_PRIVATE_FIELD",
                            "Field '" + te.getSimpleName() + "." + field.getSimpleName() + "' should be private",
                            loc));
                }
            }
        }
        return violations;
    }

    public static List<ViolationLocation> findSwitchOnTypesOrEnums(
            WorkspaceAnalyzer.CodebaseModel model, TypeElement targetType) {
        List<ViolationLocation> hits = new ArrayList<>();
        Trees trees = model.trees();
        TypeMirror targetErased = model.types().erasure(targetType.asType());
        for (CompilationUnitTree cu : model.compilationUnits()) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitSwitch(com.sun.source.tree.SwitchTree node, Void unused) {
                    TypeMirror exprType = trees.getTypeMirror(new TreePath(getCurrentPath(), node.getExpression()));
                    if (exprType != null && model.types().isSameType(model.types().erasure(exprType), targetErased)) {
                        hits.add(new ViolationLocation(
                                "SWITCH_ON_SYMBOL",
                                "Hardcoded switch over " + targetType.getSimpleName() + " limits open/closed extensibility",
                                formatLocation(cu, trees, node)));
                    }
                    return super.visitSwitch(node, unused);
                }
            }.scan(cu, null);
        }
        return hits;
    }

    public static int countLines(WorkspaceAnalyzer.CodebaseModel model) {
        int total = 0;
        for (CompilationUnitTree cu : model.compilationUnits()) {
            LineMap lm = cu.getLineMap();
            long end = model.trees().getSourcePositions().getEndPosition(cu, cu);
            if (lm != null && end > 0) {
                total += (int) lm.getLineNumber(end);
            }
        }
        return total;
    }

    public static String formatLocation(CompilationUnitTree cu, Trees trees, Tree node) {
        long pos = trees.getSourcePositions().getStartPosition(cu, node);
        String file = cu.getSourceFile() != null ? cu.getSourceFile().getName() : "<unknown>";
        int slash = file.lastIndexOf('/');
        String shortName = slash >= 0 ? file.substring(slash + 1) : file;
        if (pos >= 0 && cu.getLineMap() != null) {
            return shortName + ":" + cu.getLineMap().getLineNumber(pos);
        }
        return shortName;
    }
}
