package com.poseidon.codegraph.starter.service;

import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncrementalUpdateServiceMemoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void createUpdateAndDeleteJavaFileAgainstMemoryStorage() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path sourceFile = sourceRoot.resolve("com/poseidon/demo/App.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
            package com.poseidon.demo;

            public class App {
                public String run() {
                    return "v1";
                }
            }
            """);

        InMemoryCodeGraphRepository repository = new InMemoryCodeGraphRepository();
        IncrementalUpdateService service = new IncrementalUpdateService(
            repository,
            repository,
            repository,
            repository,
            repository);

        service.handleFileAdded(
            "demo",
            sourceFile.toString(),
            "src/main/java/com/poseidon/demo/App.java",
            "git@example/demo.git",
            "main",
            new String[0],
            new String[] {sourceRoot.toString()},
            List.of(),
            List.of());

        assertThat(repository.findUnitsByProjectFilePath("demo", "src/main/java/com/poseidon/demo/App.java"))
            .extracting(CodeUnitDO::getQualifiedName)
            .containsExactly("com.poseidon.demo.App");
        assertThat(repository.findFunctionsByProjectFilePath("demo", "src/main/java/com/poseidon/demo/App.java"))
            .extracting(CodeFunctionDO::getQualifiedName)
            .containsExactly("com.poseidon.demo.App.run()");
        assertThat(repository.findOutgoingRelationships("demo", "demo::unit:com.poseidon.demo.App", "UNIT_TO_FUNCTION"))
            .extracting(CodeRelationshipDO::getToNodeId)
            .containsExactly("demo::fn:com.poseidon.demo.App.run()");

        Files.writeString(sourceFile, """
            package com.poseidon.demo;

            public class App {
                public String renamed() {
                    return "v2";
                }
            }
            """);

        service.handleFileModified(
            "demo",
            sourceFile.toString(),
            "src/main/java/com/poseidon/demo/App.java",
            "git@example/demo.git",
            "main",
            new String[0],
            new String[] {sourceRoot.toString()},
            List.of(),
            List.of());

        assertThat(repository.findFunctionsByProjectFilePath("demo", "src/main/java/com/poseidon/demo/App.java"))
            .extracting(CodeFunctionDO::getQualifiedName)
            .containsExactly("com.poseidon.demo.App.renamed()");
        assertThat(repository.findExistingFunctionsByQualifiedNames("demo", List.of("demo::fn:com.poseidon.demo.App.run()")))
            .isEmpty();
        assertThat(repository.findOutgoingRelationships("demo", "demo::unit:com.poseidon.demo.App", "UNIT_TO_FUNCTION"))
            .extracting(CodeRelationshipDO::getToNodeId)
            .containsExactly("demo::fn:com.poseidon.demo.App.renamed()");

        service.handleFileDeleted(
            "demo",
            sourceFile.toString(),
            "src/main/java/com/poseidon/demo/App.java",
            "git@example/demo.git",
            "main",
            new String[0],
            new String[] {sourceRoot.toString()});

        assertThat(repository.findUnitsByProjectFilePath("demo", "src/main/java/com/poseidon/demo/App.java")).isEmpty();
        assertThat(repository.findFunctionsByProjectFilePath("demo", "src/main/java/com/poseidon/demo/App.java")).isEmpty();
        assertThat(repository.findOutgoingRelationships("demo", "demo::unit:com.poseidon.demo.App", null)).isEmpty();
    }
}
