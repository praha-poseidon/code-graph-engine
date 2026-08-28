package com.poseidon.codegraph.starter.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncrementalUpdateServiceLanguageTest {

    @Test
    void infersPhpFromPhpSourcePath() {
        assertEquals("php", IncrementalUpdateService.inferLanguage("src/App/Service.php"));
        assertEquals("php", IncrementalUpdateService.inferLanguage("SRC/BOOTSTRAP.PHP"));
    }

    @Test
    void keepsExistingLanguageInference() {
        assertEquals("java", IncrementalUpdateService.inferLanguage("src/App.java"));
        assertEquals("go", IncrementalUpdateService.inferLanguage("cmd/main.go"));
        assertEquals("javascript", IncrementalUpdateService.inferLanguage("src/app.jsx"));
        assertEquals("typescript", IncrementalUpdateService.inferLanguage("src/app.tsx"));
        assertEquals("kotlin", IncrementalUpdateService.inferLanguage("src/App.kt"));
        assertEquals("kotlin", IncrementalUpdateService.inferLanguage("build.gradle.kts"));
        assertEquals("swift", IncrementalUpdateService.inferLanguage("Sources/App/Service.swift"));
    }
}
