package com.ai.fabric.platform.backend.mcp.connection;

import com.ai.fabric.platform.backend.mcp.runtime.McpRuntimeConnectionResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Internal adapters only; no browser endpoint can directly resolve or forward upstream credentials. */
@Service
public class McpInternalAdapters implements McpRuntimeConnectionResolver {
    private final McpConnectionExecutionResolver connections;
    private final McpConnectionGatewayClient gateway;
    private final ObjectMapper mapper;
    public McpInternalAdapters(McpConnectionExecutionResolver connections, McpConnectionGatewayClient gateway, ObjectMapper mapper) {
        this.connections = connections; this.gateway = gateway; this.mapper = mapper;
    }
    @Override public Resolved resolve(String connectionId, String deploymentId, String targetProfileId, String toolName, String schemaHash) {
        var result = connections.resolveExecution(connectionId, deploymentId, targetProfileId, toolName, schemaHash);
        return new Resolved(result.server(), result.resolvedSecretValues());
    }
    @Override public JsonNode execute(JsonNode canonicalRequest) {
        return gateway.execute(mapper.convertValue(canonicalRequest, new TypeReference<Map<String, Object>>() { }));
    }
}
