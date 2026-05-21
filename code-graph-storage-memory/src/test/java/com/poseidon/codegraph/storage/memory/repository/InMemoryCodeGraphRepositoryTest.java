package com.poseidon.codegraph.storage.memory.repository;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodePackageDO;
import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCodeGraphRepositoryTest {

    private final InMemoryCodeGraphRepository repository = new InMemoryCodeGraphRepository();

    @Test
    void storesAndFindsNodesWithinProjectScope() {
        repository.insertPackagesBatch(List.of(pkg("p1", "demo", "com.demo"), pkg("p2", "other", "com.demo")));
        repository.insertUnitsBatch(List.of(unit("u1", "demo", "src/A.java"), unit("u2", "other", "src/A.java")));
        repository.insertFunctionsBatch(List.of(function("f1", "demo", "src/A.java"), function("f2", "other", "src/A.java")));
        repository.insertEndpointsBatch(List.of(endpoint("e1", "demo", "src/A.java", "inbound"), endpoint("e2", "other", "src/A.java", "inbound")));

        assertThat(repository.findExistingPackagesByQualifiedNames("demo", List.of("com.demo"))).containsExactly("p1");
        assertThat(repository.findExistingUnitsByQualifiedNames("demo", List.of("u1"))).containsExactly("u1");
        assertThat(repository.findExistingFunctionsByQualifiedNames("demo", List.of("f1"))).containsExactly("f1");
        assertThat(repository.findExistingEndpointsByIds("demo", List.of("e1", "e2"))).containsExactly("e1");

        assertThat(repository.findUnitsByProjectFilePath("demo", "src/A.java")).extracting(CodeUnitDO::getId).containsExactly("u1");
        assertThat(repository.findFunctionsByProjectFilePath("demo", "src/A.java")).extracting(CodeFunctionDO::getId).containsExactly("f1");
        assertThat(repository.findEndpointsByProjectFilePath("demo", "src/A.java")).extracting(CodeEndpointDO::getId).containsExactly("e1");
        assertThat(repository.findEndpointsByDirection("demo", "inbound")).extracting(CodeEndpointDO::getId).containsExactly("e1");
    }

    @Test
    void findsCallerFilesAndMetaFromCallRelationships() {
        CodeFunctionDO caller = function("caller", "demo", "src/Caller.java");
        caller.setGitRepoUrl("git@example/demo.git");
        caller.setGitBranch("main");
        CodeFunctionDO callee = function("callee", "demo", "src/Callee.java");
        repository.insertFunctionsBatch(List.of(caller, callee, function("foreign", "other", "src/Callee.java")));
        repository.insertRelationshipsBatch(List.of(
            relationship("rel1", "demo", "caller", "callee", "CALLS"),
            relationship("rel2", "other", "foreign", "callee", "CALLS"),
            relationship("rel3", "demo", "caller", "callee", "UNIT_TO_FUNCTION")));

        assertThat(repository.findWhoCallsMe("demo", "src/Callee.java")).containsExactly("src/Caller.java");
        assertThat(repository.findWhoCallsMeWithMeta("demo", "src/Callee.java"))
            .singleElement()
            .satisfies(meta -> {
                assertThat(meta.getProjectFilePath()).isEqualTo("src/Caller.java");
                assertThat(meta.getGitRepoUrl()).isEqualTo("git@example/demo.git");
                assertThat(meta.getGitBranch()).isEqualTo("main");
            });
    }

    @Test
    void deletesOutgoingCallsAndRelationshipsConnectedToDeletedNodes() {
        repository.insertUnitsBatch(List.of(unit("unit", "demo", "src/A.java")));
        repository.insertFunctionsBatch(List.of(function("caller", "demo", "src/A.java"), function("callee", "demo", "src/B.java")));
        repository.insertEndpointsBatch(List.of(endpoint("endpoint", "demo", "src/A.java", "outbound")));
        repository.insertRelationshipsBatch(List.of(
            relationship("call", "demo", "caller", "callee", "CALLS"),
            relationship("struct", "demo", "unit", "caller", "UNIT_TO_FUNCTION"),
            relationship("match", "demo", "endpoint", "remoteEndpoint", "MATCHES")));

        repository.deleteFileOutgoingCalls("demo", "src/A.java");

        assertThat(repository.findIncomingRelationships("demo", "caller", null)).extracting(CodeRelationshipDO::getId)
            .containsExactly("struct");

        repository.deleteById("demo", "unit");
        repository.deleteById("demo", "endpoint");

        assertThat(repository.findOutgoingRelationships("demo", "unit", null)).isEmpty();
        assertThat(repository.findOutgoingRelationships("demo", "endpoint", null)).isEmpty();
        assertThat(repository.findUnitsByProjectFilePath("demo", "src/A.java")).isEmpty();
        assertThat(repository.findEndpointsByProjectFilePath("demo", "src/A.java")).isEmpty();
    }

    @Test
    void filtersEndpointAndRelationshipQueries() {
        repository.insertEndpointsBatch(List.of(
            endpoint("in", "demo", "src/A.java", "inbound"),
            endpoint("out", "demo", "src/B.java", "outbound")));
        repository.insertRelationshipsBatch(List.of(
            relationship("call", "demo", "from", "to", "CALLS"),
            relationship("match", "demo", "from", "endpoint", "MATCHES")));

        assertThat(repository.findEndpointsByMatchIdentity("GET /users", null)).extracting(CodeEndpointDO::getId)
            .containsExactlyInAnyOrder("in", "out");
        assertThat(repository.findEndpointsByMatchIdentity("GET /users", "outbound")).extracting(CodeEndpointDO::getId)
            .containsExactly("out");
        assertThat(repository.findOutgoingRelationships("demo", "from", "CALLS")).extracting(CodeRelationshipDO::getId)
            .containsExactly("call");
        assertThat(repository.findIncomingRelationships("demo", "endpoint", "MATCHES")).extracting(CodeRelationshipDO::getId)
            .containsExactly("match");
        assertThat(repository.findExistingStructureRelationships("demo", List.of(
            relationship("new", "demo", "from", "endpoint", "MATCHES"),
            relationship("missing", "demo", "x", "y", "CALLS"))))
            .isEqualTo(Set.of("from:endpoint:MATCHES"));
    }

    @Test
    void upsertsNodesAndRelationshipsById() {
        CodePackageDO firstPackage = pkg("pkg", "demo", "com.old");
        CodePackageDO updatedPackage = pkg("pkg", "demo", "com.new");
        CodeUnitDO firstUnit = unit("unit", "demo", "src/Old.java");
        CodeUnitDO updatedUnit = unit("unit", "demo", "src/New.java");
        CodeFunctionDO firstFunction = function("function", "demo", "src/Old.java");
        CodeFunctionDO updatedFunction = function("function", "demo", "src/New.java");
        CodeEndpointDO firstEndpoint = endpoint("endpoint", "demo", "src/Old.java", "inbound");
        CodeEndpointDO updatedEndpoint = endpoint("endpoint", "demo", "src/New.java", "outbound");
        CodeRelationshipDO firstRelationship = relationship("rel", "demo", "from", "old", "CALLS");
        CodeRelationshipDO updatedRelationship = relationship("rel", "demo", "from", "new", "CALLS");

        repository.insertPackagesBatch(List.of(firstPackage));
        repository.updatePackagesBatch(List.of(updatedPackage));
        repository.insertUnitsBatch(List.of(firstUnit));
        repository.updateUnitsBatch(List.of(updatedUnit));
        repository.insertFunctionsBatch(List.of(firstFunction));
        repository.updateFunctionsBatch(List.of(updatedFunction));
        repository.insertEndpointsBatch(List.of(firstEndpoint));
        repository.updateEndpointsBatch(List.of(updatedEndpoint));
        repository.insertRelationshipsBatch(List.of(firstRelationship, updatedRelationship));

        assertThat(repository.findExistingPackagesByQualifiedNames("demo", List.of("com.new"))).containsExactly("pkg");
        assertThat(repository.findUnitsByProjectFilePath("demo", "src/New.java")).extracting(CodeUnitDO::getId)
            .containsExactly("unit");
        assertThat(repository.findFunctionsByProjectFilePath("demo", "src/New.java")).extracting(CodeFunctionDO::getId)
            .containsExactly("function");
        assertThat(repository.findEndpointsByDirection("demo", "outbound")).extracting(CodeEndpointDO::getId)
            .containsExactly("endpoint");
        assertThat(repository.findOutgoingRelationships("demo", "from", "CALLS")).singleElement()
            .satisfies(relationship -> {
                assertThat(relationship.getId()).isEqualTo("rel");
                assertThat(relationship.getToNodeId()).isEqualTo("new");
            });
    }

    @Test
    void deleteByIdRemovesOnlyNodesAndRelationshipsInRequestedProject() {
        repository.insertUnitsBatch(List.of(unit("demo::unit", "demo", "src/A.java"), unit("other::unit", "other", "src/A.java")));
        repository.insertFunctionsBatch(List.of(function("demo::function", "demo", "src/A.java"), function("other::function", "other", "src/A.java")));
        repository.insertEndpointsBatch(List.of(endpoint("demo::endpoint", "demo", "src/A.java", "inbound"), endpoint("other::endpoint", "other", "src/A.java", "inbound")));
        repository.insertRelationshipsBatch(List.of(
            relationship("demo::rel-unit", "demo", "demo::unit", "demo::function", "UNIT_TO_FUNCTION"),
            relationship("demo::rel-endpoint", "demo", "demo::endpoint", "remote::endpoint", "MATCHES"),
            relationship("other::rel-unit", "other", "other::unit", "other::function", "UNIT_TO_FUNCTION"),
            relationship("other::rel-endpoint", "other", "other::endpoint", "remote::endpoint", "MATCHES")));

        repository.deleteById("demo", "demo::unit");
        repository.deleteById("demo", "demo::endpoint");

        assertThat(repository.findUnitsByProjectFilePath("demo", "src/A.java")).isEmpty();
        assertThat(repository.findEndpointsByProjectFilePath("demo", "src/A.java")).isEmpty();
        assertThat(repository.findOutgoingRelationships("demo", "demo::unit", null)).isEmpty();
        assertThat(repository.findOutgoingRelationships("demo", "demo::endpoint", null)).isEmpty();

        assertThat(repository.findUnitsByProjectFilePath("other", "src/A.java")).extracting(CodeUnitDO::getId)
            .containsExactly("other::unit");
        assertThat(repository.findEndpointsByProjectFilePath("other", "src/A.java")).extracting(CodeEndpointDO::getId)
            .containsExactly("other::endpoint");
        assertThat(repository.findOutgoingRelationships("other", "other::unit", null)).extracting(CodeRelationshipDO::getId)
            .containsExactly("other::rel-unit");
        assertThat(repository.findOutgoingRelationships("other", "other::endpoint", null)).extracting(CodeRelationshipDO::getId)
            .containsExactly("other::rel-endpoint");
    }

    @Test
    void acceptsNullBatchInputsAsNoOp() {
        repository.insertPackagesBatch(null);
        repository.updatePackagesBatch(null);
        repository.insertUnitsBatch(null);
        repository.updateUnitsBatch(null);
        repository.insertFunctionsBatch(null);
        repository.updateFunctionsBatch(null);
        repository.insertEndpointsBatch(null);
        repository.updateEndpointsBatch(null);
        repository.insertRelationshipsBatch(null);

        assertThat(repository.findExistingPackagesByQualifiedNames("demo", List.of("x"))).isEmpty();
        assertThat(repository.findUnitsByProjectFilePath("demo", "src/A.java")).isEmpty();
        assertThat(repository.findFunctionsByProjectFilePath("demo", "src/A.java")).isEmpty();
        assertThat(repository.findEndpointsByProjectFilePath("demo", "src/A.java")).isEmpty();
        assertThat(repository.findOutgoingRelationships("demo", "x", null)).isEmpty();
    }

    private CodePackageDO pkg(String id, String projectName, String qualifiedName) {
        CodePackageDO pkg = new CodePackageDO();
        pkg.setId(id);
        pkg.setProjectName(projectName);
        pkg.setQualifiedName(qualifiedName);
        return pkg;
    }

    private CodeUnitDO unit(String id, String projectName, String projectFilePath) {
        CodeUnitDO unit = new CodeUnitDO();
        unit.setId(id);
        unit.setProjectName(projectName);
        unit.setQualifiedName(id);
        unit.setProjectFilePath(projectFilePath);
        return unit;
    }

    private CodeFunctionDO function(String id, String projectName, String projectFilePath) {
        CodeFunctionDO function = new CodeFunctionDO();
        function.setId(id);
        function.setProjectName(projectName);
        function.setQualifiedName(id);
        function.setProjectFilePath(projectFilePath);
        return function;
    }

    private CodeEndpointDO endpoint(String id, String projectName, String projectFilePath, String direction) {
        CodeEndpointDO endpoint = new CodeEndpointDO();
        endpoint.setId(id);
        endpoint.setProjectName(projectName);
        endpoint.setProjectFilePath(projectFilePath);
        endpoint.setDirection(direction);
        endpoint.setMatchIdentity("GET /users");
        return endpoint;
    }

    private CodeRelationshipDO relationship(String id, String projectName, String from, String to, String type) {
        CodeRelationshipDO relationship = new CodeRelationshipDO();
        relationship.setId(id);
        relationship.setProjectName(projectName);
        relationship.setFromNodeId(from);
        relationship.setToNodeId(to);
        relationship.setRelationshipType(type);
        return relationship;
    }
}
