package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.RelationshipType;

/** Java relationship vocabulary and its GraphDelta endpoint contract. */
public final class JavaRelationshipTypes {

    public static final RelationshipType EXTENDS = RelationshipType.of("EXTENDS");
    public static final RelationshipType IMPLEMENTS = RelationshipType.of("IMPLEMENTS");
    public static final RelationshipType OVERRIDES = RelationshipType.of("OVERRIDES");

    private JavaRelationshipTypes() {
    }

    public static void apply(CodeRelationship relationship, RelationshipType type) {
        relationship.setRelationshipType(type);
        if (EXTENDS.equals(type)) {
            relationship.setFromNodeType("CodeUnit");
            relationship.setToNodeType("CodeUnit");
        } else if (IMPLEMENTS.equals(type)) {
            relationship.setFromNodeType("CodeUnit");
            relationship.setToNodeType("CodeUnit");
        } else if (OVERRIDES.equals(type)) {
            relationship.setFromNodeType("CodeFunction");
            relationship.setToNodeType("CodeFunction");
        } else {
            throw new IllegalArgumentException("Unknown Java relationship type: " + type);
        }
    }
}
