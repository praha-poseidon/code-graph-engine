package com.poseidon.codegraph.parser.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeNode;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.GraphDeltaValidationException;
import com.poseidon.codegraph.model.delta.GraphDeltaValidator;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.spi.CodeGraphParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Parser adapter that delegates parsing to an external process.
 *
 * <p>Protocol:
 * - stdin: ParseRequest JSON
 * - stdout: GraphDelta JSON
 * - stderr: diagnostic text for failures
 */
public final class ProcessCodeGraphParser implements CodeGraphParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final GraphDeltaValidator VALIDATOR = new GraphDeltaValidator();

    private final String language;
    private final List<String> command;
    private final Duration timeout;

    public ProcessCodeGraphParser(String language, List<String> command, Duration timeout) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language must not be blank");
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        this.language = language.toLowerCase(Locale.ROOT);
        this.command = List.copyOf(command);
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    @Override
    public String language() {
        return language;
    }

    @Override
    public GraphDelta parse(ParseRequest request) {
        Process process = startProcess();
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());

        try (OutputStream stdin = process.getOutputStream()) {
            OBJECT_MAPPER.writeValue(stdin, request);
            stdin.flush();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ProcessParserTimeoutException(language, command, timeout);
            }

            String output = stdout.join();
            String error = stderr.join();
            if (process.exitValue() != 0) {
                throw new ProcessParserExitException(language, command, process.exitValue(), error);
            }
            if (output == null || output.isBlank()) {
                throw new ProcessParserProtocolException("External parser returned empty output", language, command, output);
            }
            try {
                GraphDelta delta = OBJECT_MAPPER.readValue(output, GraphDelta.class);
                stampProjectName(delta, request.projectName());
                VALIDATOR.validateOrThrow(delta);
                return delta;
            } catch (IOException e) {
                throw new ProcessParserProtocolException("External parser returned invalid GraphDelta JSON", language, command, output, e);
            } catch (GraphDeltaValidationException e) {
                throw new ProcessParserProtocolException("External parser returned invalid GraphDelta data", language, command, output, e);
            }
        } catch (IOException e) {
            throw new ProcessParserException("External parser IO failed: language=" + language
                + ", command=" + command, language, command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ProcessParserException("External parser interrupted: language=" + language
                + ", command=" + command, language, command, e);
        }
    }

    private Process startProcess() {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new ProcessParserStartException(language, command, e);
        }
    }

    private void stampProjectName(GraphDelta delta, String projectName) {
        if (delta == null || projectName == null || projectName.isBlank()) {
            return;
        }
        stampNodes(delta.packages(), projectName);
        stampNodes(delta.units(), projectName);
        stampNodes(delta.functions(), projectName);
        stampNodes(delta.endpoints(), projectName);
        if (delta.relationships() != null) {
            for (CodeRelationship relationship : delta.relationships()) {
                if (relationship != null && (relationship.getProjectName() == null || relationship.getProjectName().isBlank())) {
                    relationship.setProjectName(projectName);
                }
            }
        }
        if (delta.endpoints() != null) {
            for (CodeEndpoint endpoint : delta.endpoints()) {
                if (endpoint != null && endpoint.getFunction() != null
                    && (endpoint.getFunction().getProjectName() == null || endpoint.getFunction().getProjectName().isBlank())) {
                    endpoint.getFunction().setProjectName(projectName);
                }
            }
        }
    }

    private void stampNodes(List<? extends CodeNode> nodes, String projectName) {
        if (nodes == null) {
            return;
        }
        for (CodeNode node : nodes) {
            if (node != null && (node.getProjectName() == null || node.getProjectName().isBlank())) {
                node.setProjectName(projectName);
            }
        }
    }

    private CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read external parser stream", e);
            }
        });
    }
}
