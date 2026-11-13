package dev.oopproof.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.TypeElement;

/**
 * Maps abstract specification concept roles (e.g., "@BaseShape", "@AreaVisitor", "@GameEngine")
 * to concrete Java TypeElements and method names discovered in a candidate codebase.
 */
public final class BindingEnv {

    private final Map<String, TypeElement> typeBindings;
    private final Map<String, String> symbolBindings;

    public BindingEnv() {
        this.typeBindings = new LinkedHashMap<>();
        this.symbolBindings = new LinkedHashMap<>();
    }

    public BindingEnv(Map<String, TypeElement> typeBindings, Map<String, String> symbolBindings) {
        this.typeBindings = new LinkedHashMap<>(typeBindings);
        this.symbolBindings = new LinkedHashMap<>(symbolBindings);
    }

    public BindingEnv withType(String role, TypeElement type) {
        BindingEnv copy = new BindingEnv(this.typeBindings, this.symbolBindings);
        copy.typeBindings.put(role, type);
        return copy;
    }

    public BindingEnv withSymbol(String role, String value) {
        BindingEnv copy = new BindingEnv(this.typeBindings, this.symbolBindings);
        copy.symbolBindings.put(role, value);
        return copy;
    }

    public Optional<TypeElement> type(String role) {
        return Optional.ofNullable(typeBindings.get(role));
    }

    public Optional<String> symbol(String role) {
        return Optional.ofNullable(symbolBindings.get(role));
    }

    public Map<String, TypeElement> allTypes() {
        return Collections.unmodifiableMap(typeBindings);
    }

    public Map<String, String> allSymbols() {
        return Collections.unmodifiableMap(symbolBindings);
    }

    public Map<String, String> toDisplayMap() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, TypeElement> e : typeBindings.entrySet()) {
            out.put(e.getKey(), e.getValue().getQualifiedName().toString());
        }
        for (Map.Entry<String, String> e : symbolBindings.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }
}
