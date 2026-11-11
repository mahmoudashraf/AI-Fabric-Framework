package com.ai.fabric.platform.backend.mcp.connection;

import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Service-registry-bound gateway client. Callers never choose a gateway URL or service credential. */
@Component
public class McpConnectionGatewayClient {
    private final PlatformProductProvisioningProperties properties;
    private final PlatformManagedProductServiceService services;
    private final PlatformSecretService secrets;
    private final ObjectMapper mapper;
    private final McpSafeHttpTransport http;
    public McpConnectionGatewayClient(PlatformProductProvisioningProperties properties,
        PlatformManagedProductServiceService services, PlatformSecretService secrets, ObjectMapper mapper,
        McpSafeHttpTransport http) {
        this.properties = properties; this.services = services; this.secrets = secrets; this.mapper = mapper; this.http = http;
    }

    public JsonNode discover(String serverRef, JsonNode server, Map<String, String> resolvedSecretValues) {
        return call("/api/internal/mcp/import/discover", Map.of("serverRef", serverRef, "server", server,
            "trace", Map.of("mcpSecretValues", resolvedSecretValues), "allowedTools", List.of()));
    }

    /** MAIN's target-authenticated bridge supplies the reconstructed action/trace, not browser JSON. */
    public JsonNode execute(Map<String, Object> trustedPayload) {
        return call("/api/internal/mcp/actions/execute", trustedPayload);
    }
    public JsonNode verify(String serverRef, JsonNode server, Map<String, String> secretValues, JsonNode expectedTools) {
        return call("/api/internal/mcp/servers/verify", Map.of("serverRef", serverRef, "server", server,
            "trace", Map.of("mcpSecretValues", secretValues), "expectedTools", expectedTools));
    }

    private JsonNode call(String path, Map<String, Object> payload) {
        var service = services.requireService(properties.mcpExecutionGatewayServiceRef());
        if (service.getBaseUrl() == null || service.getBaseUrl().isBlank() || service.getSecretName() == null)
            throw unavailable("Managed MCP gateway is not configured.");
        String key = secrets.resolveSecret(service.getSecretName());
        if (key == null || key.isBlank()) throw unavailable("Managed MCP gateway credential is unavailable.");
        String base = service.getBaseUrl().replaceAll("/+$", "");
        var response = http.managedGateway(URI.create(base + path), Map.of("X-MCP-GATEWAY-API-KEY", key),
            mapper.valueToTree(payload).toString(), "/api/internal/mcp/actions/execute".equals(path));
        if (response.status() < 200 || response.status() >= 300)
            throw unavailable("Managed MCP gateway returned HTTP " + response.status() + ".");
        return http.json(response);
    }
    private static ResponseStatusException unavailable(String reason) { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason); }
}
