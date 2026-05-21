package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.RelationshipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JavaJdtCodeGraphParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesSpringMvcEndpointWithBuiltinRulesByDefault() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/example/UserController.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
            source,
            """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/api")
            class UserController {
                @GetMapping("/users/{id}")
                public String getUser(String id) {
                    return id;
                }
            }
            """);

        JavaJdtCodeGraphParser parser = new JavaJdtCodeGraphParser();

        var delta = parser.parse(new ParseRequest(
            "demo",
            "java",
            tempDir.toString(),
            List.of(source.toString()),
            List.of(tempDir.resolve("src/main/java").toString()),
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            Map.of("projectFilePath", "src/main/java/com/example/UserController.java")
        ));

        assertThat(delta.endpoints())
            .hasSize(1)
            .first()
            .isInstanceOfSatisfying(HttpEndpoint.class, endpoint -> {
                assertThat(endpoint.getDirection()).isEqualTo("inbound");
                assertThat(endpoint.getHttpMethod()).isEqualTo("GET");
                assertThat(endpoint.getPath()).isEqualTo("/api/users/{param}");
                assertThat(endpoint.getMatchIdentity()).isEqualTo("GET /api/users/{param}");
            });
    }

    @Test
    void parseRequestCanSupplyTraceRulesAndExternalValuesForEndpointExtraction() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/example/UserClient.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
            source,
            """
            package com.example;

            @interface ConfigRef {
                String value();
            }

            class RestTemplate {
                String getForObject(String url, Class<?> responseType) {
                    return null;
                }
            }

            class UserClient {
                @ConfigRef("${users.base-url}")
                private String baseUrl;

                private final RestTemplate restTemplate = new RestTemplate();

                String load(String id) {
                    return restTemplate.getForObject(baseUrl + "/users/" + id, String.class);
                }
            }
            """);

        JavaJdtCodeGraphParser parser = new JavaJdtCodeGraphParser();

        var delta = parser.parse(new ParseRequest(
            "demo",
            "java",
            tempDir.toString(),
            List.of(source.toString()),
            List.of(tempDir.resolve("src/main/java").toString()),
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(
                """
                trace "Custom ConfigRef Trace"

                from field
                when annotation @ConfigRef on field

                let rawValue =
                  from annotation on field @ConfigRef take attr(value)

                build {
                  namespace: "config"
                  lookup: rawValue | normalize placeholderLookup
                  default: rawValue | normalize placeholderDefault
                }
                """),
            Map.of("config", Map.of("users.base-url", List.of("http://users.example/api/v1"))),
            Map.of("projectFilePath", "src/main/java/com/example/UserClient.java")
        ));

        assertThat(delta.endpoints())
            .hasSize(1)
            .first()
            .isInstanceOfSatisfying(HttpEndpoint.class, endpoint -> {
                assertThat(endpoint.getDirection()).isEqualTo("outbound");
                assertThat(endpoint.getHttpMethod()).isEqualTo("GET");
                assertThat(endpoint.getPath()).isEqualTo("/api/v{version}/users/{param}");
                assertThat(endpoint.getMatchIdentity()).isEqualTo("GET /api/v{version}/users/{param}");
            });
    }

    @Test
    void filtersOnlyTrivialBeanAccessorsButKeepsBusinessSetMethods() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/poseidon/fixture/AccountFixture.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
            source,
            """
            package com.poseidon.fixture;

            class Account {
                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

                public void setStatus(String status) {
                    validate(status);
                    this.name = status;
                }

                private void validate(String value) {
                    if (value == null) {
                        throw new IllegalArgumentException();
                    }
                }
            }

            class Caller {
                void run(Account account) {
                    account.getName();
                    account.setName("n");
                    account.setStatus("active");
                }
            }
            """);

        JavaJdtCodeGraphParser parser = new JavaJdtCodeGraphParser();

        var delta = parser.parse(new ParseRequest(
            "demo",
            "java",
            tempDir.toString(),
            List.of(source.toString()),
            List.of(tempDir.resolve("src/main/java").toString()),
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            Map.of("projectFilePath", "src/main/java/com/poseidon/fixture/AccountFixture.java")
        ));

        assertThat(delta.relationships())
            .filteredOn(relationship -> relationship.getRelationshipType() == RelationshipType.CALLS)
            .extracting(relationship -> relationship.getToNodeId())
            .anyMatch(id -> id.contains("Account.setStatus(java.lang.String)"))
            .noneMatch(id -> id.contains("Account.getName()"))
            .noneMatch(id -> id.contains("Account.setName(java.lang.String)"));
    }

    @Test
    void directJdtSourceParserExtractsPackagesUnitsFunctionsAndCalls() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path source = sourceRoot.resolve("com/poseidon/demo/DirectParserFixture.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
            source,
            """
            package com.poseidon.demo;

            class Target {
                static void ping() {
                }

                void pong() {
                }
            }

            class Caller {
                void run(Target target) {
                    Target.ping();
                    target.pong();
                }
            }
            """);

        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[] { sourceRoot.toString() },
            ProcessorRegistry.createCoreOnly()
        );
        String projectFile = "src/main/java/com/poseidon/demo/DirectParserFixture.java";

        assertThat(parser.parsePackages(source.toString(), "demo", projectFile))
            .extracting("qualifiedName")
            .containsExactly("com.poseidon.demo");
        assertThat(parser.parseUnits(source.toString(), "demo", projectFile))
            .extracting("unitType")
            .containsOnly("class");
        assertThat(parser.parseFunctions(source.toString(), "demo", projectFile))
            .extracting("name")
            .contains("ping", "pong", "run");

        assertThat(parser.parseRelationships(source.toString(), "demo", projectFile))
            .filteredOn(relationship -> relationship.getRelationshipType() == RelationshipType.CALLS)
            .satisfiesExactlyInAnyOrder(
                relationship -> {
                    assertThat(relationship.getToNodeId()).contains("Target.ping()");
                    assertThat(relationship.getCallType()).isEqualTo("static");
                },
                relationship -> {
                    assertThat(relationship.getToNodeId()).contains("Target.pong()");
                    assertThat(relationship.getCallType()).isEqualTo("virtual");
                }
            );
    }
}
