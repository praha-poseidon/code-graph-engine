package com.poseidon.codegraph.app;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FRONTEND_CODE_GRAPH_CLI", matches = ".+")
class FrontendProcessParserEndToEndTest {

    private static final String PROJECT = "frontend-process-e2e";

    @BeforeAll
    static void configureExternalParser() {
        System.setProperty("codegraph.parser.process.languages", "typescript");
        System.setProperty("codegraph.parser.process.typescript.command",
            "node '" + System.getenv("FRONTEND_CODE_GRAPH_CLI") + "' --stdio");
        System.setProperty("codegraph.parser.process.timeoutSeconds", "30");
    }

    @AfterAll
    static void clearExternalParserConfig() {
        System.clearProperty("codegraph.parser.process.languages");
        System.clearProperty("codegraph.parser.process.typescript.command");
        System.clearProperty("codegraph.parser.process.timeoutSeconds");
    }

    @Test
    void appServiceWritesFrontendGraphFromExternalProcessParser() {
        InMemoryCodeGraphRepository repository = new InMemoryCodeGraphRepository();
        IncrementalUpdateService service = new IncrementalUpdateService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        Path projectRoot = frontendParserRoot().resolve("fixtures/react-basic");
        Path sourceFile = projectRoot.resolve("src/pages/UserPage.tsx");

        service.handleFileAdded(
            PROJECT,
            sourceFile.toString(),
            "src/pages/UserPage.tsx",
            "git@example/frontend-process-e2e.git",
            "main",
            new String[0],
            new String[] { projectRoot.resolve("src").toString() }
        );

        assertThat(repository.findEndpointsByProjectFilePath(PROJECT, "src/api/user.ts"))
            .extracting(CodeEndpointDO::getMatchIdentity)
            .contains("HTTP:GET:/api/users/{param}", "HTTP:POST:/api/users");

        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/pages/UserPage.tsx",
                RelationshipType.IMPORTS.name()))
            .hasSize(3);
        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/pages/UserPage.tsx::UserPage",
                RelationshipType.RENDERS.name()))
            .hasSize(1);
        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/pages/UserPage.tsx::UserPage()",
                RelationshipType.USES_HOOK.name()))
            .hasSize(1);
        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/api/user.ts::createUser(payload: unknown)",
                RelationshipType.FUNCTION_TO_ENDPOINT.name()))
            .hasSize(1);
    }

    private static Path frontendParserRoot() {
        Path cli = Path.of(System.getenv("FRONTEND_CODE_GRAPH_CLI")).toAbsolutePath();
        return cli.getParent().getParent();
    }
}
