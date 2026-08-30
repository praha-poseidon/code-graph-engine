package com.poseidon.codegraph.app.task;

import java.time.Instant;

public record AnalysisTask(
        String id,
        long repositoryId,
        String status,
        int progressCurrent,
        int progressTotal,
        String message,
        String errorDetails,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {
}
