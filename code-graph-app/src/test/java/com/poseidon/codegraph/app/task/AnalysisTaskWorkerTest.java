package com.poseidon.codegraph.app.task;

import com.poseidon.codegraph.app.config.RepositoryConfig;
import com.poseidon.codegraph.app.config.RepositoryConfigStore;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import com.poseidon.codegraph.starter.service.IncrementalUpdateSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisTaskWorkerTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsSupportedSourceExtensionsToParserLanguages() {
        assertThat(AnalysisTaskWorker.languageFor(Path.of("Api.java"))).isEqualTo("java");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("main.go"))).isEqualTo("go");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("page.tsx"))).isEqualTo("typescript");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("worker.mjs"))).isEqualTo("javascript");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("service.py"))).isEqualTo("python");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("index.php"))).isEqualTo("php");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("App.kt"))).isEqualTo("kotlin");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("View.swift"))).isEqualTo("swift");
        assertThat(AnalysisTaskWorker.languageFor(Path.of("README.md"))).isEqualTo("unknown");
    }

    @Test
    void parserFailureStillClosesTaskSessionAndCleansWorkspace() throws Exception {
        AnalysisTaskStore taskStore = mock(AnalysisTaskStore.class);
        RepositoryConfigStore repositoryStore = mock(RepositoryConfigStore.class);
        GitWorkspace workspace = mock(GitWorkspace.class);
        IncrementalUpdateService updateService = mock(IncrementalUpdateService.class);
        IncrementalUpdateSession session = mock(IncrementalUpdateSession.class);
        AnalysisTask task = new AnalysisTask(
            "task-1", 7L, "RUNNING", 0, 0, null, null, Instant.now(), Instant.now(), null);
        RepositoryConfig repository = repository();
        Path checkout = Files.createDirectories(tempDir.resolve("checkout"));
        Files.writeString(checkout.resolve("main.go"), "package main\n");

        when(repositoryStore.findById(7L)).thenReturn(Optional.of(repository));
        when(repositoryStore.decrypted(repository)).thenReturn(repository);
        when(workspace.cloneRepository("task-1", repository)).thenReturn(checkout);
        when(updateService.openSession("go")).thenReturn(session);
        doThrow(new IllegalStateException("semantic parser failed"))
            .when(session).handleFileAdded(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(String[].class), any(String[].class), anyList(), anyList());

        new AnalysisTaskWorker(taskStore, repositoryStore, workspace, updateService, false).execute(task);

        verify(session).close();
        verify(workspace).cleanup("task-1");
        verify(taskStore).fail(eq("task-1"), eq("semantic parser failed"), anyString());
    }

    @Test
    void sessionCloseFailureCannotPreventWorkspaceCleanup() throws Exception {
        AnalysisTaskStore taskStore = mock(AnalysisTaskStore.class);
        RepositoryConfigStore repositoryStore = mock(RepositoryConfigStore.class);
        GitWorkspace workspace = mock(GitWorkspace.class);
        IncrementalUpdateService updateService = mock(IncrementalUpdateService.class);
        IncrementalUpdateSession session = mock(IncrementalUpdateSession.class);
        AnalysisTask task = new AnalysisTask(
            "task-2", 7L, "RUNNING", 0, 0, null, null, Instant.now(), Instant.now(), null);
        RepositoryConfig repository = repository();
        Path checkout = Files.createDirectories(tempDir.resolve("checkout-close"));
        Files.writeString(checkout.resolve("main.go"), "package main\n");

        when(repositoryStore.findById(7L)).thenReturn(Optional.of(repository));
        when(repositoryStore.decrypted(repository)).thenReturn(repository);
        when(workspace.cloneRepository("task-2", repository)).thenReturn(checkout);
        when(updateService.openSession("go")).thenReturn(session);
        when(session.language()).thenReturn("go");
        doThrow(new IllegalStateException("close failed")).when(session).close();

        new AnalysisTaskWorker(taskStore, repositoryStore, workspace, updateService, false).execute(task);

        verify(session).close();
        verify(workspace).cleanup("task-2");
        verify(taskStore).succeed("task-2", 1);
    }

    private RepositoryConfig repository() {
        return new RepositoryConfig(
            7L,
            "demo",
            "git@example/demo.git",
            "main",
            List.of("go"),
            "NONE",
            null,
            null,
            null,
            List.of(),
            "IDLE",
            null,
            Instant.now(),
            Instant.now());
    }
}
