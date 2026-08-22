package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.RelationshipKind;
import com.poseidon.codegraph.model.RelationshipType;

/** Java relationship vocabulary and its GraphDelta endpoint contract. */
public final class JavaRelationshipTypes {

    public static final RelationshipType EXTENDS = RelationshipType.of("JAVA_EXTENDS");
    public static final RelationshipType IMPLEMENTS = RelationshipType.of("JAVA_IMPLEMENTS");
    public static final RelationshipType OVERRIDES = RelationshipType.of("JAVA_OVERRIDES");

    private JavaRelationshipTypes() {
    }

    public static void apply(CodeRelationship relationship, RelationshipType type) {
        relationship.setRelationshipType(type);
        if (EXTENDS.equals(type)) {
            relationship.setRelationshipKind(RelationshipKind.SPECIALIZES);
            relationship.setFromNodeType("CodeUnit");
            relationship.setToNodeType("CodeUnit");
        } else if (IMPLEMENTS.equals(type)) {
            relationship.setRelationshipKind(RelationshipKind.CONFORMS);
            relationship.setFromNodeType("CodeUnit");
            relationship.setToNodeType("CodeUnit");
        } else if (OVERRIDES.equals(type)) {
            relationship.setRelationshipKind(RelationshipKind.REFINES);
            relationship.setFromNodeType("CodeFunction");
            relationship.setToNodeType("CodeFunction");
        } else {
            throw new IllegalArgumentException("Unknown Java relationship type: " + type);
        }
    }
}
