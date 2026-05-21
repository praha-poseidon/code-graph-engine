package com.poseidon.codegraph.model.delta;

import com.poseidon.codegraph.model.event.ChangeType;

import java.util.List;
import java.util.Map;

/**
 * Stable input protocol for parser implementations.
 */
public record ParseRequest(
        String projectName,
        String language,
        String projectRoot,
        List<String> sourceFiles,
        List<String> sourceRoots,
        List<String> dependencies,
        String gitRepoUrl,
        String gitBranch,
        ChangeType changeType,
        List<String> ruleSources,
        List<String> traceRuleSources,
        Map<String, Map<String, List<String>>> externalValues,
        Map<String, Object> options) {
}
