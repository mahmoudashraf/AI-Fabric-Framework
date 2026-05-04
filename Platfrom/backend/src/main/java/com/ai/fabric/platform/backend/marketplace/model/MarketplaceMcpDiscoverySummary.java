package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record MarketplaceMcpDiscoverySummary(
    boolean ready,
    String message,
    String serverRef,
    String endpointUrl,
    String protocolVersion,
    List<McpToolSummary> tools,
    String errorCode
) {

    public record McpToolSummary(
        String name,
        String title,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema,
        String schemaHash
    ) {
    }
}
