package com.poseidon.codegraph.model.delta;

import java.util.Map;

/**
 * Non-fatal parser diagnostic.
 */
public record Diagnostic(
        DiagnosticLevel level,
        String code,
        String message,
        String projectFilePath,
        Integer lineNumber,
        Map<String, Object> details) {
}
