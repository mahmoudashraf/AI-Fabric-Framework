package com.ai.fabric.platform.backend.mcp.connection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Customer-owned business integrations only; no visitor OAuth or raw browser credentials. */
public final class McpConnectionContracts {
    private McpConnectionContracts() { }

    public record HeaderCredential(String name, String secretRef) { }
    public record OAuthSettings(String authorizationServerUrl, String resourceUrl, String registrationMode,
        String clientId, String clientSecretRef, String clientAuthenticationMethod, List<String> scopes) { }
    public record AuthSettings(String mode, String secretRef, String headerName,
        List<HeaderCredential> headers, OAuthSettings oauth) { }
    public record SaveConnection(Long expectedRevision, String name, String endpointUrl, String targetProfileId,
        String connectionModel, String transport, AuthSettings auth) { }
    public record Connection(String id, String deploymentId, String customerId, String targetProfileId,
        String serverRef, String name, String endpointUrl, String connectionModel, String transport,
        AuthSettings auth, long revision, String status, String authStatus, Instant tokenExpiresAt,
        String lastDiscoveryId, Instant createdAt, Instant updatedAt, List<String> allowedTargetProfileIds) { }
    public record Catalogue(String deploymentId, List<Connection> connections) { }
    public record Tool(String name, String title, String description, JsonNode inputSchema, JsonNode outputSchema,
        JsonNode annotations, JsonNode execution, String schemaHash, boolean compatible, List<String> findings) { }
    public record Snapshot(String id, String connectionId, long connectionRevision, Instant observedAt,
        boolean complete, String protocolVersion, List<Tool> tools, String message) { }
    public record ToolSelection(String toolName, String accessMode, Boolean requiresConfirmation,
        Boolean allowAnonymous, Boolean readGrounding, Boolean enabled) { }
    public record ImportTools(String snapshotId, long connectionRevision, List<ToolSelection> tools) { }
    public record ImportReceipt(String id, String connectionId, String snapshotId, String draftId,
        int draftRevision, int createdCount, int updatedCount, List<String> actionNames, Instant createdAt,
        List<ActionChange> changes) { }
    public record ActionChange(String actionName, String toolName, String changeType, JsonNode before, JsonNode after) { }
    public record UndoReceipt(String importReceiptId, String draftId, int draftRevision, Instant restoredAt) { }
    public record Authorize(Long expectedRevision, String returnPath) { }
    public record TargetBinding(Long expectedRevision, String targetProfileId, boolean enabled) { }
    public record AuthorizationStart(String authorizationUrl, Instant expiresAt, String connectionId) { }
    public record CallbackResult(String returnPath, String outcome) { }

    /** INTERNAL ONLY. The authenticated runtime bridge must establish deployment/target and applied action first. */
    public record ExecutionResolution(JsonNode server, Map<String, String> resolvedSecretValues) {
        @Override public String toString() { return "McpExecutionResolution[credentials=redacted]"; }
    }
}
