package com.poseidon.codegraph.engine.domain.service.delta;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphDeltaApplyServiceTest {

    @Test
    void appliesDeletedRelationshipsBeforeDeletedNodes() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        List<String> operations = new ArrayList<>();

        context.getWriter().setDeleteRelationship(id -> operations.add("relationship:" + id));
        context.getWriter().setDeleteNode(id -> operations.add("node:" + id));

        GraphDelta delta = new GraphDelta(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("node-1"),
            List.of("rel-1"),
            List.of()
        );

        service.apply(delta, context);

        assertEquals(List.of("relationship:demo::rel-1", "node:demo::node-1"), operations);
    }

    @Test
    void scopesNodeAndRelationshipIdsByProjectBeforeSave() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("project-a");
        List<CodeFunction> inserted = new ArrayList<>();
        List<CodeRelationship> relationships = new ArrayList<>();

        context.getReader().setFindExistingUnitsByQualifiedNames(ids -> java.util.Set.of());
        context.getReader().setFindFunctionsByQualifiedNames(ids -> java.util.Map.of());
        context.getReader().setFindExistingStructureRelationships(rels -> java.util.Set.of());
        context.getWriter().setInsertUnitsBatch(units -> {});
        context.getWriter().setInsertFunctionsBatch(inserted::addAll);
        context.getWriter().setInsertRelationshipsBatch(relationships::addAll);

        CodeUnit unit = unit("demo.Caller");
        CodeFunction function = function("demo.Caller.call()");
        CodeRelationship relationship = relationship(unit.getId(), function.getId(), RelationshipType.UNIT_TO_FUNCTION);
        GraphDelta delta = new GraphDelta(
            null,
            List.of(),
            List.of(unit),
            List.of(function),
            List.of(),
            List.of(relationship),
            List.of(),
            List.of(),
            List.of()
        );

        service.apply(delta, context);

        assertEquals("project-a::demo.Caller.call()", inserted.get(0).getId());
        assertEquals("project-a", inserted.get(0).getProjectName());
        assertEquals("project-a::demo.Caller", relationships.get(0).getFromNodeId());
        assertEquals("project-a::demo.Caller.call()", relationships.get(0).getToNodeId());
        assertEquals("project-a", relationships.get(0).getProjectName());
    }

    @Test
    void doesNotLetPlaceholderFunctionOverwriteRealFunction() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        List<CodeFunction> inserted = new ArrayList<>();
        List<CodeFunction> updated = new ArrayList<>();

        CodeFunction incomingPlaceholder = function("fn:demo.User.find()");
        incomingPlaceholder.setIsPlaceholder(true);
        CodeFunction existingReal = function("demo::fn:demo.User.find()");
        existingReal.setIsPlaceholder(false);

        context.getReader().setFindFunctionsByQualifiedNames(ids -> Map.of(existingReal.getId(), existingReal));
        context.getWriter().setInsertFunctionsBatch(inserted::addAll);
        context.getWriter().setUpdateFunctionsBatch(updated::addAll);

        GraphDelta delta = new GraphDelta(
            null,
            List.of(),
            List.of(),
            List.of(incomingPlaceholder),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        service.apply(delta, context);

        assertEquals(List.of(), inserted);
        assertEquals(List.of(), updated);
    }

    @Test
    void insertsOnlyNewPackagesUnitsAndStructureRelationships() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        List<CodePackage> insertedPackages = new ArrayList<>();
        List<CodePackage> updatedPackages = new ArrayList<>();
        List<CodeUnit> insertedUnits = new ArrayList<>();
        List<CodeUnit> updatedUnits = new ArrayList<>();
        List<CodeRelationship> insertedRelationships = new ArrayList<>();

        CodePackage existingPackage = pkg("com.poseidon");
        CodePackage newPackage = pkg("com.poseidon.new");
        CodeUnit existingUnit = unit("com.poseidon.User");
        CodeUnit newUnit = unit("com.poseidon.NewUser");
        CodeRelationship existingRelationship = relationship(
            existingPackage.getId(),
            existingUnit.getId(),
            RelationshipType.PACKAGE_TO_UNIT);
        CodeRelationship newRelationship = relationship(
            newPackage.getId(),
            newUnit.getId(),
            RelationshipType.PACKAGE_TO_UNIT);

        context.getReader().setFindExistingPackagesByQualifiedNames(ids -> java.util.Set.of("demo::com.poseidon"));
        context.getReader().setFindExistingUnitsByQualifiedNames(ids -> java.util.Set.of("demo::com.poseidon.User"));
        context.getReader().setFindFunctionsByQualifiedNames(ids -> Map.of());
        context.getReader().setFindExistingStructureRelationships(rels -> java.util.Set.of(
            "demo::com.poseidon:demo::com.poseidon.User:PACKAGE_TO_UNIT"));
        context.getWriter().setInsertPackagesBatch(insertedPackages::addAll);
        context.getWriter().setUpdatePackagesBatch(updatedPackages::addAll);
        context.getWriter().setInsertUnitsBatch(insertedUnits::addAll);
        context.getWriter().setUpdateUnitsBatch(updatedUnits::addAll);
        context.getWriter().setInsertFunctionsBatch(functions -> {});
        context.getWriter().setInsertRelationshipsBatch(insertedRelationships::addAll);

        GraphDelta delta = new GraphDelta(
            null,
            List.of(existingPackage, newPackage),
            List.of(existingUnit, newUnit),
            List.of(),
            List.of(),
            List.of(existingRelationship, newRelationship),
            List.of(),
            List.of(),
            List.of());

        service.apply(delta, context);

        assertEquals(List.of("demo::com.poseidon.new"), insertedPackages.stream().map(CodePackage::getId).toList());
        assertEquals(List.of("demo::com.poseidon"), updatedPackages.stream().map(CodePackage::getId).toList());
        assertEquals(List.of("demo::com.poseidon.NewUser"), insertedUnits.stream().map(CodeUnit::getId).toList());
        assertEquals(List.of("demo::com.poseidon.User"), updatedUnits.stream().map(CodeUnit::getId).toList());
        assertEquals(List.of("demo::com.poseidon.new:demo::com.poseidon.NewUser:PACKAGE_TO_UNIT"),
            insertedRelationships.stream()
                .map(rel -> rel.getFromNodeId() + ":" + rel.getToNodeId() + ":" + rel.getRelationshipType())
                .toList());
    }

    @Test
    void deduplicatesEndpointsAndCreatesOutboundToInboundMatchRelationship() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        List<CodeEndpoint> insertedEndpoints = new ArrayList<>();
        List<CodeRelationship> insertedRelationships = new ArrayList<>();

        HttpEndpoint inbound = endpoint("endpoint:in", "inbound", "GET", "/users");
        HttpEndpoint duplicateInbound = endpoint("endpoint:in", "inbound", "GET", "/users");
        HttpEndpoint existingOutbound = endpoint("demo::endpoint:out", "outbound", "GET", "/users");

        context.getReader().setFindExistingEndpointsByIds(ids -> java.util.Set.of());
        context.getReader().setFindEndpointsByMatchIdentity((matchIdentity, direction) -> {
            if ("GET /users".equals(matchIdentity) && "outbound".equals(direction)) {
                return List.of(existingOutbound);
            }
            return List.of();
        });
        context.getReader().setFindExistingStructureRelationships(rels -> java.util.Set.of());
        context.getWriter().setInsertEndpointsBatch(insertedEndpoints::addAll);
        context.getWriter().setInsertRelationshipsBatch(insertedRelationships::addAll);

        GraphDelta delta = new GraphDelta(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(inbound, duplicateInbound),
            List.of(),
            List.of(),
            List.of(),
            List.of());

        service.apply(delta, context);

        assertEquals(List.of("demo::endpoint:in"), insertedEndpoints.stream().map(CodeEndpoint::getId).toList());
        assertEquals(1, insertedRelationships.size());
        CodeRelationship match = insertedRelationships.get(0);
        assertEquals(RelationshipType.MATCHES, match.getRelationshipType());
        assertEquals("demo::endpoint:out", match.getFromNodeId());
        assertEquals("demo::endpoint:in", match.getToNodeId());
    }

    @Test
    void persistsExtendsImplementsAndOverridesAsStructureRelationships() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        List<CodeRelationship> insertedRelationships = new ArrayList<>();

        CodeRelationship extendsRelationship = relationship("fn.Child", "fn.Base", RelationshipType.EXTENDS);
        CodeRelationship implementsRelationship = relationship("fn.Child", "fn.Api", RelationshipType.IMPLEMENTS);
        CodeRelationship overridesRelationship = relationship("fn.Child.get()", "fn.Base.get()", RelationshipType.OVERRIDES);
        CodeRelationship callRelationship = relationship("fn.Child.get()", "fn.Helper.run()", RelationshipType.CALLS);

        context.getReader().setFindExistingStructureRelationships(rels -> java.util.Set.of());
        context.getWriter().setInsertRelationshipsBatch(insertedRelationships::addAll);

        GraphDelta delta = new GraphDelta(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(extendsRelationship, implementsRelationship, overridesRelationship, callRelationship),
            List.of(),
            List.of(),
            List.of());

        service.apply(delta, context);

        assertThat(insertedRelationships)
            .extracting(CodeRelationship::getRelationshipType)
            .containsExactlyInAnyOrder(RelationshipType.EXTENDS, RelationshipType.IMPLEMENTS, RelationshipType.OVERRIDES);
        assertThat(insertedRelationships)
            .noneSatisfy(relationship -> assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.CALLS));
    }

    @Test
    void persistsFrontendRelationshipsAsStructureRelationships() {
        GraphDeltaApplyService service = new GraphDeltaApplyService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("frontend");
        List<CodeRelationship> insertedRelationships = new ArrayList<>();

        CodeRelationship moduleToUnit = relationship("module.UserPage", "component.UserPage", RelationshipType.MODULE_TO_UNIT);
        CodeRelationship imports = relationship("module.UserPage", "module.UserCard", RelationshipType.IMPORTS);
        CodeRelationship renders = relationship("component.UserPage", "component.UserCard", RelationshipType.RENDERS);
        CodeRelationship usesHook = relationship("fn.UserPage", "fn.useUser", RelationshipType.USES_HOOK);
        CodeRelationship callRelationship = relationship("fn.UserPage", "fn.getUser", RelationshipType.CALLS);

        context.getReader().setFindExistingStructureRelationships(rels -> java.util.Set.of());
        context.getWriter().setInsertRelationshipsBatch(insertedRelationships::addAll);

        GraphDelta delta = new GraphDelta(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(moduleToUnit, imports, renders, usesHook, callRelationship),
            List.of(),
            List.of(),
            List.of());

        service.apply(delta, context);

        assertThat(insertedRelationships)
            .extracting(CodeRelationship::getRelationshipType)
            .containsExactlyInAnyOrder(
                RelationshipType.MODULE_TO_UNIT,
                RelationshipType.IMPORTS,
                RelationshipType.RENDERS,
                RelationshipType.USES_HOOK);
        assertThat(insertedRelationships)
            .noneSatisfy(relationship -> assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.CALLS));
    }

    private CodeUnit unit(String id) {
        CodeUnit unit = new CodeUnit();
        unit.setId(id);
        unit.setName(id);
        unit.setQualifiedName(id);
        unit.setLanguage("java");
        unit.setProjectFilePath("src/main/java/demo/User.java");
        return unit;
    }

    private CodePackage pkg(String id) {
        CodePackage pkg = new CodePackage();
        pkg.setId(id);
        pkg.setName(id);
        pkg.setQualifiedName(id);
        pkg.setLanguage("java");
        pkg.setProjectFilePath("src/main/java/demo/User.java");
        return pkg;
    }

    private CodeFunction function(String id) {
        CodeFunction function = new CodeFunction();
        function.setId(id);
        function.setName(id);
        function.setQualifiedName(id);
        function.setLanguage("java");
        function.setProjectFilePath("src/main/java/demo/User.java");
        function.setIsPlaceholder(false);
        return function;
    }

    private CodeRelationship relationship(String from, String to, RelationshipType type) {
        CodeRelationship relationship = new CodeRelationship();
        relationship.setFromNodeId(from);
        relationship.setToNodeId(to);
        relationship.setRelationshipType(type);
        relationship.setId(GraphIds.relationshipId(from, type, to));
        relationship.setLanguage("java");
        return relationship;
    }

    private HttpEndpoint endpoint(String id, String direction, String method, String path) {
        HttpEndpoint endpoint = new HttpEndpoint();
        endpoint.setId(id);
        endpoint.setName(method + " " + path);
        endpoint.setLanguage("java");
        endpoint.setProjectFilePath("src/main/java/demo/User.java");
        endpoint.setDirection(direction);
        endpoint.setHttpMethod(method);
        endpoint.setPath(path);
        endpoint.setMatchIdentity(method + " " + path);
        return endpoint;
    }
}
