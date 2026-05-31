package com.poseidon.codegraph.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "code-graph.storage.type=memory")
class CodeGraphAppEndToEndTest {

    private static final String PROJECT = "app-e2e";
    private static final String PROJECT_FILE = "src/main/java/com/example/ApiController.java";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryCodeGraphRepository repository;

    @TempDir
    Path tempDir;

    @Test
    void apiCreateUpdateAndDeleteWritesExpectedGraphToMemoryStorage() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path sourceFile = sourceRoot.resolve("com/example/ApiController.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source("/api/v1/users/{id}?debug=true", "getUser"));

        mockMvc.perform(post("/api/code-graph/files/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(sourceFile, sourceRoot))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertGraph(sourceFile, "/api/v{version}/users/{param}", "GET /api/v{version}/users/{param}", "getUser");

        Files.writeString(sourceFile, source("/api/v2/orders/{orderId}", "getOrder"));

        mockMvc.perform(put("/api/code-graph/files/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(sourceFile, sourceRoot))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertGraph(sourceFile, "/api/v{version}/orders/{param}", "GET /api/v{version}/orders/{param}", "getOrder");
        assertThat(repository.findEndpointsByMatchIdentity("GET /api/v{version}/users/{param}", "inbound"))
            .filteredOn(endpoint -> PROJECT.equals(endpoint.getProjectName()))
            .isEmpty();
        assertThat(repository.findExistingFunctionsByQualifiedNames(
                PROJECT,
                List.of(PROJECT + "::fn:com.example.ApiController.getUser()")))
            .isEmpty();

        mockMvc.perform(delete("/api/code-graph/files/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(sourceFile, sourceRoot))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertThat(repository.findUnitsByProjectFilePath(PROJECT, PROJECT_FILE)).isEmpty();
        assertThat(repository.findFunctionsByProjectFilePath(PROJECT, PROJECT_FILE)).isEmpty();
        assertThat(repository.findEndpointsByProjectFilePath(PROJECT, PROJECT_FILE)).isEmpty();
    }

    @Test
    void apiReturnsClearErrorWhenExternalEndpointSerIsInvalid() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path sourceFile = sourceRoot.resolve("com/example/ApiController.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source("/api/users", "getUser"));

        Map<String, Object> request = new java.util.LinkedHashMap<>(request(sourceFile, sourceRoot));
        request.put("projectName", "app-e2e-invalid-ser");
        request.put("endpointRuleSources", List.of("broken rule"));

        mockMvc.perform(post("/api/code-graph/files/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid SER syntax")));
    }

    @Test
    void apiImportsFrontendParserGraphJsonWithoutExplicitDeltaScope() throws Exception {
        Map<String, Object> rawFrontendGraph = Map.of(
            "packages", List.of(Map.of(
                "id", "frontend-demo",
                "name", "frontend-demo",
                "qualifiedName", "frontend-demo",
                "language", "unknown",
                "projectFilePath", ".",
                "packagePath", "."
            )),
            "units", List.of(Map.of(
                "id", "frontend-demo#src/pages/UserPage.tsx",
                "name", "UserPage.tsx",
                "qualifiedName", "frontend-demo#src/pages/UserPage.tsx",
                "language", "typescript",
                "projectFilePath", "src/pages/UserPage.tsx",
                "startLine", 1,
                "endLine", 20,
                "unitType", "module",
                "packageId", "frontend-demo"
            )),
            "functions", List.of(Map.of(
                "id", "frontend-demo#src/pages/UserPage.tsx::saveUser()",
                "name", "saveUser",
                "qualifiedName", "frontend-demo#src/pages/UserPage.tsx::saveUser()",
                "language", "typescript",
                "projectFilePath", "src/pages/UserPage.tsx",
                "startLine", 3,
                "endLine", 8,
                "signature", "saveUser()"
            )),
            "endpoints", List.of(Map.ofEntries(
                Map.entry("id", "frontend-demo#src/pages/UserPage.tsx::endpoint:UI:CLICK:button:Save"),
                Map.entry("name", "UI:CLICK:button:Save"),
                Map.entry("qualifiedName", "frontend-demo#src/pages/UserPage.tsx::endpoint:UI:CLICK:button:Save"),
                Map.entry("language", "typescript"),
                Map.entry("projectFilePath", "src/pages/UserPage.tsx"),
                Map.entry("startLine", 5),
                Map.entry("endLine", 5),
                Map.entry("endpointType", "UI"),
                Map.entry("direction", "inbound"),
                Map.entry("isExternal", false),
                Map.entry("parseLevel", "full"),
                Map.entry("matchIdentity", "UI:CLICK:button:Save"),
                Map.entry("uiEvent", "click"),
                Map.entry("uiElement", "button"),
                Map.entry("uiText", "Save")
            )),
            "relationships", List.of(
                Map.of(
                    "id", "rel-package-unit",
                    "fromNodeId", "frontend-demo",
                    "toNodeId", "frontend-demo#src/pages/UserPage.tsx",
                    "relationshipType", "PACKAGE_TO_UNIT",
                    "language", "typescript"
                ),
                Map.of(
                    "id", "rel-unit-function",
                    "fromNodeId", "frontend-demo#src/pages/UserPage.tsx",
                    "toNodeId", "frontend-demo#src/pages/UserPage.tsx::saveUser()",
                    "relationshipType", "UNIT_TO_FUNCTION",
                    "language", "typescript"
                ),
                Map.of(
                    "id", "rel-endpoint-function",
                    "fromNodeId", "frontend-demo#src/pages/UserPage.tsx::endpoint:UI:CLICK:button:Save",
                    "toNodeId", "frontend-demo#src/pages/UserPage.tsx::saveUser()",
                    "relationshipType", "ENDPOINT_TO_FUNCTION",
                    "language", "typescript"
                )
            )
        );

        mockMvc.perform(post("/api/code-graph/delta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rawFrontendGraph)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertThat(repository.findUnitsByProjectFilePath("frontend-demo", "src/pages/UserPage.tsx"))
            .extracting(CodeUnitDO::getQualifiedName)
            .containsExactly("frontend-demo#src/pages/UserPage.tsx");
        assertThat(repository.findEndpointsByProjectFilePath("frontend-demo", "src/pages/UserPage.tsx"))
            .extracting(CodeEndpointDO::getEndpointType)
            .containsExactly("UI");
        assertThat(repository.findOutgoingRelationships(
                "frontend-demo",
                "frontend-demo::frontend-demo#src/pages/UserPage.tsx::endpoint:UI:CLICK:button:Save",
                "ENDPOINT_TO_FUNCTION"))
            .extracting(CodeRelationshipDO::getToNodeId)
            .containsExactly("frontend-demo::frontend-demo#src/pages/UserPage.tsx::saveUser()");
    }

    private void assertGraph(Path sourceFile, String path, String matchIdentity, String functionName) {
        assertThat(repository.findUnitsByProjectFilePath(PROJECT, PROJECT_FILE))
            .extracting(CodeUnitDO::getQualifiedName)
            .contains("com.example.ApiController", "com.example.RouteGet");
        assertThat(repository.findFunctionsByProjectFilePath(PROJECT, PROJECT_FILE))
            .extracting(CodeFunctionDO::getQualifiedName)
            .containsExactly("com.example.ApiController." + functionName + "()");

        CodeEndpointDO endpoint = repository.findEndpointsByProjectFilePath(PROJECT, PROJECT_FILE).stream()
            .filter(candidate -> matchIdentity.equals(candidate.getMatchIdentity()))
            .findFirst()
            .orElseThrow();
        assertThat(endpoint.getDirection()).isEqualTo("inbound");
        assertThat(endpoint.getEndpointType()).isEqualTo("HTTP");
        assertThat(endpoint.getPath()).isEqualTo(path);
        assertThat(endpoint.getProjectFilePath()).isEqualTo(PROJECT_FILE);

        assertThat(repository.findOutgoingRelationships(PROJECT, endpoint.getId(), "ENDPOINT_TO_FUNCTION"))
            .extracting(CodeRelationshipDO::getToNodeId)
            .containsExactly(PROJECT + "::fn:com.example.ApiController." + functionName + "()");
        assertThat(repository.findOutgoingRelationships(PROJECT, PROJECT + "::unit:com.example.ApiController", "UNIT_TO_FUNCTION"))
            .extracting(CodeRelationshipDO::getToNodeId)
            .containsExactly(PROJECT + "::fn:com.example.ApiController." + functionName + "()");
    }

    private Map<String, Object> request(Path sourceFile, Path sourceRoot) {
        return Map.of(
            "projectName", PROJECT,
            "absoluteFilePath", sourceFile.toString(),
            "projectFilePath", PROJECT_FILE,
            "gitRepoUrl", "git@example/app-e2e.git",
            "gitBranch", "main",
            "classpathEntries", List.of(),
            "sourcepathEntries", List.of(sourceRoot.toString()),
            "endpointRuleSources", List.of(routeRule()),
            "traceRuleSources", List.of());
    }

    private String source(String routePath, String methodName) {
        return """
            package com.example;

            @interface RouteGet {
                String value();
            }

            public class ApiController {
                @RouteGet("%s")
                public String %s() {
                    return "ok";
                }
            }
            """.formatted(routePath, methodName);
    }

    private String routeRule() {
        return """
            rule "Custom HTTP Inbound"
            endpoint HTTP inbound

            find method with annotation @RouteGet

            let httpMethod =
              from literal GET take value

            let path =
              from annotation on method @RouteGet take attr(value)

            build {
              httpMethod: httpMethod
              path: path | normalize slash | normalize pathVariable
            }
            """;
    }
}
