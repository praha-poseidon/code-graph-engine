package com.poseidon.codegraph.starter.service;

import com.poseidon.codegraph.spi.CodeGraphParserSession;

import java.util.List;
import java.util.Map;

/**
 * One disposable incremental-analysis task.
 *
 * <p>The session is deliberately a local Java object rather than a global
 * session-id registry. Files are processed sequentially; separate build tasks
 * create separate instances and therefore separate parser processes.
 */
public final class IncrementalUpdateSession implements AutoCloseable {

    private final String language;
    private final IncrementalUpdateService service;
    private final CodeGraphParserSession parserSession;
    private boolean closed;

    IncrementalUpdateSession(
            String language,
            IncrementalUpdateService service,
            CodeGraphParserSession parserSession) {
        this.language = language;
        this.service = service;
        this.parserSession = parserSession;
    }

    public String language() {
        return language;
    }

    public synchronized void handleFileChange(
            String projectName,
            String absoluteFilePath,
            String projectFilePath,
            String gitRepoUrl,
            String gitBranch,
            String[] classpathEntries,
            String[] sourcepathEntries,
            boolean cascade,
            List<String> endpointRuleSources,
            List<String> traceRuleSources) {
        execute(() -> service.handleFileChange(
            projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
            classpathEntries, sourcepathEntries, cascade, endpointRuleSources, traceRuleSources));
    }

    public synchronized void handleFileAdded(
            String projectName,
            String absoluteFilePath,
            String projectFilePath,
            String gitRepoUrl,
            String gitBranch,
            String[] classpathEntries,
            String[] sourcepathEntries,
            List<String> endpointRuleSources,
            List<String> traceRuleSources) {
        execute(() -> service.handleFileAdded(
            projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
            classpathEntries, sourcepathEntries, endpointRuleSources, traceRuleSources));
    }

    public synchronized void handleFileModified(
            String projectName,
            String absoluteFilePath,
            String projectFilePath,
            String gitRepoUrl,
            String gitBranch,
            String[] classpathEntries,
            String[] sourcepathEntries,
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            Map<String, Map<String, List<String>>> externalValues) {
        execute(() -> service.handleFileModified(
            projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
            classpathEntries, sourcepathEntries, endpointRuleSources, traceRuleSources, externalValues));
    }

    public synchronized void handleFileDeleted(
            String projectName,
            String absoluteFilePath,
            String projectFilePath,
            String gitRepoUrl,
            String gitBranch,
            String[] classpathEntries,
            String[] sourcepathEntries) {
        execute(() -> service.handleFileDeleted(
            projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
            classpathEntries, sourcepathEntries));
    }

    private void execute(SessionOperation operation) {
        ensureOpen();
        try {
            operation.run();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Incremental update session is already closed");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        parserSession.close();
    }

    @FunctionalInterface
    private interface SessionOperation {
        void run();
    }
}
