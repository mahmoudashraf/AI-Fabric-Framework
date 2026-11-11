package com.ai.fabric.platform.backend.mcp.connection;

import static com.ai.fabric.platform.backend.mcp.connection.McpConnectionContracts.*;
import static org.springframework.http.HttpStatus.*;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.security.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class McpConnectionService {
    private static final Set<String> MODES = Set.of("NONE", "BEARER_TOKEN_SECRET_REF", "API_KEY_HEADER_SECRET", "MULTIPLE_HEADERS",
        "OAUTH_AUTHORIZATION_CODE", "OAUTH2_CLIENT_CREDENTIALS");
    private static final Set<String> BLOCKED_HEADERS = Set.of("host", "content-type", "content-length", "accept", "connection",
        "transfer-encoding", "cookie", "set-cookie", "proxy-authorization", "proxy-authenticate", "mcp-session-id", "mcp-protocol-version");
    private final McpConnectionRepository connections;
    private final McpDiscoverySnapshotRepository snapshots;
    private final McpImportReceiptRepository receipts;
    private final McpOAuthAttemptRepository attempts;
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    private final DeploymentTargetProfileService targets;
    private final DeploymentService drafts;
    private final McpOwnedSecrets secrets;
    private final McpBusinessOAuthService oauth;
    private final McpConnectionGatewayClient gateway;
    private final McpToolCatalogue catalogue;
    private final McpConnectionProperties properties;
    private final ObjectMapper mapper;
    private final PlatformAuditService audit;
    private final McpConnectionExecutionResolver executionResolver;

    public McpConnectionService(McpConnectionRepository connections, McpDiscoverySnapshotRepository snapshots,
        McpImportReceiptRepository receipts, McpOAuthAttemptRepository attempts, DeploymentRepository deployments,
        DeploymentAccessService access, DeploymentTargetProfileService targets, DeploymentService drafts,
        McpOwnedSecrets secrets, McpBusinessOAuthService oauth, McpConnectionGatewayClient gateway,
        McpToolCatalogue catalogue, McpConnectionProperties properties, ObjectMapper mapper, PlatformAuditService audit,
        McpConnectionExecutionResolver executionResolver) {
        this.connections = connections; this.snapshots = snapshots; this.receipts = receipts; this.attempts = attempts;
        this.deployments = deployments; this.access = access; this.targets = targets; this.drafts = drafts; this.secrets = secrets;
        this.oauth = oauth; this.gateway = gateway; this.catalogue = catalogue; this.properties = properties;
        this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS); this.audit = audit;
        this.executionResolver = executionResolver;
    }

    @Transactional(readOnly = true)
    public Catalogue list(String deploymentId, String targetProfileId) {
        requireEditor(deploymentId);
        return new Catalogue(deploymentId, connections.findByDeploymentIdOrderByCreatedAtAsc(deploymentId).stream()
            .filter(this::sameCustomer).map(this::summary).toList());
    }

    @Transactional
    public Connection create(String deploymentId, SaveConnection request) {
        var deployment = requireEditor(deploymentId);
        deployments.findByIdForUpdate(deploymentId).orElseThrow(McpConnectionService::missing);
        if (connections.countByDeploymentId(deploymentId) >= properties.getMaxConnectionsPerDeployment())
            throw bad("This Smart Brain has reached its MCP connection limit.");
        var connection = new McpConnectionEntity();
        connection.setId("mcp-" + UUID.randomUUID());
        connection.setServerRef("mcp_" + connection.getId().substring(4).replace("-", ""));
        connection.setDeploymentId(deploymentId); connection.setCustomerId(deployment.getCustomerId());
        connection.setRevision(1); connection.setCreatedAt(Instant.now());
        configure(connection, deployment, request);
        connection.setTargetBindingsJson(json(List.of(connection.getTargetProfileId())));
        connections.save(connection); record("MCP_CONNECTION_CREATED", connection, Map.of());
        return summary(connection);
    }

    @Transactional
    public Connection update(String deploymentId, String connectionId, SaveConnection request) {
        var deployment = requireEditor(deploymentId);
        var connection = ownedLocked(deploymentId, connectionId);
        requireRevision(connection, request == null ? null : request.expectedRevision());
        String originalTarget = connection.getTargetProfileId();
        var authority = List.of(connection.getEndpointUrl(), connection.getTargetProfileId(), connection.getConnectionModel(),
            connection.getTransport(), connection.getAuthConfigJson());
        configure(connection, deployment, request);
        boolean changed = !authority.equals(List.of(connection.getEndpointUrl(), connection.getTargetProfileId(), connection.getConnectionModel(),
            connection.getTransport(), connection.getAuthConfigJson()));
        if (changed) {
            connection.setEncryptedTokens(null); connection.setTokenExpiresAt(null); connection.setOauthMetadataJson(null);
            if ("OAUTH_AUTHORIZATION_CODE".equals(auth(connection).mode())) connection.setAuthStatus("AUTHORIZATION_REQUIRED");
            attempts.cancelForConnection(connectionId, Instant.now());
            if (!originalTarget.equals(connection.getTargetProfileId()))
                connection.setTargetBindingsJson(json(List.of(connection.getTargetProfileId())));
            else for (String allowed : bindings(connection)) normalizeAuth(deploymentId, allowed, connection.getConnectionModel(), auth(connection));
        }
        connection.setRevision(connection.getRevision() + 1); connection.setLastDiscoveryId(null);
        connections.save(connection); record("MCP_CONNECTION_UPDATED", connection, Map.of("authorityChanged", changed));
        return summary(connection);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Snapshot discover(String deploymentId, String connectionId) {
        requireEditor(deploymentId);
        var connection = ownedLocked(deploymentId, connectionId); requireActive(connection);
        var resolved = resolveCredentials(connection);
        JsonNode discovery = gateway.discover(connection.getServerRef(), resolved.server(), resolved.resolvedSecretValues());
        List<Tool> tools;
        try { tools = catalogue.read(discovery); }
        catch (IllegalArgumentException invalid) { throw bad(invalid.getMessage()); }
        boolean complete = discovery.path("ready").asBoolean() && discovery.path("complete").asBoolean(true);
        String id = "mcps-" + UUID.randomUUID();
        var snapshot = new Snapshot(id, connectionId, connection.getRevision(), Instant.now(), complete,
            discovery.path("protocolVersion").asText(null), tools,
            discoveryMessage(connection, discovery, complete));
        String json = json(snapshot);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > properties.getMaxSnapshotBytes())
            throw bad("MCP catalogue exceeds the configured snapshot size limit.");
        var entity = new McpDiscoverySnapshotEntity(); entity.setId(id); entity.setConnectionId(connectionId);
        entity.setConnectionRevision(connection.getRevision()); entity.setObservedAt(snapshot.observedAt());
        entity.setSnapshotJson(json); entity.setContentHash(McpToolCatalogue.hash(json)); snapshots.save(entity);
        connection.setLastDiscoveryId(id); connection.setUpdatedAt(Instant.now()); connections.save(connection);
        record("MCP_TOOLS_DISCOVERED", connection, Map.of("snapshotId", id, "complete", complete, "toolCount", tools.size()));
        return snapshot;
    }

    private String discoveryMessage(McpConnectionEntity connection, JsonNode discovery, boolean complete) {
        if (complete) return "Tool discovery completed. No business tools were executed.";
        String message = "Discovery is incomplete; tools cannot be imported.";
        if ("BUSINESS".equals(connection.getConnectionModel())) {
            message += switch (discovery.path("errorCode").asText("")) {
                case "MCP_UPSTREAM_AUTH_REQUIRED" -> " The company connection owner should reconnect or update the business authorization before discovering tools again.";
                case "MCP_UPSTREAM_ACCESS_DENIED" -> " The company connection owner should check the business connection permissions and reconnect before discovering tools again.";
                default -> "";
            };
        }
        if (Set.of("API_KEY_HEADER_SECRET", "MULTIPLE_HEADERS").contains(auth(connection).mode()))
            message += " Check the chosen header names against the gateway's approved header list.";
        return message;
    }

    @Transactional(readOnly = true)
    public Snapshot getSnapshot(String deploymentId, String connectionId, String snapshotId) {
        requireEditor(deploymentId);
        return snapshot(owned(deploymentId, connectionId), snapshotId);
    }

    @Transactional
    public ImportReceipt importTools(String deploymentId, String connectionId, ImportTools request) {
        requireEditor(deploymentId);
        deployments.findByIdForUpdate(deploymentId).orElseThrow(McpConnectionService::missing);
        var connection = ownedLocked(deploymentId, connectionId); requireActive(connection);
        if (request == null) throw bad("Choose discovered tools to import.");
        requireRevision(connection, request.connectionRevision());
        var snapshot = snapshot(connection, request.snapshotId());
        if (!snapshot.complete()) throw conflict("Refresh the incomplete catalogue before importing tools.");
        if (request.tools() == null || request.tools().isEmpty() || request.tools().size() > properties.getMaxTools())
            throw bad("Select at least one compatible tool within the catalogue limit.");
        Map<String, Tool> tools = new LinkedHashMap<>(); snapshot.tools().forEach(tool -> tools.put(tool.name(), tool));
        Set<String> selected = new HashSet<>();
        var draft = drafts.getActiveDraftForDeployment(deploymentId);
        JsonNode originalActions = draft.actionsConfig() == null ? mapper.createObjectNode() : draft.actionsConfig().deepCopy();
        ObjectNode config = draft.actionsConfig() != null && draft.actionsConfig().isObject()
            ? (ObjectNode) draft.actionsConfig().deepCopy() : mapper.createObjectNode();
        ArrayNode actions = array(config, "actions"); ArrayNode servers = array(config, "mcpServers");
        List<String> names = new ArrayList<>(); List<ActionChange> changes = new ArrayList<>(); int created = 0, updated = 0;
        for (ToolSelection selection : request.tools()) {
            if (selection == null || selection.toolName() == null || !selected.add(selection.toolName())) throw bad("Select each tool once.");
            Tool tool = tools.get(selection.toolName());
            if (tool == null || !tool.compatible()) throw bad("A selected tool is absent or incompatible in this snapshot.");
            String name = "mcp_" + McpToolCatalogue.hash(connectionId + "\n" + tool.name()).substring(0, 24);
            int existing = index(actions, "name", name);
            JsonNode before = existing < 0 ? null : actionProjection(actions.get(existing));
            ObjectNode action;
            if (existing >= 0) {
                JsonNode old = actions.get(existing);
                if (!connectionId.equals(old.path("execution").path("mcp").path("connectionId").asText())
                    || !tool.name().equals(old.path("execution").path("mcp").path("toolName").asText()))
                    throw conflict("An unrelated action already uses the imported action identity.");
                action = (ObjectNode) old.deepCopy(); updated++;
            } else { action = mapper.createObjectNode(); created++; }
            bindAction(action, connection, tool, selection, name);
            if (existing < 0) actions.add(action); else actions.set(existing, action);
            names.add(name);
            changes.add(new ActionChange(name, tool.name(), existing < 0 ? "CREATED" : "UPDATED", before, actionProjection(action)));
        }
        ObjectNode server = server(connection);
        int existingServer = index(servers, "serverRef", connection.getServerRef());
        if (existingServer >= 0 && !connectionId.equals(servers.get(existingServer).path("connectionId").asText()))
            throw conflict("An unrelated MCP server already uses this server identity.");
        // Retain existing selected tools unless explicitly removed in the normal action editor.
        ArrayNode allowed = server.putArray("allowedTools"); Set<String> allowedNames = new LinkedHashSet<>();
        for (JsonNode action : actions) if (connectionId.equals(action.path("execution").path("mcp").path("connectionId").asText()))
            allowedNames.add(action.path("execution").path("mcp").path("toolName").asText());
        allowedNames.forEach(allowed::add);
        if (existingServer < 0) servers.add(server); else servers.set(existingServer, server);
        var saved = drafts.updateDraft(draft.id(), new UpdateDeploymentDraftRequest(config, null, null, null, null, null));
        var receipt = new ImportReceipt("mcpi-" + UUID.randomUUID(), connectionId, snapshot.id(), saved.id(),
            saved.revisionNumber(), created, updated, List.copyOf(names), Instant.now(), List.copyOf(changes));
        var entity = new McpImportReceiptEntity(); entity.setId(receipt.id()); entity.setConnectionId(connectionId);
        entity.setSnapshotId(snapshot.id()); entity.setDraftId(saved.id()); entity.setCreatedAt(receipt.createdAt());
        entity.setReceiptJson(json(receipt)); entity.setBeforeActionsJson(originalActions.toString());
        entity.setAfterActionsHash(McpToolCatalogue.hash(saved.actionsConfig().toString())); receipts.save(entity);
        record("MCP_TOOLS_IMPORTED", connection, Map.of("receiptId", receipt.id(), "snapshotId", snapshot.id(),
            "draftId", saved.id(), "created", created, "updated", updated));
        return receipt;
    }

    @Transactional
    public UndoReceipt undoImport(String deploymentId, String connectionId, String receiptId) {
        requireEditor(deploymentId);
        deployments.findByIdForUpdate(deploymentId).orElseThrow(McpConnectionService::missing);
        var connection = ownedLocked(deploymentId, connectionId);
        var receipt = receipts.findById(receiptId).filter(r -> connectionId.equals(r.getConnectionId())).orElseThrow(McpConnectionService::missing);
        if (receipt.getUndoneAt() != null) throw conflict("This import has already been undone.");
        var draft = drafts.getActiveDraftForDeployment(deploymentId);
        if (!receipt.getDraftId().equals(draft.id()) || draft.actionsConfig() == null
            || !McpToolCatalogue.hash(draft.actionsConfig().toString()).equals(receipt.getAfterActionsHash()))
            throw conflict("The action draft changed after this import. Review or edit it normally; undo will not overwrite newer changes.");
        JsonNode original;
        try { original = mapper.readTree(receipt.getBeforeActionsJson()); }
        catch (Exception invalid) { throw new IllegalStateException("Stored import undo metadata is invalid."); }
        var saved = drafts.updateDraft(draft.id(), new UpdateDeploymentDraftRequest(original, null, null, null, null, null));
        receipt.setUndoneAt(Instant.now()); receipt.setUndoDraftRevision(saved.revisionNumber()); receipts.save(receipt);
        record("MCP_IMPORT_UNDONE", connection, Map.of("receiptId", receiptId, "draftId", saved.id()));
        return new UndoReceipt(receiptId, saved.id(), saved.revisionNumber(), receipt.getUndoneAt());
    }

    @Transactional
    public Connection disconnect(String deploymentId, String connectionId) {
        requireEditor(deploymentId);
        var connection = ownedLocked(deploymentId, connectionId);
        oauth.disconnect(connection);
        connection.setStatus("DISCONNECTED"); connection.setRevision(connection.getRevision() + 1);
        connection.setLastDiscoveryId(null); connection.setUpdatedAt(Instant.now());
        attempts.cancelForConnection(connectionId, Instant.now()); connections.save(connection);
        record("MCP_CONNECTION_DISCONNECTED", connection, Map.of());
        return summary(connection);
    }

    @Transactional
    public Connection bindTarget(String deploymentId, String connectionId, TargetBinding request) {
        var deployment = requireEditor(deploymentId);
        var connection = ownedLocked(deploymentId, connectionId);
        if (request == null || request.targetProfileId() == null || request.targetProfileId().isBlank()) throw bad("A selected environment is required.");
        requireRevision(connection, request.expectedRevision());
        var target = targets.requireActiveProfile(request.targetProfileId());
        DeploymentEnvironmentPolicyGuard.requireAllowed(deployment, target);
        var allowed = new LinkedHashSet<>(bindings(connection));
        if (request.enabled()) {
            // Reuse is explicit and cannot escape a target-scoped key's existing ownership policy.
            normalizeAuth(deploymentId, request.targetProfileId(), connection.getConnectionModel(), auth(connection));
            allowed.add(request.targetProfileId());
        } else allowed.remove(request.targetProfileId());
        connection.setTargetBindingsJson(json(List.copyOf(allowed))); connection.setUpdatedAt(Instant.now()); connections.save(connection);
        record("MCP_CONNECTION_TARGET_BINDING_UPDATED", connection, Map.of("boundTargetProfileId", request.targetProfileId(), "enabled", request.enabled()));
        return summary(connection);
    }

    /** Internal service method: MAIN authenticates target runtime and reconstructs the actual applied action. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ExecutionResolution resolveExecution(String connectionId, String deploymentId, String targetProfileId,
        String toolName, String expectedSchemaHash) {
        return executionResolver.resolveExecution(connectionId, deploymentId, targetProfileId, toolName, expectedSchemaHash);
    }

    @Transactional(readOnly = true)
    public JsonNode getAssistantCatalogue(String deploymentId, String connectionId, String snapshotId, List<String> toolNames) {
        requireEditor(deploymentId);
        var snapshot = snapshot(owned(deploymentId, connectionId), snapshotId);
        if (toolNames == null || toolNames.isEmpty() || toolNames.size() > properties.getMaxTools()) throw bad("Choose tools for assistance.");
        var root = mapper.createObjectNode(); var result = root.putArray("tools");
        Set<String> requested = new LinkedHashSet<>(toolNames);
        for (Tool tool : snapshot.tools()) if (requested.remove(tool.name())) {
            var value = result.addObject().put("name", tool.name());
            if (tool.title() != null) value.put("title", tool.title());
            if (tool.description() != null) value.put("description", tool.description());
            value.set("inputSchema", tool.inputSchema());
            if (tool.outputSchema() != null) value.set("outputSchema", tool.outputSchema());
        }
        if (!requested.isEmpty()) throw bad("A selected tool is not in this owned catalogue.");
        return root;
    }

    private void configure(McpConnectionEntity c, DeploymentEntity deployment, SaveConnection r) {
        if (r == null || r.name() == null || r.name().isBlank() || r.name().length() > 160) throw bad("Provide a connection name of at most 160 characters.");
        if (!Set.of("PUBLIC", "BUSINESS").contains(String.valueOf(r.connectionModel()))) throw bad("Choose Public or Business-authorized.");
        if (r.targetProfileId() == null || r.targetProfileId().isBlank()) throw bad("A selected Smart Brain environment is required.");
        var target = targets.requireActiveProfile(r.targetProfileId()); DeploymentEnvironmentPolicyGuard.requireAllowed(deployment, target);
        String endpoint = McpSafeHttpTransport.validate(r.endpointUrl()).toString();
        String transport = r.transport() == null ? "AUTO" : r.transport();
        if (!Set.of("AUTO", "STREAMABLE_HTTP", "SSE").contains(transport)) throw bad("Choose a supported remote MCP transport.");
        AuthSettings auth = normalizeAuth(deployment.getId(), r.targetProfileId(), r.connectionModel(), r.auth());
        c.setName(r.name().trim()); c.setEndpointUrl(endpoint); c.setTargetProfileId(r.targetProfileId());
        c.setConnectionModel(r.connectionModel()); c.setTransport("AUTO".equals(transport) ? "STREAMABLE_HTTP" : transport); c.setAuthConfigJson(json(auth));
        c.setStatus("ACTIVE"); c.setAuthStatus("PUBLIC".equals(r.connectionModel()) ? "NOT_REQUIRED"
            : "OAUTH_AUTHORIZATION_CODE".equals(auth.mode()) ? (c.getEncryptedTokens() == null || c.getAuthStatus() == null
                ? "AUTHORIZATION_REQUIRED" : c.getAuthStatus()) : "CONFIGURED");
        c.setUpdatedAt(Instant.now());
    }

    private AuthSettings normalizeAuth(String deploymentId, String target, String model, AuthSettings auth) {
        if ("PUBLIC".equals(model)) {
            if (auth != null && (!"NONE".equals(auth.mode()) || auth.secretRef() != null || auth.oauth() != null
                || (auth.headers() != null && !auth.headers().isEmpty()))) throw bad("Public connections cannot contain credentials or OAuth settings.");
            return new AuthSettings("NONE", null, null, List.of(), null);
        }
        if (auth == null || !MODES.contains(String.valueOf(auth.mode())) || "NONE".equals(auth.mode()))
            throw bad("Choose business authentication and an owned integration key or OAuth configuration.");
        switch (auth.mode()) {
            case "BEARER_TOKEN_SECRET_REF" -> {
                secrets.check(deploymentId, target, auth.secretRef());
                return new AuthSettings(auth.mode(), auth.secretRef(), null, List.of(), null);
            }
            case "API_KEY_HEADER_SECRET" -> {
                header(auth.headerName()); secrets.check(deploymentId, target, auth.secretRef());
                return new AuthSettings(auth.mode(), auth.secretRef(), auth.headerName(), List.of(), null);
            }
            case "MULTIPLE_HEADERS" -> {
                if (auth.headers() == null || auth.headers().isEmpty() || auth.headers().size() > 8) throw bad("Provide one to eight credential headers.");
                Set<String> names = new HashSet<>();
                for (var h : auth.headers()) {
                    if (h == null) throw bad("Provide a header name and owned key.");
                    header(h.name()); if (!names.add(h.name().toLowerCase(Locale.ROOT))) throw bad("Credential header names must be unique.");
                    secrets.check(deploymentId, target, h.secretRef());
                }
                return new AuthSettings(auth.mode(), null, null, List.copyOf(auth.headers()), null);
            }
            default -> {
                var settings = oauth.validateSettings(auth.oauth(), "OAUTH2_CLIENT_CREDENTIALS".equals(auth.mode()));
                if (settings.clientSecretRef() != null) secrets.check(deploymentId, target, settings.clientSecretRef());
                return new AuthSettings(auth.mode(), null, null, List.of(), settings);
            }
        }
    }

    private void bindAction(ObjectNode action, McpConnectionEntity c, Tool tool, ToolSelection selected, String name) {
        String accessMode = selected.accessMode() == null ? action.path("accessMode").asText("READ_WRITE") : selected.accessMode();
        if ("WRITE".equals(accessMode)) accessMode = "WRITE_ONLY";
        if (!Set.of("READ", "READ_WRITE", "WRITE_ONLY").contains(accessMode)) throw bad("Choose a supported action access mode.");
        if (Boolean.TRUE.equals(selected.readGrounding()) && !"READ".equals(accessMode)) throw bad("Only explicitly trusted READ tools can supply read grounding.");
        if (!action.hasNonNull("displayName")) action.put("displayName", tool.title() == null || tool.title().isBlank() ? tool.name() : tool.title());
        if (!action.hasNonNull("description")) action.put("description", tool.description() == null || tool.description().isBlank() ? "Call MCP tool " + tool.name() : tool.description());
        action.put("name", name)
            .put("category", "mcp").put("adapterType", "mcp-tool").put("accessMode", accessMode)
            .put("requiresConfirmation", selected.requiresConfirmation() == null ? action.path("requiresConfirmation").asBoolean(true) : selected.requiresConfirmation())
            .put("anonymousAllowed", selected.allowAnonymous() == null ? action.path("anonymousAllowed").asBoolean() : selected.allowAnonymous())
            .put("groundingEligible", selected.readGrounding() == null ? action.path("groundingEligible").asBoolean() : selected.readGrounding())
            .put("readActionResolutionEligible", "READ".equals(accessMode))
            .put("enabled", selected.enabled() == null ? action.path("enabled").asBoolean(true) : selected.enabled());
        if (action.path("groundingEligible").asBoolean() && !"READ".equals(accessMode)) throw bad("Only explicitly trusted READ tools can supply read grounding.");
        action.set("inputSchema", tool.inputSchema().deepCopy());
        if (tool.outputSchema() == null) action.remove("outputSchema"); else action.set("outputSchema", tool.outputSchema().deepCopy());
        var projected = catalogue.parameters(tool.inputSchema());
        for (JsonNode param : projected) for (JsonNode old : action.path("params"))
            if (param.path("name").asText().equals(old.path("name").asText()))
                old.fields().forEachRemaining(field -> {
                    if (!Set.of("name", "type", "required", "description").contains(field.getKey()))
                        ((ObjectNode) param).set(field.getKey(), field.getValue().deepCopy());
                });
        action.set("params", projected);
        var execution = action.with("execution"); execution.put("adapterType", "mcp-tool");
        execution.putObject("retry").put("maxAttempts", 1);
        var mcp = execution.putObject("mcp").put("connectionId", c.getId()).put("connectionRevision", c.getRevision())
            .put("targetProfileId", c.getTargetProfileId()).put("serverRef", c.getServerRef()).put("toolName", tool.name())
            .put("toolSchemaHash", tool.schemaHash()).put("schemaDriftPolicy", "BLOCK_RELEASE");
        if (tool.execution() != null && tool.execution().path("taskSupport").isTextual())
            mcp.set("taskSupport", tool.execution().get("taskSupport"));
    }

    private ExecutionResolution resolveCredentials(McpConnectionEntity c) {
        return executionResolver.credentials(c, c.getTargetProfileId());
    }

    private ObjectNode server(McpConnectionEntity c) {
        return executionResolver.server(c);
    }

    private JsonNode actionProjection(JsonNode action) {
        var result = mapper.createObjectNode();
        for (String key : List.of("name", "displayName", "description", "accessMode", "requiresConfirmation", "anonymousAllowed",
            "groundingEligible", "enabled", "inputSchema", "outputSchema")) if (action.has(key)) result.set(key, action.get(key).deepCopy());
        if (action.path("execution").path("mcp").has("toolSchemaHash"))
            result.set("toolSchemaHash", action.path("execution").path("mcp").get("toolSchemaHash"));
        return result;
    }

    private Snapshot snapshot(McpConnectionEntity connection, String id) {
        var entity = snapshots.findById(id == null ? "" : id).orElseThrow(McpConnectionService::missing);
        if (!connection.getId().equals(entity.getConnectionId()) || entity.getConnectionRevision() != connection.getRevision())
            throw conflict("Refresh the catalogue after the connection changed.");
        if (!McpToolCatalogue.hash(entity.getSnapshotJson()).equals(entity.getContentHash())) throw conflict("Stored MCP catalogue integrity check failed.");
        return read(entity.getSnapshotJson(), Snapshot.class);
    }
    private DeploymentEntity requireEditor(String id) {
        enabled(); if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(UNAUTHORIZED);
        var deployment = deployments.findById(id).orElseThrow(McpConnectionService::missing);
        if (PlatformSecurityContext.currentPrincipal().role() == PlatformRole.PLATFORM_OPERATOR)
            access.requireStaffCustomerAccess(deployment.getCustomerId());
        access.requireDeploymentEditorAccess(deployment);
        if (deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())) throw conflict("Restore this Smart Brain before editing its connections.");
        return deployment;
    }
    private void enabled() { if (!properties.isEnabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "MCP connections are disabled."); }
    private McpConnectionEntity owned(String deploymentId, String id) {
        return connections.findById(id).filter(c -> deploymentId.equals(c.getDeploymentId()) && sameCustomer(c)).orElseThrow(McpConnectionService::missing);
    }
    private McpConnectionEntity ownedLocked(String deploymentId, String id) {
        return connections.findLocked(id).filter(c -> deploymentId.equals(c.getDeploymentId()) && sameCustomer(c)).orElseThrow(McpConnectionService::missing);
    }
    private boolean sameCustomer(McpConnectionEntity c) { return deployments.findById(c.getDeploymentId())
        .map(d -> Objects.equals(d.getCustomerId(), c.getCustomerId())).orElse(false); }
    Connection summary(McpConnectionEntity c) {
        return new Connection(c.getId(), c.getDeploymentId(), c.getCustomerId(), c.getTargetProfileId(), c.getServerRef(), c.getName(),
            c.getEndpointUrl(), c.getConnectionModel(), c.getTransport(), auth(c), c.getRevision(), c.getStatus(), c.getAuthStatus(),
            c.getTokenExpiresAt(), c.getLastDiscoveryId(), c.getCreatedAt(), c.getUpdatedAt(), bindings(c));
    }
    private List<String> bindings(McpConnectionEntity connection) {
        return executionResolver.bindings(connection);
    }
    private AuthSettings auth(McpConnectionEntity c) { return read(c.getAuthConfigJson(), AuthSettings.class); }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); } catch (Exception invalid) { throw new IllegalStateException("Stored MCP metadata is invalid."); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception invalid) { throw new IllegalStateException("MCP metadata could not be serialized."); }
    }
    private ArrayNode array(ObjectNode root, String key) {
        if (root.has(key) && !root.path(key).isArray()) throw conflict("The existing draft contains invalid " + key + "; correct it before import.");
        if (!root.has(key)) root.putArray(key);
        return (ArrayNode) root.get(key);
    }
    private static int index(ArrayNode values, String key, String value) {
        int found = -1;
        for (int i = 0; i < values.size(); i++) if (value.equals(values.get(i).path(key).asText())) {
            if (found >= 0) throw conflict("The draft contains duplicate configuration identities."); found = i;
        }
        return found;
    }
    private static void header(String value) {
        if (value == null || !value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")
            || BLOCKED_HEADERS.contains(value.toLowerCase(Locale.ROOT)) || value.toLowerCase(Locale.ROOT).startsWith("sec-"))
            throw bad("Choose an API credential header, not an HTTP routing, cookie or MCP protocol header.");
    }
    static void requireRevision(McpConnectionEntity c, Long revision) {
        if (revision == null || c.getRevision() != revision) throw conflict("This connection changed. Reload it before continuing.");
    }
    private static void requireActive(McpConnectionEntity c) {
        if (!"ACTIVE".equals(c.getStatus())) throw conflict("Reconnect this MCP connection before using it.");
    }
    private void record(String event, McpConnectionEntity c, Map<String, Object> additional) {
        var details = new LinkedHashMap<String, Object>(additional); details.put("connectionId", c.getId());
        details.put("targetProfileId", c.getTargetProfileId()); details.put("connectionRevision", c.getRevision());
        audit.record(event, "DEPLOYMENT", c.getDeploymentId(), details);
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(NOT_FOUND, "MCP connection or catalogue not found in this Smart Brain."); }
    private static ResponseStatusException bad(String reason) { return new ResponseStatusException(BAD_REQUEST, reason); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(CONFLICT, reason); }
}
