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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FRONTEND_CODE_GRAPH_CLI", matches = ".+")
class FrontendProcessParserEndToEndTest {

    private static final String PROJECT = "frontend-process-e2e";
    private static final String TYPE_PROJECT = "frontend-type-relations-e2e";
    private static final String FETCH_RULE = """
        rule "Frontend E2E Fetch"
        fact frontend_api_call

        find call fetch

        let path =
          from argument[0] take value

        let method =
          from argument[1] take attr(method)
        fallback "GET"

        build {
          client: "fetch"
          method: method | normalize upper
          path: path | normalize httpPath
        }
        """;
    private static final String REQUEST_RULE = """
        rule "Frontend E2E Request Methods"
        fact frontend_api_call

        find call [get,post,put,patch,delete,del]
        when call owner request

        let method =
          from call take method

        let path =
          from argument[0] take value

        build {
          client: "request"
          method: method | normalize upper
          path: path | normalize httpPath
        }
        """;

    @BeforeAll
    static void configureExternalParser() {
        System.setProperty("codegraph.parser.process.languages", "typescript javascript");
        System.setProperty("codegraph.parser.process.typescript.command",
            "node '" + System.getenv("FRONTEND_CODE_GRAPH_CLI") + "' --stdio");
        System.setProperty("codegraph.parser.process.javascript.command",
            "node '" + System.getenv("FRONTEND_CODE_GRAPH_CLI") + "' --stdio");
        System.setProperty("codegraph.parser.process.timeoutSeconds", "30");
    }

    @AfterAll
    static void clearExternalParserConfig() {
        System.clearProperty("codegraph.parser.process.languages");
        System.clearProperty("codegraph.parser.process.typescript.command");
        System.clearProperty("codegraph.parser.process.javascript.command");
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
        add(service, PROJECT, projectRoot, "src/api/user.ts");
        add(service, PROJECT, projectRoot, "src/components/UserCard.jsx");
        add(service, PROJECT, projectRoot, "src/hooks/useUser.ts");
        add(service, PROJECT, projectRoot, "src/pages/UserPage.tsx");

        assertThat(repository.findEndpointsByProjectFilePath(PROJECT, "src/api/user.ts"))
            .extracting(CodeEndpointDO::getMatchIdentity)
            .contains("HTTP:GET:/api/users/{param}", "HTTP:POST:/api/users");

        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/pages/UserPage.tsx",
                RelationshipType.UNIT_TO_FUNCTION.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .contains(PROJECT + "::src/pages/UserPage.tsx::UserPage()");
        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/pages/UserPage.tsx::UserPage()",
                RelationshipType.CALLS.name()))
            .hasSize(1);
        assertThat(repository.findOutgoingRelationships(
                PROJECT,
                PROJECT + "::src/api/user.ts::createUser(payload: unknown)",
                RelationshipType.FUNCTION_TO_ENDPOINT.name()))
            .hasSize(1);
    }

    @Test
    void appServicePersistsExactTypescriptTypeAndCallRelationships() {
        InMemoryCodeGraphRepository repository = new InMemoryCodeGraphRepository();
        IncrementalUpdateService service = new IncrementalUpdateService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        Path projectRoot = frontendParserRoot().resolve("fixtures/type-relations");
        add(service, TYPE_PROJECT, projectRoot, "src/contracts.ts");
        add(service, TYPE_PROJECT, projectRoot, "src/other.ts");
        add(service, TYPE_PROJECT, projectRoot, "src/service.ts");
        add(service, TYPE_PROJECT, projectRoot, "src/page.ts");

        String userService = TYPE_PROJECT + "::src/service.ts::UserService";
        String baseService = TYPE_PROJECT + "::src/contracts.ts::BaseService";
        String apiClient = TYPE_PROJECT + "::src/contracts.ts::ApiClient";
        String wrongApiClient = TYPE_PROJECT + "::src/other.ts::ApiClient";
        String save = TYPE_PROJECT + "::src/service.ts::UserService.save()";
        String interfaceSave = TYPE_PROJECT + "::src/contracts.ts::ApiClient.save()";
        String handleSave = TYPE_PROJECT + "::src/page.ts::handleSave()";

        assertThat(repository.findOutgoingRelationships(
                TYPE_PROJECT, userService, RelationshipType.EXTENDS.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .containsExactly(baseService);
        assertThat(repository.findOutgoingRelationships(
                TYPE_PROJECT, userService, RelationshipType.IMPLEMENTS.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .containsExactly(apiClient)
            .doesNotContain(wrongApiClient);
        assertThat(repository.findOutgoingRelationships(
                TYPE_PROJECT, save, RelationshipType.OVERRIDES.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .containsExactly(interfaceSave);
        assertThat(repository.findOutgoingRelationships(
                TYPE_PROJECT, handleSave, RelationshipType.CALLS.name()))
            .extracting(relationship -> relationship.getToNodeId())
            .containsExactly(save);
    }

    private static void add(
            IncrementalUpdateService service,
            String projectName,
            Path projectRoot,
            String projectFilePath) {
        service.handleFileAdded(
            projectName,
            projectRoot.resolve(projectFilePath).toString(),
            projectFilePath,
            "git@example/" + projectName + ".git",
            "main",
            new String[0],
            new String[] { projectRoot.resolve("src").toString() },
            List.of(FETCH_RULE, REQUEST_RULE),
            List.of()
        );
    }

    private static Path frontendParserRoot() {
        Path cli = Path.of(System.getenv("FRONTEND_CODE_GRAPH_CLI")).toAbsolutePath();
        return cli.getParent().getParent();
    }
}
