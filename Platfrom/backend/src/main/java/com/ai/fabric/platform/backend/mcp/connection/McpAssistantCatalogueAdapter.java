package com.ai.fabric.platform.backend.mcp.connection;

import com.ai.fabric.platform.backend.assistant.McpAssistantCatalogueResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class McpAssistantCatalogueAdapter implements McpAssistantCatalogueResolver {
    private final McpConnectionService connections;
    public McpAssistantCatalogueAdapter(McpConnectionService connections) { this.connections = connections; }
    @Override public JsonNode catalogue(String deploymentId, String connectionId, String snapshotId, List<String> toolNames) {
        return connections.getAssistantCatalogue(deploymentId, connectionId, snapshotId, toolNames);
    }
}
