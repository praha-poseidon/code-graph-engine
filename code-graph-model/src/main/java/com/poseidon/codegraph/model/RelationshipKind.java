package com.poseidon.codegraph.model;

/**
 * Engine-level relationship behavior.
 *
 * <p>This vocabulary is deliberately language-neutral. Parsers keep their exact
 * language relationship in {@link RelationshipType}; Engine services depend on
 * this smaller semantic contract instead of knowing every language edge name.</p>
 */
public enum RelationshipKind {
    CALL,
    CONTAINS,
    SPECIALIZES,
    CONFORMS,
    REFINES,
    EMBEDS,
    RENDERS,
    BINDS_ENDPOINT,
    MATCHES_ENDPOINT,
    OTHER
}
