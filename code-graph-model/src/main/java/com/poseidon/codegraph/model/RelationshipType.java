package com.poseidon.codegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Extensible graph relationship name.
 *
 * <p>Unlike an enum, this value accepts language-owned names such as
 * {@code SATISFIES} or {@code USES_TRAIT}. Only genuinely shared edge
 * names live here. Language adapters own their native relationship vocabulary.</p>
 */
public final class RelationshipType {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Map<String, RelationshipType> INTERNED = new ConcurrentHashMap<>();

    public static final RelationshipType CALLS = declared("CALLS", "CodeFunction", "CodeFunction");
    public static final RelationshipType RENDERS = declared("RENDERS", "CodeFunction", "CodeFunction");
    public static final RelationshipType PACKAGE_TO_UNIT = declared("PACKAGE_TO_UNIT", "CodePackage", "CodeUnit");
    public static final RelationshipType PACKAGE_TO_PACKAGE = declared("PACKAGE_TO_PACKAGE", "CodePackage", "CodePackage");
    public static final RelationshipType UNIT_TO_FUNCTION = declared("UNIT_TO_FUNCTION", "CodeUnit", "CodeFunction");
    public static final RelationshipType ENDPOINT_TO_FUNCTION = declared("ENDPOINT_TO_FUNCTION", "CodeEndpoint", "CodeFunction");
    public static final RelationshipType FUNCTION_TO_ENDPOINT = declared("FUNCTION_TO_ENDPOINT", "CodeFunction", "CodeEndpoint");
    public static final RelationshipType MATCHES = declared("MATCHES", "CodeEndpoint", "CodeEndpoint");

    private static final List<RelationshipType> DECLARED_TYPES = List.of(
        CALLS,
        RENDERS,
        PACKAGE_TO_UNIT,
        PACKAGE_TO_PACKAGE,
        UNIT_TO_FUNCTION,
        ENDPOINT_TO_FUNCTION,
        FUNCTION_TO_ENDPOINT,
        MATCHES
    );

    private final String name;
    private final String defaultFromNodeType;
    private final String defaultToNodeType;

    private RelationshipType(String name, String defaultFromNodeType, String defaultToNodeType) {
        this.name = name;
        this.defaultFromNodeType = defaultFromNodeType;
        this.defaultToNodeType = defaultToNodeType;
    }

    private static RelationshipType declared(String name, String fromNodeType, String toNodeType) {
        validateName(name);
        RelationshipType type = new RelationshipType(name, fromNodeType, toNodeType);
        RelationshipType previous = INTERNED.putIfAbsent(name, type);
        return previous == null ? type : previous;
    }

    /**
     * Resolves any safe relationship name. Unknown names intentionally have no
     * Engine metadata; their parser must send endpoint node types.
     */
    @JsonCreator
    public static RelationshipType valueOf(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toUpperCase();
        validateName(normalized);
        return INTERNED.computeIfAbsent(normalized,
            key -> new RelationshipType(key, null, null));
    }

    public static RelationshipType of(String name) {
        return valueOf(name);
    }

    /** Declared shared types only; dynamic language types are not centralized here. */
    public static RelationshipType[] values() {
        return DECLARED_TYPES.toArray(RelationshipType[]::new);
    }

    @JsonValue
    public String name() {
        return name;
    }

    public String getFromLabel() {
        return defaultFromNodeType;
    }

    public String getToLabel() {
        return defaultToNodeType;
    }

    public boolean is(String expectedName) {
        return expectedName != null && name.equals(expectedName);
    }

    private static void validateName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe relationship type name: " + name);
        }
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RelationshipType that && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
