package com.poseidon.codegraph.model.delta;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeNode;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.EndpointType;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.RelationshipKind;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphDeltaValidatorTest {

    private final GraphDeltaValidator validator = new GraphDeltaValidator();

    @Test
    void acceptsValidFunctionAndUnitRelationship() {
        CodeUnit unit = unit("demo.User");
        CodeFunction function = function("demo.User.find()");
        CodeRelationship relationship = relationship(unit.getId(), function.getId(), RelationshipType.UNIT_TO_FUNCTION);

        GraphDelta delta = delta(List.of(unit), List.of(function), List.of(relationship));

        assertDoesNotThrow(() -> validator.validateOrThrow(delta));
    }

    @Test
    void rejectsMissingRequiredNodeFields() {
        CodeFunction function = new CodeFunction();
        function.setId("demo.User.find()");

        GraphDelta delta = delta(List.of(), List.of(function), List.of());

        assertThrows(GraphDeltaValidationException.class, () -> validator.validateOrThrow(delta));
    }

    @Test
    void rejectsRelationshipWhenKnownNodeTypeDoesNotMatchRelationshipType() {
        CodeUnit unit = unit("demo.User");
        CodeFunction function = function("demo.User.find()");
        CodeRelationship relationship = relationship(function.getId(), unit.getId(), RelationshipType.UNIT_TO_FUNCTION);

        GraphDelta delta = delta(List.of(unit), List.of(function), List.of(relationship));

        assertThrows(GraphDeltaValidationException.class, () -> validator.validateOrThrow(delta));
    }

    @Test
    void rejectsRelationshipWithoutId() {
        CodeUnit unit = unit("demo.User");
        CodeFunction function = function("demo.User.find()");
        CodeRelationship relationship = relationship(unit.getId(), function.getId(), RelationshipType.UNIT_TO_FUNCTION);
        relationship.setId(null);

        GraphDelta delta = delta(List.of(unit), List.of(function), List.of(relationship));

        assertThrows(GraphDeltaValidationException.class, () -> validator.validateOrThrow(delta));
    }

    @Test
    void validatesEveryDeclaredRelationshipTypeAgainstItsEndpointNodeKinds() {
        for (RelationshipType type : RelationshipType.values()) {
            CodeNode from = node(type.getFromLabel(), "from-" + type.name());
            CodeNode to = node(type.getToLabel(), "to-" + type.name());
            CodeRelationship relationship = relationship(from.getId(), to.getId(), type);
            GraphDelta delta = deltaWithNodes(List.of(from, to), relationship);

            assertDoesNotThrow(() -> validator.validateOrThrow(delta), type.name());

            CodeNode wrongFrom = node(differentLabel(type.getFromLabel()), "wrong-from-" + type.name());
            CodeRelationship invalid = relationship(wrongFrom.getId(), to.getId(), type);
            List<String> codes = validator.validate(deltaWithNodes(List.of(wrongFrom, to), invalid))
                .stream().map(Diagnostic::code).toList();
            assertEquals(List.of("relationship.from.type.invalid"), codes, type.name());
        }
    }

    @Test
    void acceptsLanguageOwnedRelationshipWithExplicitContract() {
        CodeUnit implementation = unit("demo.UserService");
        CodeUnit contract = unit("demo.Service");
        CodeRelationship relationship = relationship(
            implementation.getId(), contract.getId(), RelationshipType.of("GO_SATISFIES"));
        relationship.setRelationshipKind(RelationshipKind.CONFORMS);
        relationship.setFromNodeType("CodeUnit");
        relationship.setToNodeType("CodeUnit");

        assertDoesNotThrow(() -> validator.validateOrThrow(
            delta(List.of(implementation, contract), List.of(), List.of(relationship))));
    }

    @Test
    void rejectsLanguageOwnedRelationshipWithoutBehaviorAndEndpointContract() {
        CodeUnit implementation = unit("demo.UserService");
        CodeUnit contract = unit("demo.Service");
        CodeRelationship relationship = relationship(
            implementation.getId(), contract.getId(), RelationshipType.of("GO_SATISFIES"));

        List<String> codes = validator.validate(
            delta(List.of(implementation, contract), List.of(), List.of(relationship)))
            .stream().map(Diagnostic::code).toList();

        assertEquals(List.of(
            "relationship.kind.required",
            "relationship.fromNodeType.required",
            "relationship.toNodeType.required"), codes);
    }

    @Test
    void reportsNullDeltaAsDiagnostic() {
        List<Diagnostic> diagnostics = validator.validate(null);

        assertEquals(List.of("delta.null"), diagnostics.stream().map(Diagnostic::code).toList());
    }

    @Test
    void rejectsDuplicateIdsTypeConflictsInvalidEndpointAndDeletedBlankIds() {
        CodeUnit unit = unit("shared");
        CodeFunction function = function("shared");
        CodeFunction duplicateFunction = function("demo.User.other()");
        duplicateFunction.setId(function.getId());
        CodeEndpoint endpoint = endpoint("endpoint:1");
        endpoint.setDirection("sideways");
        endpoint.setEndpointType(null);

        GraphDelta delta = new GraphDelta(
            null,
            List.of(pkg("demo")),
            List.of(unit),
            List.of(function, duplicateFunction),
            List.of(endpoint),
            List.of(),
            List.of(""),
            List.of(" "),
            List.of());

        List<String> codes = validator.validate(delta).stream().map(Diagnostic::code).toList();

        assertEquals(List.of(
            "node.id.type.conflict",
            "function.id.duplicate",
            "node.id.type.conflict",
            "endpoint.type.required",
            "endpoint.direction.invalid",
            "deletedNodeIds.blank",
            "deletedRelationshipIds.blank"), codes);
    }

    @Test
    void rejectsDuplicateRelationshipsAndMissingRelationshipFields() {
        CodeUnit unit = unit("demo.User");
        CodeFunction function = function("demo.User.find()");
        CodeRelationship first = relationship(unit.getId(), function.getId(), RelationshipType.UNIT_TO_FUNCTION);
        CodeRelationship second = relationship(unit.getId(), function.getId(), RelationshipType.UNIT_TO_FUNCTION);
        second.setId("another-id");
        CodeRelationship missing = new CodeRelationship();

        GraphDelta delta = delta(List.of(unit), List.of(function), List.of(first, second, missing));

        List<String> codes = validator.validate(delta).stream().map(Diagnostic::code).toList();

        assertEquals(List.of(
            "relationship.duplicate",
            "relationship.from.required",
            "relationship.to.required",
            "relationship.id.required",
            "relationship.type.required"), codes);
    }

    private GraphDelta delta(List<CodeUnit> units, List<CodeFunction> functions, List<CodeRelationship> relationships) {
        return new GraphDelta(null, List.of(), units, functions, List.of(), relationships, List.of(), List.of(), List.of());
    }

    private CodePackage pkg(String id) {
        CodePackage pkg = new CodePackage();
        pkg.setId(id);
        pkg.setName("demo");
        pkg.setQualifiedName(id);
        pkg.setLanguage("java");
        pkg.setProjectName("demo");
        pkg.setProjectFilePath("src/main/java/demo/User.java");
        return pkg;
    }

    private CodeUnit unit(String id) {
        CodeUnit unit = new CodeUnit();
        unit.setId(id);
        unit.setName("User");
        unit.setQualifiedName(id);
        unit.setLanguage("java");
        unit.setProjectName("demo");
        unit.setProjectFilePath("src/main/java/demo/User.java");
        return unit;
    }

    private CodeFunction function(String id) {
        CodeFunction function = new CodeFunction();
        function.setId(id);
        function.setName("find");
        function.setQualifiedName(id);
        function.setLanguage("java");
        function.setProjectName("demo");
        function.setProjectFilePath("src/main/java/demo/User.java");
        return function;
    }

    private CodeRelationship relationship(String from, String to, RelationshipType type) {
        CodeRelationship relationship = new CodeRelationship();
        relationship.setFromNodeId(from);
        relationship.setToNodeId(to);
        relationship.setRelationshipType(type);
        relationship.setId(GraphIds.relationshipId(from, type, to));
        relationship.setLanguage("java");
        relationship.setProjectName("demo");
        return relationship;
    }

    private CodeEndpoint endpoint(String id) {
        CodeEndpoint endpoint = new HttpEndpoint();
        endpoint.setId(id);
        endpoint.setName("endpoint");
        endpoint.setLanguage("java");
        endpoint.setProjectName("demo");
        endpoint.setProjectFilePath("src/main/java/demo/User.java");
        endpoint.setEndpointType(EndpointType.HTTP);
        endpoint.setDirection("inbound");
        return endpoint;
    }

    private CodeNode node(String label, String id) {
        return switch (label) {
            case "CodePackage" -> pkg("pkg:" + id);
            case "CodeUnit" -> unit("unit:" + id);
            case "CodeFunction" -> function("fn:" + id);
            case "CodeEndpoint" -> endpoint("endpoint:inbound:HTTP:" + id);
            default -> throw new IllegalArgumentException("unknown node label " + label);
        };
    }

    private String differentLabel(String label) {
        return switch (label) {
            case "CodePackage" -> "CodeUnit";
            case "CodeUnit" -> "CodeFunction";
            case "CodeFunction" -> "CodeEndpoint";
            case "CodeEndpoint" -> "CodePackage";
            default -> throw new IllegalArgumentException("unknown node label " + label);
        };
    }

    private GraphDelta deltaWithNodes(List<CodeNode> nodes, CodeRelationship relationship) {
        List<CodePackage> packages = new ArrayList<>();
        List<CodeUnit> units = new ArrayList<>();
        List<CodeFunction> functions = new ArrayList<>();
        List<CodeEndpoint> endpoints = new ArrayList<>();
        for (CodeNode node : nodes) {
            if (node instanceof CodePackage value) packages.add(value);
            else if (node instanceof CodeUnit value) units.add(value);
            else if (node instanceof CodeFunction value) functions.add(value);
            else if (node instanceof CodeEndpoint value) endpoints.add(value);
        }
        return new GraphDelta(null, packages, units, functions, endpoints,
            List.of(relationship), List.of(), List.of(), List.of());
    }
}
