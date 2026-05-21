package com.poseidon.codegraph.model.delta;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;

import java.util.List;

/**
 * Parser output consumed by the graph engine.
 */
public record GraphDelta(
        DeltaScope scope,
        List<CodePackage> packages,
        List<CodeUnit> units,
        List<CodeFunction> functions,
        List<CodeEndpoint> endpoints,
        List<CodeRelationship> relationships,
        List<String> deletedNodeIds,
        List<String> deletedRelationshipIds,
        List<Diagnostic> diagnostics) {
}
