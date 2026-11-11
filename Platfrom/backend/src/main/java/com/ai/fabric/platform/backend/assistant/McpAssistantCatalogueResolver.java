package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Returns owned, nonsecret source metadata, never a credential or executable client. */
public interface McpAssistantCatalogueResolver {
    JsonNode catalogue(String deploymentId, String connectionId, String snapshotId, List<String> toolNames);
}
