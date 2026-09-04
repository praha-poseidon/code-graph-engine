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
import com.poseidon.codegraph.spi.CodeGraphParserSession;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final boolean streaming;

    public ProcessCodeGraphParser(String language, List<String> command, Duration timeout) {
        this(language, command, timeout, false);
    }

    public ProcessCodeGraphParser(String language, List<String> command, Duration timeout, boolean streaming) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language must not be blank");
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        this.language = language.toLowerCase(Locale.ROOT);
        this.command = List.copyOf(command);
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.streaming = streaming;
    }

    @Override
    public String language() {
        return language;
    }

    @Override
    public CodeGraphParserSession openSession() {
        if (!streaming) {
            return CodeGraphParser.super.openSession();
        }
        return new StreamingSession(startProcess());
    }

    @Override
    public GraphDelta parse(ParseRequest request) {
        Process process = startProcess();
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        boolean completed = false;

        try (OutputStream stdin = process.getOutputStream()) {
            OBJECT_MAPPER.writeValue(stdin, request);
            stdin.flush();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
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
            GraphDelta delta = decodeDelta(output, request);
            completed = true;
            return delta;
        } catch (IOException e) {
            throw new ProcessParserException("External parser IO failed: language=" + language
                + ", command=" + command, language, command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessParserException("External parser interrupted: language=" + language
                + ", command=" + command, language, command, e);
        } finally {
            if (!completed) {
                terminateProcessTree(process);
            }
        }
    }

    private Process startProcess() {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new ProcessParserStartException(language, command, e);
        }
    }

    private GraphDelta decodeDelta(String output, ParseRequest request) {
        try {
            GraphDelta delta = OBJECT_MAPPER.readValue(output, GraphDelta.class);
            stampProjectName(delta, request.projectName());
            VALIDATOR.validateOrThrow(delta);
            return delta;
        } catch (IOException e) {
            throw new ProcessParserProtocolException(
                "External parser returned invalid GraphDelta JSON", language, command, output, e);
        } catch (GraphDeltaValidationException e) {
            throw new ProcessParserProtocolException(
                "External parser returned invalid GraphDelta data", language, command, output, e);
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
        CompletableFuture<String> result = new CompletableFuture<>();
        Thread.ofVirtual().name("codegraph-parser-" + language + "-stream").start(() -> {
            try (InputStream input = stream) {
                result.complete(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                result.completeExceptionally(new IllegalStateException("Cannot read external parser stream", e));
            }
        });
        return result;
    }

    private CompletableFuture<String> readLineAsync(BufferedReader reader) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Thread.ofVirtual().name("codegraph-parser-" + language + "-response").start(() -> {
            try {
                result.complete(reader.readLine());
            } catch (IOException e) {
                result.completeExceptionally(new IllegalStateException("Cannot read streaming parser response", e));
            }
        });
        return result;
    }

    /**
     * Terminates the parser and every subprocess that belongs to it (for example gopls or a
     * language server). Killing only the direct process leaks those children when the protocol
     * fails or a task is cancelled.
     */
    private void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants = new ArrayList<>(process.descendants().toList());
        descendants.sort(Comparator.comparingInt(ProcessCodeGraphParser::processDepth).reversed());
        descendants.forEach(ProcessCodeGraphParser::destroyQuietly);
        destroyQuietly(process.toHandle());

        long waitMillis = Math.max(1, Math.min(timeout.toMillis(), 1_000));
        waitForExit(process.toHandle(), waitMillis);

        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessCodeGraphParser::destroyForciblyQuietly);
        if (process.isAlive()) {
            destroyForciblyQuietly(process.toHandle());
        }
    }

    private static int processDepth(ProcessHandle handle) {
        int depth = 0;
        ProcessHandle current = handle;
        while (true) {
            var parent = current.parent();
            if (parent.isEmpty()) {
                return depth;
            }
            depth++;
            current = parent.orElseThrow();
        }
    }

    private static void destroyQuietly(ProcessHandle handle) {
        try {
            if (handle.isAlive()) {
                handle.destroy();
            }
        } catch (RuntimeException ignored) {
            // Best effort continues with destroyForcibly below.
        }
    }

    private static void destroyForciblyQuietly(ProcessHandle handle) {
        try {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        } catch (RuntimeException ignored) {
            // The operating system may have already reaped the process.
        }
    }

    private static void waitForExit(ProcessHandle handle, long waitMillis) {
        if (!handle.isAlive()) {
            return;
        }
        try {
            handle.onExit().get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            // Caller escalates to destroyForcibly.
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Process termination is the authoritative cleanup path.
        }
    }

    private final class StreamingSession implements CodeGraphParserSession {

        private final Process process;
        private final BufferedWriter stdin;
        private final BufferedReader stdout;
        private final CompletableFuture<String> stderr;
        private String projectRoot;
        private boolean closed;

        private StreamingSession(Process process) {
            this.process = process;
            this.stdin = process.outputWriter(StandardCharsets.UTF_8);
            this.stdout = process.inputReader(StandardCharsets.UTF_8);
            this.stderr = readAsync(process.getErrorStream());
        }

        @Override
        public String language() {
            return language;
        }

        @Override
        public synchronized GraphDelta parse(ParseRequest request) {
            ensureOpen();
            bindProject(request);
            if (!process.isAlive()) {
                ProcessParserExitException exception = exitException();
                failClosed();
                throw exception;
            }
            try {
                stdin.write(OBJECT_MAPPER.writeValueAsString(request));
                stdin.newLine();
                stdin.flush();

                CompletableFuture<String> response = readLineAsync(stdout);
                String output = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (output == null) {
                    throw exitException();
                }
                if (output.isBlank()) {
                    throw new ProcessParserProtocolException(
                        "External streaming parser returned empty output", language, command, output);
                }
                return decodeDelta(output, request);
            } catch (TimeoutException e) {
                ProcessParserTimeoutException exception = new ProcessParserTimeoutException(language, command, timeout);
                failClosed();
                throw exception;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ProcessParserException exception = new ProcessParserException("External streaming parser interrupted: language=" + language
                    + ", command=" + command, language, command, e);
                failClosed();
                throw exception;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                ProcessParserException exception = new ProcessParserException("External streaming parser IO failed: language=" + language
                    + ", command=" + command, language, command, cause);
                failClosed();
                throw exception;
            } catch (IOException e) {
                ProcessParserException exception = new ProcessParserException("External streaming parser IO failed: language=" + language
                    + ", command=" + command, language, command, e);
                failClosed();
                throw exception;
            } catch (RuntimeException e) {
                failClosed();
                throw e;
            }
        }

        private void bindProject(ParseRequest request) {
            if (request == null || request.projectRoot() == null || request.projectRoot().isBlank()) {
                throw new IllegalArgumentException("projectRoot is required for a streaming parser session");
            }
            String requestedRoot = Path.of(request.projectRoot()).toAbsolutePath().normalize().toString();
            if (projectRoot == null) {
                projectRoot = requestedRoot;
            } else if (!projectRoot.equals(requestedRoot)) {
                throw new IllegalArgumentException("Parser session is already bound to projectRoot=" + projectRoot
                    + ", requested=" + requestedRoot);
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Parser session is already closed");
            }
        }

        private ProcessParserExitException exitException() {
            try {
                process.waitFor(Math.min(timeout.toMillis(), 1_000), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            String error = process.isAlive() ? "" : stderr.join();
            return new ProcessParserExitException(language, command, exitCode, error);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            List<ProcessHandle> descendantsAtClose = process.descendants().toList();
            try {
                stdin.close();
                long closeTimeout = Math.min(timeout.toMillis(), 5_000);
                if (!process.waitFor(closeTimeout, TimeUnit.MILLISECONDS)) {
                    terminateProcessTree(process);
                }
            } catch (IOException e) {
                terminateProcessTree(process);
                throw new ProcessParserException("Cannot close streaming parser: language=" + language
                    + ", command=" + command, language, command, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminateProcessTree(process);
                throw new ProcessParserException("Interrupted while closing streaming parser: language=" + language
                    + ", command=" + command, language, command, e);
            } finally {
                closeQuietly(stdout);
                descendantsAtClose.stream()
                    .filter(ProcessHandle::isAlive)
                    .forEach(ProcessCodeGraphParser::destroyForciblyQuietly);
                if (process.isAlive()) {
                    terminateProcessTree(process);
                }
            }
        }

        private void failClosed() {
            if (closed) {
                return;
            }
            closed = true;
            closeQuietly(stdin);
            closeQuietly(stdout);
            terminateProcessTree(process);
        }
    }
}
