package com.poseidon.codegraph.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphIdsTest {

    @Test
    void scopesIdOnce() {
        assertEquals("demo::fn:a.b.C.m()", GraphIds.scoped("demo", "fn:a.b.C.m()"));
        assertEquals("demo::fn:a.b.C.m()", GraphIds.scoped("demo", "demo::fn:a.b.C.m()"));
        assertTrue(GraphIds.isScoped("demo", "demo::fn:a.b.C.m()"));
    }

    @Test
    void createsStableRelationshipIds() {
        String left = GraphIds.relationshipId("a", RelationshipType.CALLS, "b");
        String right = GraphIds.relationshipId("a", RelationshipType.CALLS, "b");
        assertEquals(left, right);
    }
}
