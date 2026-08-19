package com.poseidon.codegraph.app;

import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.parser.process.ProcessCodeGraphParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "KOTLIN_CODE_GRAPH_CLI", matches = ".+")
class KotlinProcessParserEndToEndTest {

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
}
