package com.poseidon.codegraph.engine.application.converter;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodePackageDO;
import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.EndpointType;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.endpoint.DbEndpoint;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import com.poseidon.codegraph.model.endpoint.MqEndpoint;
import com.poseidon.codegraph.model.endpoint.RedisEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class CodeGraphConverterTest {

    @Test
    void convertsPackageUnitFunctionAndRelationshipBothWays() {
        CodePackage pkg = new CodePackage();
        pkg.setId("pkg:demo");
        pkg.setName("demo");
        pkg.setQualifiedName("com.demo");
        pkg.setLanguage("java");
        pkg.setProjectName("app");
        pkg.setProjectFilePath("src/main/java/com/demo/App.java");
        pkg.setPackagePath("com/demo");

        CodePackageDO pkgDO = CodeGraphConverter.toDO(pkg);
        assertEquals("com/demo", pkgDO.getPackagePath());
        assertEquals("com.demo", CodeGraphConverter.toDomain(pkgDO).getQualifiedName());

        CodeUnit unit = new CodeUnit();
        unit.setId("unit:App");
        unit.setName("App");
        unit.setQualifiedName("com.demo.App");
        unit.setLanguage("java");
        unit.setProjectName("app");
        unit.setProjectFilePath(pkg.getProjectFilePath());
        unit.setGitRepoUrl("git@example/app.git");
        unit.setGitBranch("main");
        unit.setStartLine(1);
        unit.setEndLine(20);
        unit.setUnitType("class");
        unit.setModifiers(List.of("public"));
        unit.setIsAbstract(false);
        unit.setPackageId(pkg.getId());

        CodeUnitDO unitDO = CodeGraphConverter.toDO(unit);
        assertEquals("class", unitDO.getUnitType());
        assertEquals(List.of("public"), CodeGraphConverter.toDomain(unitDO).getModifiers());

        CodeFunction function = new CodeFunction();
        function.setId("fn:App.run()");
        function.setName("run");
        function.setQualifiedName("com.demo.App.run()");
        function.setLanguage("java");
        function.setProjectName("app");
        function.setProjectFilePath(pkg.getProjectFilePath());
        function.setGitRepoUrl("git@example/app.git");
        function.setGitBranch("main");
        function.setStartLine(3);
        function.setEndLine(8);
        function.setSignature("run():void");
        function.setReturnType("void");
        function.setModifiers(List.of("public"));
        function.setIsStatic(true);
        function.setIsAsync(false);
        function.setIsConstructor(false);
        function.setIsPlaceholder(false);

        CodeFunctionDO functionDO = CodeGraphConverter.toDO(function);
        assertEquals("run():void", functionDO.getSignature());
        assertEquals(Boolean.FALSE, CodeGraphConverter.toDomain(functionDO).getIsPlaceholder());

        CodeRelationship relationship = new CodeRelationship();
        relationship.setId("rel:1");
        relationship.setFromNodeId(function.getId());
        relationship.setToNodeId("fn:Target.call()");
        relationship.setRelationshipType(RelationshipType.CALLS);
        relationship.setLineNumber(5);
        relationship.setCallType("virtual");
        relationship.setLanguage("java");
        relationship.setProjectName("app");

        CodeRelationshipDO relationshipDO = CodeGraphConverter.toDO(relationship);
        assertEquals("CALLS", relationshipDO.getRelationshipType());
        assertEquals(RelationshipType.CALLS, CodeGraphConverter.toDomain(relationshipDO).getRelationshipType());
    }

    @Test
    void convertsEndpointSubtypesBothWays() {
        assertHttpRoundTrip();
        assertMqRoundTrip();
        assertRedisRoundTrip();
        assertDbRoundTrip();
    }

    @Test
    void convertsEndpointDoByTypeAndFallsBackForUnknownType() {
        CodeEndpointDO httpDO = baseEndpointDO("HTTP");
        httpDO.setHttpMethod("GET");
        httpDO.setPath("/api/users");
        httpDO.setNormalizedPath("/api/users");

        CodeEndpoint http = CodeGraphConverter.toDomain(httpDO);
        HttpEndpoint typedHttp = assertInstanceOf(HttpEndpoint.class, http);
        assertEquals("GET", typedHttp.getHttpMethod());
        assertEquals("GET /api/users", typedHttp.computeMatchIdentity());

        CodeEndpointDO unknownDO = baseEndpointDO("NOT_A_TYPE");
        CodeEndpoint unknown = CodeGraphConverter.toDomain(unknownDO);
        assertInstanceOf(HttpEndpoint.class, unknown);
        assertEquals(EndpointType.UNKNOWN, unknown.getEndpointType());
    }

    @Test
    void returnsNullForNullInput() {
        assertNull(CodeGraphConverter.toDomain((CodePackageDO) null));
        assertNull(CodeGraphConverter.toDomain((CodeUnitDO) null));
        assertNull(CodeGraphConverter.toDomain((CodeFunctionDO) null));
        assertNull(CodeGraphConverter.toDomain((CodeRelationshipDO) null));
        assertNull(CodeGraphConverter.toDomain((CodeEndpointDO) null));
        assertNull(CodeGraphConverter.toDO((CodePackage) null));
        assertNull(CodeGraphConverter.toDO((CodeUnit) null));
        assertNull(CodeGraphConverter.toDO((CodeFunction) null));
        assertNull(CodeGraphConverter.toDO((CodeRelationship) null));
        assertNull(CodeGraphConverter.toDO((CodeEndpoint) null));
    }

    private void assertHttpRoundTrip() {
        HttpEndpoint endpoint = new HttpEndpoint();
        fillCommonEndpoint(endpoint);
        endpoint.setHttpMethod("POST");
        endpoint.setPath("/api/orders");
        endpoint.setNormalizedPath("/api/orders");

        CodeEndpointDO dobj = CodeGraphConverter.toDO(endpoint);
        assertEquals("HTTP", dobj.getEndpointType());
        assertEquals("POST", dobj.getHttpMethod());
        assertEquals("/api/orders", assertInstanceOf(HttpEndpoint.class, CodeGraphConverter.toDomain(dobj)).getPath());
    }

    private void assertMqRoundTrip() {
        MqEndpoint endpoint = new MqEndpoint();
        fillCommonEndpoint(endpoint);
        endpoint.setTopic("orders.created");
        endpoint.setOperation("CONSUME");
        endpoint.setBrokerType("KAFKA");

        CodeEndpointDO dobj = CodeGraphConverter.toDO(endpoint);
        assertEquals("MQ", dobj.getEndpointType());
        assertEquals("orders.created", assertInstanceOf(MqEndpoint.class, CodeGraphConverter.toDomain(dobj)).getTopic());
    }

    private void assertRedisRoundTrip() {
        RedisEndpoint endpoint = new RedisEndpoint();
        fillCommonEndpoint(endpoint);
        endpoint.setKeyPattern("user:*");
        endpoint.setCommand("GET");
        endpoint.setDataStructure("STRING");

        CodeEndpointDO dobj = CodeGraphConverter.toDO(endpoint);
        assertEquals("REDIS", dobj.getEndpointType());
        assertEquals("user:*", assertInstanceOf(RedisEndpoint.class, CodeGraphConverter.toDomain(dobj)).getKeyPattern());
    }

    private void assertDbRoundTrip() {
        DbEndpoint endpoint = new DbEndpoint();
        fillCommonEndpoint(endpoint);
        endpoint.setTableName("users");
        endpoint.setDbOperation("SELECT");

        CodeEndpointDO dobj = CodeGraphConverter.toDO(endpoint);
        assertEquals("DB", dobj.getEndpointType());
        assertEquals("users", assertInstanceOf(DbEndpoint.class, CodeGraphConverter.toDomain(dobj)).getTableName());
    }

    private CodeEndpointDO baseEndpointDO(String endpointType) {
        CodeEndpointDO dobj = new CodeEndpointDO();
        dobj.setId("endpoint:1");
        dobj.setName("endpoint");
        dobj.setQualifiedName("endpoint");
        dobj.setProjectFilePath("src/App.java");
        dobj.setGitRepoUrl("git@example/app.git");
        dobj.setGitBranch("main");
        dobj.setLanguage("java");
        dobj.setProjectName("app");
        dobj.setStartLine(10);
        dobj.setEndLine(11);
        dobj.setEndpointType(endpointType);
        dobj.setDirection("inbound");
        dobj.setIsExternal(false);
        dobj.setServiceName("svc");
        dobj.setParseLevel("full");
        dobj.setTargetService("target");
        dobj.setMatchIdentity("match");
        return dobj;
    }

    private void fillCommonEndpoint(CodeEndpoint endpoint) {
        endpoint.setId("endpoint:1");
        endpoint.setName("endpoint");
        endpoint.setQualifiedName("endpoint");
        endpoint.setProjectFilePath("src/App.java");
        endpoint.setGitRepoUrl("git@example/app.git");
        endpoint.setGitBranch("main");
        endpoint.setLanguage("java");
        endpoint.setProjectName("app");
        endpoint.setStartLine(10);
        endpoint.setEndLine(11);
        endpoint.setDirection("inbound");
        endpoint.setIsExternal(false);
        endpoint.setServiceName("svc");
        endpoint.setParseLevel("full");
        endpoint.setTargetService("target");
        endpoint.setMatchIdentity("match");
    }
}
