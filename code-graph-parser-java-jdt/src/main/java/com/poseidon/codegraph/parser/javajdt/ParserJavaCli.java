package com.poseidon.codegraph.parser.javajdt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.event.ChangeType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Standalone process-protocol CLI for the Java JDT parser. */
public final class ParserJavaCli {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ParserJavaCli() {}

    public static void main(String[] args) throws Exception {
        configureCliLogging();
        if (args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
            printUsage();
            return;
        }
        ParseRequest request;
        if (contains(args, "--stdio")) {
            request = MAPPER.readValue(System.in, ParseRequest.class);
        } else if (value(args, "--request") != null) {
            request = MAPPER.readValue(Path.of(value(args, "--request")).toFile(), ParseRequest.class);
        } else if (value(args, "--project") != null) {
            request = projectRequest(Path.of(value(args, "--project")));
        } else {
            throw new IllegalArgumentException("use --stdio, --request <file>, or --project <dir>");
        }
        MAPPER.writeValue(System.out, new JavaJdtCodeGraphParser().parse(request));
        System.out.println();
    }

    private static void configureCliLogging() {
        if (System.getProperty("logback.configurationFile") != null) return;
        var resource = ParserJavaCli.class.getResource("/parser-java-logback.xml");
        if (resource != null) System.setProperty("logback.configurationFile", resource.toExternalForm());
    }

    private static ParseRequest projectRequest(Path root) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        List<String> sourceFiles = new ArrayList<>();
        try (var paths = Files.walk(absoluteRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .sorted()
                    .forEach(sourceFiles::add);
        }
        return new ParseRequest(
                absoluteRoot.getFileName().toString(),
                "java",
                absoluteRoot.toString(),
                sourceFiles,
                List.of(absoluteRoot.toString()),
                List.of(),
                null,
                null,
                ChangeType.SOURCE_ADDED,
                List.of(),
                List.of(),
                Map.of(),
                Map.of());
    }

    private static boolean contains(String[] args, String expected) {
        for (String arg : args) {
            if (expected.equals(arg)) return true;
        }
        return false;
    }

    private static String value(String[] args, String name) {
        for (int index = 0; index + 1 < args.length; index++) {
            if (name.equals(args[index])) return args[index + 1];
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("""
                Usage: parser-java (--stdio | --request <file> | --project <dir>)

                Read one ParseRequest and write one GraphDelta JSON document to stdout.
                """);
    }
}
