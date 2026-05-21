package com.poseidon.codegraph.parser.javajdt.endpoint.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalConfigValueScannerTest {

    @TempDir
    Path projectRoot;

    @Test
    void scansPropertiesAndYamlIntoConfigNamespace() throws Exception {
        Path javaFile = projectRoot.resolve("src/main/java/com/example/UserClient.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "package com.example; class UserClient {}");

        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(
                resources.resolve("application.properties"),
                """
                users.base-url=http://users.example
                users.timeout=1000
                """);
        Files.writeString(
                resources.resolve("application.yml"),
                """
                service:
                  path: /api/users
                  nested:
                    enabled: true
                """);

        Map<String, Map<String, List<String>>> values =
                ExternalConfigValueScanner.scan(
                        javaFile.toString(),
                        "src/main/java/com/example/UserClient.java");

        assertEquals(List.of("http://users.example"), values.get("config").get("users.base-url"));
        assertEquals(List.of("1000"), values.get("config").get("users.timeout"));
        assertEquals(List.of("/api/users"), values.get("config").get("service.path"));
        assertEquals(List.of("true"), values.get("config").get("service.nested.enabled"));
    }

    @Test
    void scansBootstrapYamlCommentsQuotedValuesAndDuplicateKeys() throws Exception {
        Path javaFile = projectRoot.resolve("module/src/main/java/com/example/UserClient.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(javaFile, "package com.example; class UserClient {}");

        Path resources = projectRoot.resolve("module/src/main/resources");
        Files.createDirectories(resources);
        Files.writeString(
                resources.resolve("bootstrap.yml"),
                """
                users:
                  base-url: "http://users.example#not-comment" # comment
                feature:
                  enabled: 'true'
                """);
        Files.writeString(
                resources.resolve("application.yaml"),
                """
                users:
                  base-url: http://override.example
                ---
                ignored-line-without-colon
                """);

        Map<String, Map<String, List<String>>> values =
                ExternalConfigValueScanner.scan(javaFile.toString(), null);

        assertEquals(
                List.of("http://override.example", "http://users.example#not-comment"),
                values.get("config").get("users.base-url"));
        assertEquals(List.of("true"), values.get("config").get("feature.enabled"));
    }

    @Test
    void returnsEmptyMapWhenProjectRootCannotBeResolved() {
        assertTrue(ExternalConfigValueScanner.scan(null, null).isEmpty());
    }
}
