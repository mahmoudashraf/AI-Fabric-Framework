package com.ai.fabric.platform.backend.mcp.connection;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.setup.SetupCheckContracts.ServiceResult;
import com.ai.fabric.platform.backend.mcp.runtime.McpConnectionReadiness;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Existing setup/apply integration only. No business execution, draft writes or provisioning dependency. */
@Service
public class McpConnectionReadinessAdapter implements McpConnectionReadiness {
    private final McpConnectionRepository connections;
    private final McpDiscoverySnapshotRepository snapshots;
    private final DeploymentRepository deployments;
    private final McpConnectionExecutionResolver resolver;
    private final McpConnectionGatewayClient gateway;
    private final ObjectMapper mapper;
    public McpConnectionReadinessAdapter(McpConnectionRepository connections, McpDiscoverySnapshotRepository snapshots,
        DeploymentRepository deployments, McpConnectionExecutionResolver resolver, McpConnectionGatewayClient gateway, ObjectMapper mapper) {
        this.connections = connections; this.snapshots = snapshots; this.deployments = deployments;
        this.resolver = resolver; this.gateway = gateway; this.mapper = mapper;
    }

    @Override @Transactional(readOnly = true)
    public List<ServiceResult> inspect(String deploymentId, JsonNode actions) {
        var result = new ArrayList<ServiceResult>();
        groups(actions).forEach((id, tools) -> {
            var row = connections.findById(id).filter(c -> deploymentId.equals(c.getDeploymentId()) && sameCustomer(c)).orElse(null);
            var usedBy = tools.stream().map(t -> t.path("displayName").asText(t.path("name").asText())).toList();
            String label = row == null ? "MCP connection" : row.getName();
            String status = "NOT_TESTED", message = "Discover and import this connection's tools before deployment.";
            Instant checkedAt = null;
            if (row == null) { status = "FAILED"; message = "The configured MCP connection is not owned by this Smart Brain."; }
            else if (!"ACTIVE".equals(row.getStatus()) || Set.of("RECONNECT_REQUIRED", "AUTHORIZATION_REQUIRED").contains(row.getAuthStatus())) {
                status = "FAILED"; message = "Connect or reconnect this business integration before using its tools.";
            } else if (tools.stream().anyMatch(t -> t.path("execution").path("mcp").path("connectionRevision").asLong(-1) != row.getRevision())) {
                status = "FAILED"; message = "Connection settings changed. Refresh and import its tools again before publishing.";
            } else {
                try {
                    JsonNode observed = row.getLastVerificationJson() == null ? null : mapper.readTree(row.getLastVerificationJson());
                    if (observed != null && observed.path("connectionRevision").asLong(-1) == row.getRevision() && matches(observed.path("tools"), tools)) {
                        checkedAt = Instant.parse(observed.path("checkedAt").asText());
                        status = observed.path("ready").asBoolean() ? "VERIFIED" : "FAILED";
                        message = status.equals("VERIFIED") ? "Selected tools passed the saved MCP metadata verification. Apply checks the target again."
                            : "The latest MCP metadata verification failed. Recheck the connection before applying.";
                    } else if (row.getLastDiscoveryId() != null) {
                        var snapshot = snapshots.findById(row.getLastDiscoveryId()).filter(s -> id.equals(s.getConnectionId()) && s.getConnectionRevision() == row.getRevision()).orElse(null);
                        if (snapshot != null && McpToolCatalogue.hash(snapshot.getSnapshotJson()).equals(snapshot.getContentHash())) {
                            JsonNode saved = mapper.readTree(snapshot.getSnapshotJson()); checkedAt = snapshot.getObservedAt();
                            if (saved.path("complete").asBoolean() && matches(saved.path("tools"), tools)) {
                                status = "VERIFIED"; message = "Selected tools match the saved discovery snapshot. Apply performs live target verification.";
                            } else { status = "FAILED"; message = "Selected tools no longer match a complete saved catalogue. Refresh discovery."; }
                        }
                    }
                } catch (Exception malformed) { status = "FAILED"; message = "Stored MCP verification evidence is invalid. Refresh discovery."; }
            }
            result.add(new ServiceResult("mcp:" + id, label, status, message, checkedAt, usedBy, null));
        });
        return List.copyOf(result);
    }

    @Override @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verify(String deploymentId, String targetProfileId, JsonNode actions) {
        for (var entry : groups(actions).entrySet()) {
            String id = entry.getKey(); List<JsonNode> selected = entry.getValue(); JsonNode first = selected.getFirst().path("execution").path("mcp");
            var resolved = resolver.resolveExecution(id, deploymentId, targetProfileId, first.path("toolName").asText(), first.path("toolSchemaHash").asText());
            long revision = resolved.server().path("connectionRevision").asLong(-1);
            var expected = mapper.createArrayNode(); var seen = new HashMap<String, String>();
            for (JsonNode action : selected) {
                JsonNode mcp = action.path("execution").path("mcp");
                if (mcp.path("connectionRevision").asLong(-2) != revision || !mcp.path("serverRef").asText().equals(resolved.server().path("serverRef").asText()))
                    throw failure("MCP connection changed since tool import. Refresh and import before applying.");
                String tool = mcp.path("toolName").asText(), hash = mcp.path("toolSchemaHash").asText();
                if (tool.isBlank() || hash.isBlank() || seen.containsKey(tool) && !hash.equals(seen.get(tool))) throw failure("The MCP tool binding is incomplete or ambiguous.");
                if (!seen.containsKey(tool)) expected.addObject().put("name", tool).put("schemaHash", hash).put("schemaDriftPolicy", "BLOCK_RELEASE");
                seen.put(tool, hash);
            }
            var server = (ObjectNode) resolved.server(); var allowed = server.putArray("allowedTools"); seen.keySet().forEach(allowed::add);
            JsonNode response = gateway.verify(server.path("serverRef").asText(), server, resolved.resolvedSecretValues(), expected);
            var record = mapper.createObjectNode().put("connectionRevision", revision).put("targetProfileId", targetProfileId)
                .put("checkedAt", Instant.now().toString()).put("ready", response.path("ready").asBoolean()); record.set("tools", expected);
            var row = connections.findLocked(id).orElseThrow(() -> failure("MCP connection is unavailable.")); row.setLastVerificationJson(record.toString()); connections.save(row);
            if (!response.path("ready").asBoolean()) throw failure("MCP tool metadata verification failed. No business tool was executed.");
        }
    }

    private boolean sameCustomer(McpConnectionEntity row) { return deployments.findById(row.getDeploymentId())
        .map(d -> Objects.equals(d.getCustomerId(), row.getCustomerId())).orElse(false); }
    private boolean matches(JsonNode observed, List<JsonNode> actions) {
        var hashes = new HashMap<String, String>();
        for (JsonNode tool : observed) hashes.put(tool.path("name").asText(), tool.path("schemaHash").asText());
        return actions.stream().allMatch(a -> {
            JsonNode mcp = a.path("execution").path("mcp"); return Objects.equals(hashes.get(mcp.path("toolName").asText()), mcp.path("toolSchemaHash").asText());
        });
    }
    private static Map<String, List<JsonNode>> groups(JsonNode actions) {
        var result = new LinkedHashMap<String, List<JsonNode>>();
        for (JsonNode action : actions.path("actions")) if (action.path("enabled").asBoolean(true)
            && "mcp-tool".equals(action.path("adapterType").asText()) && action.path("execution").path("mcp").path("connectionId").isTextual())
            result.computeIfAbsent(action.path("execution").path("mcp").path("connectionId").asText(), key -> new ArrayList<>()).add(action);
        return result;
    }
    private static ResponseStatusException failure(String reason) { return new ResponseStatusException(CONFLICT, reason); }
}
