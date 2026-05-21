package com.poseidon.codegraph.model.delta;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeNode;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.RelationshipType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates parser output before it is applied to storage.
 */
public class GraphDeltaValidator {

    public List<Diagnostic> validate(GraphDelta delta) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (delta == null) {
            diagnostics.add(error("delta.null", "GraphDelta must not be null", null, null));
            return diagnostics;
        }

        Map<String, String> nodeTypes = new HashMap<>();
        validateNodes("package", safeList(delta.packages()), "CodePackage", nodeTypes, diagnostics);
        validateNodes("unit", safeList(delta.units()), "CodeUnit", nodeTypes, diagnostics);
        validateNodes("function", safeList(delta.functions()), "CodeFunction", nodeTypes, diagnostics);
        validateNodes("endpoint", safeList(delta.endpoints()), "CodeEndpoint", nodeTypes, diagnostics);
        validateEndpoints(safeList(delta.endpoints()), diagnostics);
        validateRelationships(safeList(delta.relationships()), nodeTypes, diagnostics);
        validateDeletedIds(delta.deletedNodeIds(), "deletedNodeIds", diagnostics);
        validateDeletedIds(delta.deletedRelationshipIds(), "deletedRelationshipIds", diagnostics);
        return diagnostics;
    }

    public void validateOrThrow(GraphDelta delta) {
        List<Diagnostic> diagnostics = validate(delta);
        List<Diagnostic> errors = diagnostics.stream()
            .filter(diagnostic -> diagnostic.level() == DiagnosticLevel.ERROR)
            .toList();
        if (!errors.isEmpty()) {
            throw new GraphDeltaValidationException(errors);
        }
    }

    private void validateNodes(String kind, List<? extends CodeNode> nodes, String type,
                               Map<String, String> nodeTypes, List<Diagnostic> diagnostics) {
        Set<String> seen = new HashSet<>();
        for (CodeNode node : nodes) {
            if (node == null) {
                diagnostics.add(error(kind + ".null", kind + " node must not be null", null, null));
                continue;
            }
            String id = node.getId();
            if (blank(id)) {
                diagnostics.add(error(kind + ".id.required", kind + " id must not be blank",
                    node.getProjectFilePath(), node.getStartLine()));
            } else {
                if (!seen.add(id)) {
                    diagnostics.add(error(kind + ".id.duplicate", kind + " id is duplicated in delta: " + id,
                        node.getProjectFilePath(), node.getStartLine()));
                }
                String previousType = nodeTypes.putIfAbsent(id, type);
                if (previousType != null && !previousType.equals(type)) {
                    diagnostics.add(error("node.id.type.conflict",
                        "node id is used by multiple node types: " + id + " (" + previousType + ", " + type + ")",
                        node.getProjectFilePath(), node.getStartLine()));
                }
            }
            if (blank(node.getLanguage())) {
                diagnostics.add(error(kind + ".language.required", kind + " language must not be blank",
                    node.getProjectFilePath(), node.getStartLine()));
            }
            if (blank(node.getProjectName())) {
                diagnostics.add(error(kind + ".projectName.required", kind + " projectName must not be blank",
                    node.getProjectFilePath(), node.getStartLine()));
            }
            if (blank(node.getProjectFilePath())) {
                diagnostics.add(error(kind + ".projectFilePath.required", kind + " projectFilePath must not be blank",
                    null, node.getStartLine()));
            }
            validateTypedNode(kind, node, diagnostics);
        }
    }

    private void validateTypedNode(String kind, CodeNode node, List<Diagnostic> diagnostics) {
        if (node instanceof CodeUnit unit && blank(unit.getQualifiedName())) {
            diagnostics.add(error(kind + ".qualifiedName.required", kind + " qualifiedName must not be blank",
                unit.getProjectFilePath(), unit.getStartLine()));
        }
        if (node instanceof CodeFunction function && blank(function.getQualifiedName())) {
            diagnostics.add(error(kind + ".qualifiedName.required", kind + " qualifiedName must not be blank",
                function.getProjectFilePath(), function.getStartLine()));
        }
        if (node instanceof CodePackage pkg && blank(pkg.getQualifiedName())) {
            diagnostics.add(error(kind + ".qualifiedName.required", kind + " qualifiedName must not be blank",
                pkg.getProjectFilePath(), pkg.getStartLine()));
        }
    }

    private void validateEndpoints(List<CodeEndpoint> endpoints, List<Diagnostic> diagnostics) {
        for (CodeEndpoint endpoint : endpoints) {
            if (endpoint == null) {
                continue;
            }
            if (endpoint.getEndpointType() == null) {
                diagnostics.add(error("endpoint.type.required", "endpoint endpointType must not be null",
                    endpoint.getProjectFilePath(), endpoint.getStartLine()));
            }
            if (!blank(endpoint.getDirection())
                && !"inbound".equals(endpoint.getDirection())
                && !"outbound".equals(endpoint.getDirection())) {
                diagnostics.add(error("endpoint.direction.invalid",
                    "endpoint direction must be inbound or outbound: " + endpoint.getDirection(),
                    endpoint.getProjectFilePath(), endpoint.getStartLine()));
            }
        }
    }

    private void validateRelationships(List<CodeRelationship> relationships, Map<String, String> nodeTypes,
                                       List<Diagnostic> diagnostics) {
        Set<String> seen = new HashSet<>();
        for (CodeRelationship relationship : relationships) {
            if (relationship == null) {
                diagnostics.add(error("relationship.null", "relationship must not be null", null, null));
                continue;
            }
            if (blank(relationship.getFromNodeId())) {
                diagnostics.add(error("relationship.from.required", "relationship fromNodeId must not be blank", null, relationship.getLineNumber()));
            }
            if (blank(relationship.getToNodeId())) {
                diagnostics.add(error("relationship.to.required", "relationship toNodeId must not be blank", null, relationship.getLineNumber()));
            }
            if (blank(relationship.getId())) {
                diagnostics.add(error("relationship.id.required", "relationship id must not be blank", null, relationship.getLineNumber()));
            }
            RelationshipType type = relationship.getRelationshipType();
            if (type == null) {
                diagnostics.add(error("relationship.type.required", "relationship relationshipType must not be null", null, relationship.getLineNumber()));
                continue;
            }
            if (blank(relationship.getLanguage())) {
                diagnostics.add(error("relationship.language.required", "relationship language must not be blank", null, relationship.getLineNumber()));
            }
            if (blank(relationship.getProjectName())) {
                diagnostics.add(error("relationship.projectName.required", "relationship projectName must not be blank", null, relationship.getLineNumber()));
            }
            if (!blank(relationship.getId()) && !seen.add("id:" + relationship.getId())) {
                diagnostics.add(error("relationship.id.duplicate", "relationship id is duplicated in delta: " + relationship.getId(), null, relationship.getLineNumber()));
            }
            String key = relationship.getFromNodeId() + ":" + relationship.getToNodeId() + ":" + type;
            if (!seen.add("key:" + key)) {
                diagnostics.add(error("relationship.duplicate", "relationship is duplicated in delta: " + key, null, relationship.getLineNumber()));
            }
            validateRelationshipEndpoint(relationship.getFromNodeId(), type.getFromLabel(), "from", nodeTypes, diagnostics);
            validateRelationshipEndpoint(relationship.getToNodeId(), type.getToLabel(), "to", nodeTypes, diagnostics);
        }
    }

    private void validateRelationshipEndpoint(String nodeId, String expectedType, String side,
                                              Map<String, String> nodeTypes, List<Diagnostic> diagnostics) {
        if (blank(nodeId)) {
            return;
        }
        String actualType = nodeTypes.get(nodeId);
        if (actualType != null && !actualType.equals(expectedType)) {
            diagnostics.add(error("relationship." + side + ".type.invalid",
                "relationship " + side + " node type must be " + expectedType + " but was " + actualType + ": " + nodeId,
                null, null));
        }
    }

    private void validateDeletedIds(List<String> ids, String field, List<Diagnostic> diagnostics) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (blank(id)) {
                diagnostics.add(error(field + ".blank", field + " must not contain blank ids", null, null));
            }
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Diagnostic error(String code, String message, String projectFilePath, Integer lineNumber) {
        return new Diagnostic(DiagnosticLevel.ERROR, code, message, projectFilePath, lineNumber, Map.of());
    }
}
