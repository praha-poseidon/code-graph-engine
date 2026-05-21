package com.poseidon.codegraph.parser.process;

import com.poseidon.codegraph.model.delta.ParseRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessParserConfigTest {

    @Test
    void splitPreservesQuotedArguments() {
        assertEquals(
            java.util.List.of("python3", "/tmp/my parser.py", "--mode", "graph delta"),
            ProcessParserConfig.split("python3 '/tmp/my parser.py' --mode \"graph delta\"")
        );
    }

    @Test
    void splitSupportsEscapedWhitespace() {
        assertEquals(
            java.util.List.of("node", "/tmp/parser cli/index.js"),
            ProcessParserConfig.split("node /tmp/parser\\ cli/index.js")
        );
    }

    @Test
    void processParserReadsGraphDeltaFromStdout() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of(javaBin, "-cp", System.getProperty("java.class.path"), FakeExternalParser.class.getName()),
            Duration.ofSeconds(10)
        );

        var delta = parser.parse(new ParseRequest(
            "demo",
            "go",
            "/repo",
            List.of("/repo/main.go"),
            List.of("/repo"),
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            Map.of("projectFilePath", "main.go")
        ));

        assertEquals(1, delta.functions().size());
        assertEquals("demo.main", delta.functions().get(0).getId());
    }

    @Test
    void processParserReportsInvalidJsonAsProtocolFailure() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of(javaBin, "-cp", System.getProperty("java.class.path"), InvalidJsonExternalParser.class.getName()),
            Duration.ofSeconds(10)
        );

        assertThrows(ProcessParserProtocolException.class, () -> parser.parse(request()));
    }

    @Test
    void processParserReportsNonZeroExit() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of(javaBin, "-cp", System.getProperty("java.class.path"), FailingExternalParser.class.getName()),
            Duration.ofSeconds(10)
        );

        assertThrows(ProcessParserExitException.class, () -> parser.parse(request()));
    }

    @Test
    void processParserReportsTimeout() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of(javaBin, "-cp", System.getProperty("java.class.path"), SlowExternalParser.class.getName()),
            Duration.ofMillis(1)
        );

        ProcessParserTimeoutException error =
            assertThrows(ProcessParserTimeoutException.class, () -> parser.parse(request()));

        assertEquals("go", error.language());
    }

    @Test
    void processParserReportsInvalidDeltaDataAsProtocolFailure() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of(javaBin, "-cp", System.getProperty("java.class.path"), InvalidDeltaExternalParser.class.getName()),
            Duration.ofSeconds(10)
        );

        assertThrows(ProcessParserProtocolException.class, () -> parser.parse(request()));
    }

    @Test
    void processParserReportsEmptyOutputAsProtocolFailure() {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of(javaBin, "-cp", System.getProperty("java.class.path"), EmptyOutputExternalParser.class.getName()),
            Duration.ofSeconds(10)
        );

        assertThrows(ProcessParserProtocolException.class, () -> parser.parse(request()));
    }

    @Test
    void processParserReportsStartFailure() {
        ProcessCodeGraphParser parser = new ProcessCodeGraphParser(
            "go",
            List.of("/path/to/missing/code-graph-parser"),
            Duration.ofSeconds(10)
        );

        assertThrows(ProcessParserStartException.class, () -> parser.parse(request()));
    }

    @Test
    void constructorRejectsInvalidArgumentsAndNormalizesLanguage() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessCodeGraphParser("", List.of("cmd"), null));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCodeGraphParser("go", List.of(), null));

        ProcessCodeGraphParser parser = new ProcessCodeGraphParser("Go", List.of("cmd"), null);
        assertEquals("go", parser.language());
    }

    @Test
    void loadParsersReadsConfiguredLanguagesCommandsAndTimeout() {
        System.setProperty(ProcessParserConfig.LANGUAGES_PROPERTY, "Go JavaScript");
        System.setProperty("codegraph.parser.process.go.command", "go-parser --json");
        System.setProperty("codegraph.parser.process.javascript.command", "node '/tmp/js parser.js'");
        System.setProperty(ProcessParserConfig.TIMEOUT_PROPERTY, "not-a-number");
        try {
            List<ProcessCodeGraphParser> parsers = ProcessParserConfig.loadParsers();

            assertEquals(2, parsers.size());
            assertEquals("go", parsers.get(0).language());
            assertEquals("javascript", parsers.get(1).language());
        } finally {
            System.clearProperty(ProcessParserConfig.LANGUAGES_PROPERTY);
            System.clearProperty("codegraph.parser.process.go.command");
            System.clearProperty("codegraph.parser.process.javascript.command");
            System.clearProperty(ProcessParserConfig.TIMEOUT_PROPERTY);
        }
    }

    private ParseRequest request() {
        return new ParseRequest(
            "demo",
            "go",
            "/repo",
            List.of("/repo/main.go"),
            List.of("/repo"),
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            Map.of(),
            Map.of("projectFilePath", "main.go")
        );
    }
}

class FakeExternalParser {

    public static void main(String[] args) throws Exception {
        System.in.readAllBytes();
        System.out.print("""
            {
              "scope": null,
              "packages": [],
              "units": [],
              "functions": [
                {
                  "id": "demo.main",
                  "name": "main",
                  "qualifiedName": "demo.main",
                  "language": "go",
                  "projectName": "demo",
                  "projectFilePath": "main.go",
                  "signature": "main()",
                  "returnType": "void"
                }
              ],
              "endpoints": [],
              "relationships": [],
              "deletedNodeIds": [],
              "deletedRelationshipIds": [],
              "diagnostics": []
            }
            """);
    }
}

class InvalidJsonExternalParser {

    public static void main(String[] args) throws Exception {
        System.in.readAllBytes();
        System.out.print("not json");
    }
}

class FailingExternalParser {

    public static void main(String[] args) throws Exception {
        System.in.readAllBytes();
        System.err.print("parse failed");
        System.exit(7);
    }
}

class InvalidDeltaExternalParser {

    public static void main(String[] args) throws Exception {
        System.in.readAllBytes();
        System.out.print("""
            {
              "scope": null,
              "packages": [],
              "units": [],
              "functions": [
                {
                  "id": "demo.main"
                }
              ],
              "endpoints": [],
              "relationships": [],
              "deletedNodeIds": [],
              "deletedRelationshipIds": [],
              "diagnostics": []
            }
            """);
    }
}

class EmptyOutputExternalParser {

    public static void main(String[] args) throws Exception {
        System.in.readAllBytes();
    }
}

class SlowExternalParser {

    public static void main(String[] args) throws Exception {
        System.in.readAllBytes();
        Thread.sleep(5_000);
    }
}
