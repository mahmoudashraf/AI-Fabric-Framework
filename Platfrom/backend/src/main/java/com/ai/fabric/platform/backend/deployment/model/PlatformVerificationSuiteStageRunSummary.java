package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record PlatformVerificationSuiteStageRunSummary(
    String id,
    int stageOrder,
    String stageKey,
    String stageLabel,
    String stageType,
    String targetRef,
    boolean blocking,
    String status,
    String summaryMessage,
    JsonNode details,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt
) {
}
