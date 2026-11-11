package com.ai.fabric.platform.backend.mcp.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Internal contract; callers must first establish exact applied runtime-target authority. */
public interface McpRuntimeConnectionResolver {
    Resolved resolve(String connectionId, String deploymentId, String targetProfileId, String toolName, String schemaHash);
    JsonNode execute(JsonNode canonicalRequest);

    record Resolved(JsonNode server, Map<String, String> secretValues) {
        public Resolved { secretValues = secretValues == null ? Map.of() : Map.copyOf(secretValues); }
        @Override public String toString() { return "ResolvedMcpConnection[credentials=redacted]"; }
    }
}
