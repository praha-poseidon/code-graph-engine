package com.poseidon.codegraph.app;

import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.parser.process.ProcessCodeGraphParser;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "PHP_CODE_GRAPH_CLI", matches = ".+")
class PhpProcessParserEndToEndTest {

    private static final String PROJECT = "php-process-e2e";

    @BeforeAll
    static void configureExternalParser() {
        System.setProperty("codegraph.parser.process.languages", "php");
        System.setProperty("codegraph.parser.process.php.command",
            "node '" + System.getenv("PHP_CODE_GRAPH_CLI") + "' --stdio");
        System.setProperty("codegraph.parser.process.timeoutSeconds", "30");
    }

    @AfterAll
    static void clearExternalParserConfig() {
        System.clearProperty("codegraph.parser.process.languages");
        System.clearProperty("codegraph.parser.process.php.command");
        System.clearProperty("codegraph.parser.process.timeoutSeconds");
    }

    @Test
    void appServiceWritesPhpGraphFromExternalProcessParser() {
        InMemoryCodeGraphRepository repository = new InMemoryCodeGraphRepository();
        IncrementalUpdateService service = new IncrementalUpdateService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        Path projectRoot = phpParserRoot().resolve("fixtures/basic");
        Path sourceFile = projectRoot.resolve("src/App.php");

        service.handleFileAdded(
            PROJECT,
            sourceFile.toString(),
            "src/App.php",
            "git@example/php-process-e2e.git",
            "main",
            new String[0],
            new String[] { projectRoot.resolve("src").toString() }
        );

        String packageId = PROJECT + "::pkg:app";
        String helperId = PROJECT + "::fn:app\\helper()";
        String runId = PROJECT + "::fn:app\\service::run()";

        assertThat(repository.findOutgoingRelationships(
                PROJECT, packageId, RelationshipType.PACKAGE_TO_UNIT.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .contains(
                PROJECT + "::unit:app.(namespace@src/app.php#1)",
                PROJECT + "::unit:app\\service"
            );
        assertThat(repository.findOutgoingRelationships(
                PROJECT, runId, RelationshipType.CALLS.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .contains(helperId);
        assertThat(repository.findFunctionsByProjectFilePath(PROJECT, "src/App.php"))
            .extracting(function -> function.getId())
            .contains(helperId, runId);
    }

    @Test
    void processProtocolAcceptsPhpStaticExtractEndpoints() {
        Path projectRoot = phpParserRoot().resolve("fixtures/basic");
        Path sourceFile = projectRoot.resolve("src/App.php");
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "php",
            List.of("node", System.getenv("PHP_CODE_GRAPH_CLI"), "--stdio"),
            Duration.ofSeconds(30)
        );

        var delta = parser.parse(new ParseRequest(
            PROJECT,
            "php",
            projectRoot.toString(),
            List.of(sourceFile.toString()),
            List.of(projectRoot.resolve("src").toString()),
            List.of(),
            "git@example/php-process-e2e.git",
            "main",
            ChangeType.SOURCE_ADDED,
            List.of(),
            List.of(),
            Map.of(),
            Map.of("staticExtractPresetRules", true)
        ));

        assertThat(delta.endpoints())
            .extracting(endpoint -> endpoint.getMatchIdentity())
            .contains("HTTP:GET:/api/run", "HTTP:POST:/configured/run", "REDIS:demo:key");
        assertThat(delta.relationships())
            .extracting(relationship -> relationship.getRelationshipType())
            .contains(RelationshipType.ENDPOINT_TO_FUNCTION, RelationshipType.FUNCTION_TO_ENDPOINT);
    }

    private static Path phpParserRoot() {
        Path cli = Path.of(System.getenv("PHP_CODE_GRAPH_CLI")).toAbsolutePath();
        return cli.getParent().getParent();
    }
}
