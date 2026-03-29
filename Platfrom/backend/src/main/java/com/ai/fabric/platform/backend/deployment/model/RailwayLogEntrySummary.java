package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayLogEntrySummary(
    String timestamp,
    String severity,
    String message,
    RailwayLogTagsSummary tags,
    List<RailwayLogAttributeSummary> attributes
) {
}
