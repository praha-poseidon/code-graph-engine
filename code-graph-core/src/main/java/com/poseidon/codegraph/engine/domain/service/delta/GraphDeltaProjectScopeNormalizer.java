package com.poseidon.codegraph.engine.domain.service.delta;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeNode;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.delta.GraphDelta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes parser output project-scoped before it is applied to storage.
 */
public class GraphDeltaProjectScopeNormalizer {

    public GraphDelta normalize(GraphDelta delta, String projectName) {
        if (delta == null || blank(projectName)) {
            return delta;
        }

        Map<String, String> idMapping = new HashMap<>();
        scopeNodes(delta.packages(), projectName, idMapping);
        scopeNodes(delta.units(), projectName, idMapping);
        scopeNodes(delta.functions(), projectName, idMapping);
        scopeNodes(delta.endpoints(), projectName, idMapping);

        for (CodeUnit unit : safeList(delta.units())) {
            unit.setPackageId(scopeReference(unit.getPackageId(), projectName, idMapping));
        }
        for (CodeEndpoint endpoint : safeList(delta.endpoints())) {
            if (endpoint.getFunction() != null) {
                endpoint.setFunction((CodeFunction) scopeNode(endpoint.getFunction(), projectName, idMapping));
            }
        }
        for (CodeRelationship relationship : safeList(delta.relationships())) {
            if (relationship == null) {
                continue;
            }
            relationship.setProjectName(projectName);
            relationship.setId(scopeOptional(relationship.getId(), projectName));
            relationship.setFromNodeId(scopeReference(relationship.getFromNodeId(), projectName, idMapping));
            relationship.setToNodeId(scopeReference(relationship.getToNodeId(), projectName, idMapping));
        }
        return new GraphDelta(
            delta.scope(),
            delta.packages(),
            delta.units(),
            delta.functions(),
            delta.endpoints(),
            delta.relationships(),
            scopeIds(delta.deletedNodeIds(), projectName),
            scopeIds(delta.deletedRelationshipIds(), projectName),
            delta.diagnostics()
        );
    }

    private void scopeNodes(List<? extends CodeNode> nodes, String projectName, Map<String, String> idMapping) {
        for (CodeNode node : safeList(nodes)) {
            scopeNode(node, projectName, idMapping);
        }
    }

    private CodeNode scopeNode(CodeNode node, String projectName, Map<String, String> idMapping) {
        if (node == null) {
            return null;
        }
        node.setProjectName(projectName);
        String originalId = node.getId();
        String scopedId = scopeRequired(originalId, projectName);
        if (!blank(originalId)) {
            idMapping.put(originalId, scopedId);
        }
        node.setId(scopedId);
        return node;
    }

    private String scopeReference(String id, String projectName, Map<String, String> idMapping) {
        if (blank(id)) {
            return id;
        }
        return idMapping.getOrDefault(id, scopeRequired(id, projectName));
    }

    private String scopeRequired(String id, String projectName) {
        if (blank(id) || isScoped(id, projectName)) {
            return id;
        }
        return GraphIds.scoped(projectName, id);
    }

    private String scopeOptional(String id, String projectName) {
        if (blank(id) || isScoped(id, projectName)) {
            return id;
        }
        return GraphIds.scoped(projectName, id);
    }

    private List<String> scopeIds(List<String> ids, String projectName) {
        if (ids == null) {
            return null;
        }
        return ids.stream()
            .map(id -> scopeOptional(id, projectName))
            .toList();
    }

    private boolean isScoped(String id, String projectName) {
        return GraphIds.isScoped(projectName, id);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
