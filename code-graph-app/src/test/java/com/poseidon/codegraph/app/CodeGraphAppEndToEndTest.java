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
