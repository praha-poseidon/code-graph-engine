package com.poseidon.codegraph.app;

import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.parser.process.ProcessCodeGraphParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "KOTLIN_CODE_GRAPH_CLI", matches = ".+")
class KotlinProcessParserEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void processProtocolAcceptsNativeKotlinGraphAndCallerEndpoints() {
        Path parserRoot = Path.of(System.getenv("KOTLIN_CODE_GRAPH_CLI")).toAbsolutePath().getParent().getParent();
        Path projectRoot = parserRoot.resolve("testdata/basic");
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "kotlin",
            List.of(System.getenv("KOTLIN_CODE_GRAPH_CLI"), "--stdio"),
            Duration.ofSeconds(30)
        );

        var delta = parser.parse(new ParseRequest(
            "kotlin-process-e2e",
            "kotlin",
            projectRoot.toString(),
            List.of(projectRoot.resolve("App.kt").toString()),
            List.of(projectRoot.toString()),
            List.of(),
            "git@example/kotlin-process-e2e.git",
            "main",
            ChangeType.SOURCE_ADDED,
            List.of(parserRoot.resolve("testdata/rules/http.ser").toString()),
            List.of(),
            Map.of(),
            Map.of()
        ));

        assertThat(delta.scope().language()).isEqualTo("kotlin");
        assertThat(delta.functions()).extracting(function -> function.getName())
            .contains("helper", "run", "<file-init>");
        assertThat(delta.endpoints()).extracting(endpoint -> endpoint.getMatchIdentity())
            .containsExactly("HTTP:GET:/api/run");
        assertThat(delta.relationships()).extracting(relationship -> relationship.getRelationshipType())
            .contains(RelationshipType.CALLS, RelationshipType.ENDPOINT_TO_FUNCTION);
    }

    @Test
    void processProtocolPassesDependencyClasspathToKotlinBinding() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("project"));
        Path dependencySource = tempDir.resolve("dependency/dep/External.java");
        Path dependencyClasses = Files.createDirectories(tempDir.resolve("classes"));
        Files.createDirectories(dependencySource.getParent());
        Files.writeString(dependencySource, """
            package dep;
            public final class External {
                public String call(String value) { return value; }
            }
            """);
        int compilerResult = ToolProvider.getSystemJavaCompiler().run(
            null, null, null,
            "-proc:none",
            "-d", dependencyClasses.toString(),
            dependencySource.toString()
        );
        assertThat(compilerResult).isZero();
        Path kotlinSource = projectRoot.resolve("Use.kt");
        Files.writeString(kotlinSource, """
            package demo
            import dep.External
            fun run(external: External) = external.call("value")
            """);

        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "kotlin",
            List.of(System.getenv("KOTLIN_CODE_GRAPH_CLI"), "--stdio"),
            Duration.ofSeconds(30)
        );
        var delta = parser.parse(new ParseRequest(
            "kotlin-classpath-e2e",
            "kotlin",
            projectRoot.toString(),
            List.of(kotlinSource.toString()),
            List.of(projectRoot.toString()),
            List.of(dependencyClasses.toString()),
            null,
            null,
            ChangeType.SOURCE_ADDED,
            List.of(),
            List.of(),
            Map.of(),
            Map.of()
        ));

        assertThat(delta.relationships())
            .filteredOn(relationship -> relationship.getRelationshipType() == RelationshipType.CALLS)
            .extracting(relationship -> relationship.getToNodeId())
            .anyMatch(id -> id.startsWith("fn:dep.External::call(java.lang.String)"));
        assertThat(delta.diagnostics()).isEmpty();
    }
}
