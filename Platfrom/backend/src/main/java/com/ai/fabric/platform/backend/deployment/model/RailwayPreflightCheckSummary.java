package com.ai.fabric.platform.backend.deployment.model;

public record RailwayPreflightCheckSummary(
    String key,
    String status,
    String message,
    String details
) {
}
