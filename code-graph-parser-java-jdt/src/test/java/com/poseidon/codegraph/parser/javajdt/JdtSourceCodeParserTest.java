package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.RelationshipType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdtSourceCodeParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesPackagesWithoutBindingEnvironment() throws Exception {
        Path source = writeSource(
            "src/main/java/com/example/User.java",
            """
            package com.example;

            class User {}
            """);
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[0],
            ProcessorRegistry.createCoreOnly());

        List<CodePackage> packages = parser.parsePackages(
            source.toString(),
            "demo",
            "src/main/java/com/example/User.java");

        assertThat(packages)
            .singleElement()
            .satisfies(pkg -> {
                assertThat(pkg.getId()).isEqualTo("pkg:com.example");
                assertThat(pkg.getQualifiedName()).isEqualTo("com.example");
                assertThat(pkg.getPackagePath()).isEqualTo("com/example");
                assertThat(pkg.getProjectFilePath()).isEqualTo("src/main/java/com/example");
            });
    }

    @Test
    void parsesClassEnumAndAnnotationUnitsWithSourcepathBinding() throws Exception {
        Path source = writeSource(
            "src/main/java/com/example/Types.java",
            """
            package com.example;

            public class User {
                public String name() {
                    return "u";
                }
            }

            enum Status {
                ACTIVE
            }

            @interface Marker {}
            """);
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[] {tempDir.resolve("src/main/java").toString()},
            ProcessorRegistry.createCoreOnly());

        List<CodeUnit> units = parser.parseUnits(
            source.toString(),
            "demo",
            "src/main/java/com/example/Types.java");

        assertThat(units)
            .extracting(CodeUnit::getQualifiedName)
            .containsExactly("com.example.User", "com.example.Status", "com.example.Marker");
        assertThat(units)
            .extracting(CodeUnit::getUnitType)
            .containsExactly("class", "enum", "annotation");
        assertThat(units.getFirst().getFunctions())
            .singleElement()
            .satisfies(function -> {
                assertThat(function.getQualifiedName()).isEqualTo("com.example.User.name():java.lang.String");
                assertThat(function.getReturnType()).isEqualTo("java.lang.String");
                assertThat(function.getIsPlaceholder()).isFalse();
            });
    }

    @Test
    void parseRelationshipsReturnsEmptyWhenBindingIsDisabled() throws Exception {
        Path source = writeSource(
            "src/main/java/com/example/Caller.java",
            """
            package com.example;

            class Caller {
                void run() {
                    System.out.println("x");
                }
            }
            """);
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[0],
            ProcessorRegistry.createCoreOnly());

        List<CodeRelationship> relationships = parser.parseRelationships(
            source.toString(),
            "demo",
            "src/main/java/com/example/Caller.java");

        assertThat(relationships).isEmpty();
    }

    @Test
    void resolvesCallsToCompiledClasspathTypesWhenClasspathIsProvided() throws Exception {
        Path classes = tempDir.resolve("target/classes");
        Path librarySource = writeSource(
            "lib-src/com/poseidon/lib/LibraryService.java",
            """
            package com.poseidon.lib;

            public class LibraryService {
                public String load() {
                    return "ok";
                }
            }
            """);
        compileJava(librarySource, classes);
        Path callerSource = writeSource(
            "src/main/java/com/poseidon/app/Caller.java",
            """
            package com.poseidon.app;

            import com.poseidon.lib.LibraryService;

            class Caller {
                String run() {
                    return new LibraryService().load();
                }
            }
            """);
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[] {classes.toString()},
            new String[] {tempDir.resolve("src/main/java").toString()},
            ProcessorRegistry.createCoreOnly());

        CodeGraph graph = parser.parse(
            callerSource.toString(),
            "demo",
            "src/main/java/com/poseidon/app/Caller.java",
            "git@example/demo.git",
            "main");

        assertThat(graph.getRelationshipsAsList())
            .anySatisfy(relationship -> {
                assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.CALLS);
                assertThat(relationship.getFromNodeId()).isEqualTo("fn:com.poseidon.app.Caller.run()");
                assertThat(relationship.getToNodeId()).isEqualTo("fn:com.poseidon.lib.LibraryService.load()");
            });
    }

    @Test
    void rejectsMissingClasspathEntryWithClearMessage() throws Exception {
        Path source = writeSource(
            "src/main/java/com/example/User.java",
            """
            package com.example;

            class User {}
            """);
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[] {tempDir.resolve("missing-classes").toString()},
            new String[] {tempDir.resolve("src/main/java").toString()},
            ProcessorRegistry.createCoreOnly());

        assertThatThrownBy(() -> parser.parse(
            source.toString(),
            "demo",
            "src/main/java/com/example/User.java",
            null,
            null))
            .isInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("JDT classpath entry does not exist: " + tempDir.resolve("missing-classes"));
    }

    @Test
    void rejectsMissingSourcepathEntryWithClearMessage() throws Exception {
        Path source = writeSource(
            "src/main/java/com/example/User.java",
            """
            package com.example;

            class User {}
            """);
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[] {tempDir.resolve("missing-src").toString()},
            ProcessorRegistry.createCoreOnly());

        assertThatThrownBy(() -> parser.parse(
            source.toString(),
            "demo",
            "src/main/java/com/example/User.java",
            null,
            null))
            .isInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("JDT sourcepath entry does not exist: " + tempDir.resolve("missing-src"));
    }

    @Test
    void throwsClearExceptionWhenSourceFileCannotBeRead() {
        JdtSourceCodeParser parser = new JdtSourceCodeParser(
            new String[0],
            new String[0],
            ProcessorRegistry.createCoreOnly());
        Path missing = tempDir.resolve("missing.java");

        assertThatThrownBy(() -> parser.parsePackages(
            missing.toString(),
            "demo",
            "src/main/java/com/example/Missing.java"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to parse file");
    }

    private Path writeSource(String relativePath, String source) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    private void compileJava(Path sourceFile, Path outputDirectory) throws Exception {
        Files.createDirectories(outputDirectory);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("tests must run on a JDK, not a JRE").isNotNull();
        int result = compiler.run(
            null,
            null,
            null,
            "-d",
            outputDirectory.toString(),
            sourceFile.toString());
        assertThat(result).isZero();
    }
}
