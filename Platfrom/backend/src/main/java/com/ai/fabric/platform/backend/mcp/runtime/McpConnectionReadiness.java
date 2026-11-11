package com.ai.fabric.platform.backend.mcp.runtime;

import com.ai.fabric.platform.backend.deployment.setup.SetupCheckContracts.ServiceResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Metadata-only evidence: inspection reads saved results; apply verifies actual bound tools. */
public interface McpConnectionReadiness {
    String RUNTIME_CAPABILITY = "managed-mcp-connections";

    static boolean required(JsonNode actionsConfig) {
        for (JsonNode action : actionsConfig.path("actions")) {
            JsonNode connection = action.path("execution").path("mcp").path("connectionId");
            if (action.path("enabled").asBoolean(true) && connection.isTextual() && !connection.asText().isBlank()) return true;
        }
        return false;
    }

    List<ServiceResult> inspect(String deploymentId, JsonNode actionsConfig);
    void verify(String deploymentId, String targetProfileId, JsonNode effectiveActionsConfig);
}
