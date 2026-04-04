package com.ai.fabric.integration.connection;

import com.fasterxml.jackson.databind.JsonNode;

public record ConnectionDescriptor(
    String connectionId,
    String name,
    String adapterType,
    String authMode,
    JsonNode config,
    JsonNode discoverySummary
) {
}
