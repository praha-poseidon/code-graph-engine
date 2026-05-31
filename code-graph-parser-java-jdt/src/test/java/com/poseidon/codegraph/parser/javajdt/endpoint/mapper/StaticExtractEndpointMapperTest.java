package com.poseidon.codegraph.parser.javajdt.endpoint.mapper;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.endpoint.DbEndpoint;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import com.poseidon.codegraph.model.endpoint.MqEndpoint;
import com.poseidon.codegraph.model.endpoint.RedisEndpoint;
import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.rule.EndpointSpec;
import com.poseidon.javastatic.extract.rule.FactSpec;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StaticExtractEndpointMapperTest {

    @Test
    void returnsNullForInvalidResultOrUnsupportedEndpointType() {
        CompilationUnit cu = parse("package demo; class App { void run() {} }");
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

        assertThat(StaticExtractEndpointMapper.toCodeEndpoint(null, cu, type, "src/App.java")).isNull();
        assertThat(StaticExtractEndpointMapper.toCodeEndpoint(result(null, Map.of(), null), cu, type, "src/App.java")).isNull();
        assertThat(StaticExtractEndpointMapper.toCodeEndpoint(result(rule("HTTP", "inbound"), null, null), cu, type, "src/App.java")).isNull();
        assertThat(StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("CUSTOM", "inbound"), Map.of("path", "/x"), null), cu, type, "src/App.java")).isNull();
    }

    @Test
    void mapsHttpEndpointAndDefaultsMethodWhenMissing() {
        CompilationUnit cu = parse("package demo; class App { void run() {} }");
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        MethodDeclaration method = (MethodDeclaration) type.getMethods()[0];

        CodeEndpoint endpoint = StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("HTTP", "inbound"), Map.of("path", "/api/v1/users/{id}", "unknownField", "ignored"), method),
            cu,
            type,
            "src/App.java");

        HttpEndpoint http = assertInstanceOf(HttpEndpoint.class, endpoint);
        assertThat(http.getHttpMethod()).isEqualTo("UNKNOWN");
        assertThat(http.getPath()).isEqualTo("/api/v{version}/users/{param}");
        assertThat(http.getMatchIdentity()).isEqualTo("UNKNOWN /api/v{version}/users/{param}");
        assertThat(http.getFunction().getId()).isEqualTo("fn:demo.App.run()");
        assertThat(http.getStartLine()).isEqualTo(1);
        assertThat(http.getProjectFilePath()).isEqualTo("src/App.java");
        assertThat(http.getIsExternal()).isFalse();
    }

    @Test
    void dropsHttpEndpointWithoutPath() {
        CompilationUnit cu = parse("package demo; class App { void run() {} }");
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

        assertThat(StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("HTTP", "outbound"), Map.of("httpMethod", "GET"), type.getMethods()[0]),
            cu,
            type,
            "src/App.java")).isNull();
    }

    @Test
    void mapsMqRedisAndDbEndpointFields() {
        CompilationUnit cu = parse("package demo; class App { void run() {} }");
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

        MqEndpoint mq = assertInstanceOf(MqEndpoint.class, StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("MQ", "outbound"), Map.of("topic", "orders", "operation", "PRODUCE", "brokerType", "KAFKA"), null),
            cu,
            type,
            "src/App.java"));
        assertThat(mq.getTopic()).isEqualTo("orders");
        assertThat(mq.getOperation()).isEqualTo("PRODUCE");
        assertThat(mq.getBrokerType()).isEqualTo("KAFKA");
        assertThat(mq.getMatchIdentity()).isEqualTo("MQ:orders");
        assertThat(mq.getIsExternal()).isTrue();

        RedisEndpoint redis = assertInstanceOf(RedisEndpoint.class, StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("REDIS", "outbound"), Map.of("keyPattern", "user:*", "command", "GET", "dataStructure", "STRING"), null),
            cu,
            type,
            "src/App.java"));
        assertThat(redis.getKeyPattern()).isEqualTo("user:*");
        assertThat(redis.getCommand()).isEqualTo("GET");
        assertThat(redis.getDataStructure()).isEqualTo("STRING");
        assertThat(redis.getMatchIdentity()).isEqualTo("REDIS:user:*");

        DbEndpoint db = assertInstanceOf(DbEndpoint.class, StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("DB", "outbound"), Map.of("tableName", "users", "dbOperation", "SELECT"), null),
            cu,
            type,
            "src/App.java"));
        assertThat(db.getTableName()).isEqualTo("users");
        assertThat(db.getDbOperation()).isEqualTo("SELECT");
        assertThat(db.getMatchIdentity()).isEqualTo("DB:users");
    }

    @Test
    void appliesBooleanFieldsWhenSetterExists() {
        CompilationUnit cu = parse("package demo; class App { void run() {} }");
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

        CodeEndpoint endpoint = StaticExtractEndpointMapper.toCodeEndpoint(
            result(rule("MQ", "outbound"), Map.of("topic", "orders", "isExternal", "false"), null),
            cu,
            type,
            "src/App.java");

        assertThat(endpoint.getIsExternal()).isFalse();
    }

    private static StaticExtractRule rule(String type, String direction) {
        return new StaticExtractRule(
            "test",
            null,
            true,
            0,
            new FactSpec("endpoint"),
            Map.of(),
            new EndpointSpec(type, direction),
            null,
            null,
            null);
    }

    private static StaticExtractResult result(StaticExtractRule rule, Map<String, String> fields, org.eclipse.jdt.core.dom.ASTNode anchor) {
        return new StaticExtractResult(rule, fields, 7, 9, "src/App.java", null, null, anchor);
    }

    @SuppressWarnings("deprecation")
    private static CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("App.java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(JavaCore.getOptions());
        return (CompilationUnit) parser.createAST(null);
    }
}
