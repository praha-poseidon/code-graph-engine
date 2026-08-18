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

@EnabledIfEnvironmentVariable(named = "PYTHON_CODE_GRAPH_CLI", matches = ".+")
class PythonProcessParserEndToEndTest {

    private static final String PROJECT = "python-process-e2e";

    @BeforeAll
    static void configureExternalParser() {
        System.setProperty("codegraph.parser.process.languages", "python");
        System.setProperty("codegraph.parser.process.python.command",
            "python3 '" + System.getenv("PYTHON_CODE_GRAPH_CLI") + "' --stdio");
        System.setProperty("codegraph.parser.process.timeoutSeconds", "30");
    }

    @AfterAll
    static void clearExternalParserConfig() {
        System.clearProperty("codegraph.parser.process.languages");
        System.clearProperty("codegraph.parser.process.python.command");
        System.clearProperty("codegraph.parser.process.timeoutSeconds");
    }

    @Test
    void appServiceWritesPythonGraphFromExternalProcessParser() {
        InMemoryCodeGraphRepository repository = new InMemoryCodeGraphRepository();
        IncrementalUpdateService service = new IncrementalUpdateService(
            repository, repository, repository, repository, repository
        );
        Path projectRoot = pythonParserRoot().resolve("fixtures/basic");
        Path sourceFile = projectRoot.resolve("app.py");

        service.handleFileAdded(
            PROJECT,
            sourceFile.toString(),
            "app.py",
            "git@example/python-process-e2e.git",
            "main",
            new String[0],
            new String[] { projectRoot.toString() }
        );

        String runId = PROJECT + "::fn:app.run()";
        String helperId = PROJECT + "::fn:app.helper()";
        assertThat(repository.findFunctionsByProjectFilePath(PROJECT, "app.py"))
            .extracting(function -> function.getId())
            .contains(runId, helperId);
        assertThat(repository.findOutgoingRelationships(PROJECT, runId, RelationshipType.CALLS.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .contains(helperId);
    }

    @Test
    void processProtocolAcceptsPythonEndpoints() {
        Path projectRoot = pythonParserRoot().resolve("fixtures/basic");
        Path sourceFile = projectRoot.resolve("app.py");
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "python",
            List.of("python3", System.getenv("PYTHON_CODE_GRAPH_CLI"), "--stdio"),
            Duration.ofSeconds(30)
        );

        var delta = parser.parse(new ParseRequest(
            PROJECT,
            "python",
            projectRoot.toString(),
            List.of(sourceFile.toString()),
            List.of(projectRoot.toString()),
            List.of(),
            "git@example/python-process-e2e.git",
            "main",
            ChangeType.SOURCE_ADDED,
            List.of(),
            List.of(),
            Map.of(),
            Map.of("staticExtractPresetRules", true)
        ));

        assertThat(delta.endpoints())
            .extracting(endpoint -> endpoint.getMatchIdentity())
            .contains("HTTP:GET:/api/run");
        assertThat(delta.relationships())
            .extracting(relationship -> relationship.getRelationshipType())
            .contains(RelationshipType.ENDPOINT_TO_FUNCTION);
    }

    private static Path pythonParserRoot() {
        Path cli = Path.of(System.getenv("PYTHON_CODE_GRAPH_CLI")).toAbsolutePath();
        return cli.getParent().getParent();
    }
}
