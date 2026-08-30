package com.poseidon.codegraph.app.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisTaskWorkerTest {

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
}
