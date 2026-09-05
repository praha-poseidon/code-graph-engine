package com.poseidon.codegraph.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poseidon.codegraph.app.task.AnalysisTaskStore;
import com.poseidon.codegraph.app.task.AnalysisWorkerIdentity;
import com.poseidon.codegraph.app.task.AnalysisWorkerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "code-graph.storage.type=memory",
    "code-graph.tasks.enabled=false",
    "code-graph.security.master-key=repository-api-test-key",
    "spring.datasource.url=jdbc:h2:mem:repository-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.sql.init.mode=always"
})
class RepositoryConfigApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AnalysisTaskStore taskStore;

    @Autowired
    private AnalysisWorkerStore workerStore;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM analysis_task");
        jdbc.update("DELETE FROM repository_config");
    }

    @Test
    void repositoryConfigurationAndAnalysisTaskArePersisted() throws Exception {
        String createResponse = mockMvc.perform(post("/api/config/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "gitRepoUrl", "https://github.com/example/payment-service.git",
                    "gitBranch", "main",
                    "languages", List.of("java", "go"),
                    "authType", "ACCESS_TOKEN",
                    "accessToken", "plain-token",
                    "endpointRuleSources", List.of("HTTP GET /payments")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("payment-service"))
            .andExpect(jsonPath("$.data.languages[0]").value("java"))
            .andExpect(jsonPath("$.data.hasAccessToken").value(true))
            .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse).path("data");
        long repositoryId = created.path("id").asLong();
        String storedToken = jdbc.queryForObject(
            "SELECT access_token FROM repository_config WHERE id = ?", String.class, repositoryId);
        assertThat(storedToken).startsWith("enc:v1:").doesNotContain("plain-token");

        String taskResponse = mockMvc.perform(post("/api/config/projects/{id}/analyze", repositoryId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andReturn().getResponse().getContentAsString();

        String taskId = objectMapper.readTree(taskResponse).path("data").path("id").asText();
        assertThat(taskStore.claimNext("api-test", Duration.ofSeconds(30))).isPresent();
        assertThat(taskStore.recoverExpiredLeases()).isZero();

        mockMvc.perform(get("/api/tasks").param("repositoryId", Long.toString(repositoryId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].status").value("RUNNING"));

        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].leaseOwner").value("api-test"));

        workerStore.register(new AnalysisWorkerIdentity("api-worker"));
        workerStore.heartbeat("api-worker", taskId);
        mockMvc.perform(get("/api/workers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].workerId").value("api-worker"))
            .andExpect(jsonPath("$.data[0].activeTaskId").value(taskId));

        assertThat(taskStore.succeed(taskId, "api-test", 0)).isTrue();

        mockMvc.perform(put("/api/config/projects/{id}", repositoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "gitRepoUrl", "https://github.com/example/payment-service.git",
                    "gitBranch", "release",
                    "languages", List.of("go"),
                    "authType", "NONE",
                    "endpointRuleSources", List.of()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hasAccessToken").value(false))
            .andExpect(jsonPath("$.data.status").value("done"))
            .andExpect(jsonPath("$.data.gitBranch").value("release"));

        assertThat(jdbc.queryForObject(
            "SELECT access_token FROM repository_config WHERE id = ?", String.class, repositoryId)).isNull();

        String canceledTaskId = taskStore.enqueue(repositoryId).id();
        mockMvc.perform(post("/api/tasks/{taskId}/cancel", canceledTaskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.cancelRequested").value(true));
    }
}
