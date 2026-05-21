package com.poseidon.codegraph.model.delta;

import java.util.List;

/**
 * Raised when parser output violates the graph delta protocol.
 */
public class GraphDeltaValidationException extends RuntimeException {

    private final List<Diagnostic> diagnostics;

    public GraphDeltaValidationException(List<Diagnostic> diagnostics) {
        super(message(diagnostics));
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static String message(List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "GraphDelta validation failed";
        }
        return "GraphDelta validation failed: " + diagnostics.stream()
            .limit(5)
            .map(d -> d.code() + "=" + d.message())
            .toList();
    }
}
