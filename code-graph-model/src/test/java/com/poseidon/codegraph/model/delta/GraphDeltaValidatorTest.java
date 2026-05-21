package com.poseidon.codegraph.model.delta;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.EndpointType;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import org.junit.jupiter.api.Test;

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
}
