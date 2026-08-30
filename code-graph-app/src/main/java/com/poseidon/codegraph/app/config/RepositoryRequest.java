package com.poseidon.codegraph.app.config;

import java.util.List;

public record RepositoryRequest(
        String gitRepoUrl,
        String gitBranch,
        List<String> languages,
        String authType,
        String accessToken,
        String sshPrivateKey,
        String sshPassphrase,
        List<String> endpointRuleSources) {
}
