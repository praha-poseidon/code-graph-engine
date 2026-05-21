package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RealisticJavaProjectParsingTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesProjectInternalTypesAndRelationshipsFromSourcepath() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path service = writeSource("com/poseidon/demo/UserService.java", """
            package com.poseidon.demo;

            interface UserApi {
                String find(String id);
            }

            public class UserService implements UserApi {
                @Override
                public String find(String id) {
                    return id;
                }
            }
            """);
        writeSource("com/poseidon/demo/UserStatus.java", """
            package com.poseidon.demo;

            public enum UserStatus {
                ACTIVE, DISABLED
            }
            """);
        Path controller = writeSource("com/poseidon/demo/UserController.java", """
            package com.poseidon.demo;

            public class UserController {
                public String get(String id) {
                    return new UserService().find(id);
                }
            }
            """);

        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[] {sourceRoot.toString()},
            ProcessorRegistry.createCoreOnly());

        CodeGraph serviceGraph = parser.parse(
            service.toString(),
            "demo",
            "src/main/java/com/poseidon/demo/UserService.java",
            "git@example/demo.git",
            "main");
        CodeGraph controllerGraph = parser.parse(
            controller.toString(),
            "demo",
            "src/main/java/com/poseidon/demo/UserController.java",
            "git@example/demo.git",
            "main");
        CodeGraph enumGraph = parser.parse(
            sourceRoot.resolve("com/poseidon/demo/UserStatus.java").toString(),
            "demo",
            "src/main/java/com/poseidon/demo/UserStatus.java",
            "git@example/demo.git",
            "main");

        assertThat(serviceGraph.getUnitsAsList())
            .extracting(unit -> unit.getQualifiedName() + ":" + unit.getUnitType())
            .contains("com.poseidon.demo.UserService:class", "com.poseidon.demo.UserApi:interface");
        assertThat(serviceGraph.getRelationshipsAsList())
            .anySatisfy(relationship -> {
                assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.IMPLEMENTS);
                assertThat(relationship.getFromNodeId()).isEqualTo(GraphIds.unitId("com.poseidon.demo.UserService"));
                assertThat(relationship.getToNodeId()).isEqualTo(GraphIds.unitId("com.poseidon.demo.UserApi"));
            })
            .anySatisfy(relationship -> {
                assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.OVERRIDES);
                assertThat(relationship.getFromNodeId()).isEqualTo(GraphIds.functionId("com.poseidon.demo.UserService.find(java.lang.String)"));
            });
        assertThat(controllerGraph.getRelationshipsAsList())
            .anySatisfy(relationship -> {
                assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.CALLS);
                assertThat(relationship.getFromNodeId()).isEqualTo(GraphIds.functionId("com.poseidon.demo.UserController.get(java.lang.String)"));
                assertThat(relationship.getToNodeId()).isEqualTo(GraphIds.functionId("com.poseidon.demo.UserService.find(java.lang.String)"));
            });
        assertThat(enumGraph.getUnitsAsList())
            .singleElement()
            .satisfies(unit -> {
                assertThat(unit.getQualifiedName()).isEqualTo("com.poseidon.demo.UserStatus");
                assertThat(unit.getUnitType()).isEqualTo("enum");
            });
    }

    private Path writeSource(String relativePath, String source) throws Exception {
        Path file = tempDir.resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }
}
