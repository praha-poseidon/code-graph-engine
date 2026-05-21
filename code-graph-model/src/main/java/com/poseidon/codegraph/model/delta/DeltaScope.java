package com.poseidon.codegraph.model.delta;

import com.poseidon.codegraph.model.event.ChangeType;

import java.util.List;
import java.util.Map;

/**
 * Scope for one parser output.
 */
public record DeltaScope(
        String projectName,
        String language,
        String gitRepoUrl,
        String gitBranch,
        String projectRoot,
        List<String> sourceFiles,
        ChangeType changeType,
        Map<String, Object> attributes) {
}
