package com.ai.fabric.platform.backend.mcp.connection;

import static com.ai.fabric.platform.backend.mcp.connection.McpConnectionContracts.*;
import static org.springframework.http.HttpStatus.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read/execution side deliberately has no draft service or provisioning dependency. */
@Service
public class McpConnectionExecutionResolver {
    private final McpConnectionRepository connections;
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileService targets;
    private final McpOwnedSecrets secrets;
    private final McpBusinessOAuthService oauth;
    private final McpConnectionProperties properties;
    private final ObjectMapper mapper;
    public McpConnectionExecutionResolver(McpConnectionRepository connections, DeploymentRepository deployments,
        DeploymentTargetProfileService targets, McpOwnedSecrets secrets, McpBusinessOAuthService oauth,
        McpConnectionProperties properties, ObjectMapper mapper) {
        this.connections = connections; this.deployments = deployments; this.targets = targets; this.secrets = secrets;
        this.oauth = oauth; this.properties = properties; this.mapper = mapper;
    }

    // A provider's rotated refresh token cannot be rolled back when a later apply or tool call fails.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
        noRollbackFor = ResponseStatusException.class)
    public ExecutionResolution resolveExecution(String connectionId, String deploymentId, String targetProfileId,
        String toolName, String expectedSchemaHash) {
        if (!properties.isEnabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "MCP connections are disabled.");
        var c = connections.findLocked(connectionId).filter(row -> deploymentId.equals(row.getDeploymentId())).orElseThrow(McpConnectionExecutionResolver::missing);
        if (!bindings(c).contains(targetProfileId) || toolName == null || toolName.isBlank() || expectedSchemaHash == null || expectedSchemaHash.isBlank()) throw missing();
        var deployment = deployments.findById(deploymentId).orElseThrow(McpConnectionExecutionResolver::missing);
        if (!Objects.equals(deployment.getCustomerId(), c.getCustomerId()) || deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())) throw missing();
        if (!"ACTIVE".equals(c.getStatus())) throw new ResponseStatusException(CONFLICT, "Reconnect this MCP connection before using it.");
        DeploymentEnvironmentPolicyGuard.requireAllowed(deployment, targets.requireActiveProfile(targetProfileId));
        var resolved = credentials(c, targetProfileId);
        ((ObjectNode) resolved.server()).put("targetProfileId", targetProfileId).putArray("allowedTools").add(toolName);
        return resolved;
    }

    ExecutionResolution credentials(McpConnectionEntity c, String targetProfileId) {
        var server = server(c); var values = new LinkedHashMap<String, String>();
        var auth = auth(c);
        if ("PUBLIC".equals(c.getConnectionModel())) return new ExecutionResolution(server, Map.of());
        if (auth.mode().startsWith("OAUTH")) {
            if (auth.oauth().clientSecretRef() != null) secrets.check(c.getDeploymentId(), targetProfileId, auth.oauth().clientSecretRef());
            String token = oauth.accessToken(c);
            String ref = "MCP_SECRET_CONNECTION_" + McpToolCatalogue.hash(c.getId()).substring(0, 24).toUpperCase(Locale.ROOT);
            server.putObject("auth").put("mode", "BEARER_TOKEN_SECRET_REF").put("secretRef", ref); values.put(ref, token);
        } else if ("MULTIPLE_HEADERS".equals(auth.mode())) {
            for (var h : auth.headers()) values.put(h.secretRef(), secrets.resolve(c.getDeploymentId(), targetProfileId, h.secretRef()));
        } else values.put(auth.secretRef(), secrets.resolve(c.getDeploymentId(), targetProfileId, auth.secretRef()));
        return new ExecutionResolution(server, Map.copyOf(values));
    }

    ObjectNode server(McpConnectionEntity c) {
        var result = mapper.createObjectNode().put("serverRef", c.getServerRef()).put("connectionId", c.getId())
            .put("connectionRevision", c.getRevision()).put("targetProfileId", c.getTargetProfileId())
            .put("transport", "SSE".equals(c.getTransport()) ? "HTTP_SSE" : "STREAMABLE_HTTP").put("endpointUrl", c.getEndpointUrl());
        result.putObject("auth").put("mode", "NONE");
        result.set("allowedTargetProfileIds", mapper.valueToTree(bindings(c)));
        result.putObject("verification").put("mode", "INITIALIZE_AND_TOOLS_LIST").put("schemaDriftPolicy", "BLOCK_RELEASE");
        var auth = auth(c);
        if (!"PUBLIC".equals(c.getConnectionModel()) && !auth.mode().startsWith("OAUTH")) {
            if ("MULTIPLE_HEADERS".equals(auth.mode())) {
                var gatewayAuth = result.putObject("auth").put("mode", "MULTIPLE_HEADERS_SECRET_REFS"); var headers = gatewayAuth.putArray("headers");
                for (var h : auth.headers()) headers.addObject().put("headerName", h.name()).put("secretRef", h.secretRef());
            } else result.set("auth", mapper.valueToTree(auth));
        }
        return result;
    }

    List<String> bindings(McpConnectionEntity c) {
        try {
            JsonNode values = mapper.readTree(c.getTargetBindingsJson());
            if (!values.isArray() || values.size() > 16) throw new IllegalArgumentException();
            var result = new ArrayList<String>();
            for (JsonNode value : values) {
                if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 128 || result.contains(value.asText())) throw new IllegalArgumentException();
                result.add(value.asText());
            }
            return List.copyOf(result);
        } catch (Exception invalid) { throw new IllegalStateException("Stored MCP target bindings are invalid."); }
    }
    private AuthSettings auth(McpConnectionEntity c) {
        try { return mapper.readValue(c.getAuthConfigJson(), AuthSettings.class); }
        catch (Exception invalid) { throw new IllegalStateException("Stored MCP authentication settings are invalid."); }
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(NOT_FOUND, "MCP connection is not authorized for this Smart Brain and target."); }
}
