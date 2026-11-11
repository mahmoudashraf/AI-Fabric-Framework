package com.ai.fabric.platform.backend.mcp.runtime;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;

/** Reconstructs execution from the applied release, never caller-supplied action/secret metadata. */
@Service
public class McpRuntimeExecutionService {
    private static final Set<String> ACTIVE_RELEASES = Set.of("VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED");
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetContextService targets;
    private final DeploymentRuntimeAuthorityService authority;
    private final McpRuntimeConnectionResolver connections;
    private final ObjectMapper mapper;

    public McpRuntimeExecutionService(DeploymentRepository deployments, DeploymentReleaseRepository releases,
        DeploymentVersionRepository versions, DeploymentTargetContextService targets, DeploymentRuntimeAuthorityService authority,
        McpRuntimeConnectionResolver connections, ObjectMapper mapper) {
        this.deployments = deployments; this.releases = releases; this.versions = versions; this.targets = targets;
        this.authority = authority; this.connections = connections; this.mapper = mapper;
    }

    public JsonNode execute(String deploymentId, String targetId, String callerKey, JsonNode request) {
        if (callerKey == null || callerKey.isBlank() || request == null || !request.isObject()) throw denied();
        var deployment = deployments.findById(deploymentId).orElseThrow(McpRuntimeExecutionService::denied);
        var release = releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, targetId).stream()
            .filter(row -> "ACTIVE".equals(row.getProvisioningStatus()) && ACTIVE_RELEASES.contains(row.getStatus()))
            .findFirst().orElseThrow(McpRuntimeExecutionService::denied);
        var version = versions.findById(release.getDeploymentVersionId()).orElseThrow(McpRuntimeExecutionService::denied);
        authority.authenticateMcpRuntime(deployment, version, release, callerKey);
        targets.resolveOwnedRead(deploymentId, targetId, release.getId(), deployment.getCustomerId(), deployment.getTenantId());

        String actionId = request.path("actionId").asText();
        if (actionId.isBlank() || actionId.length() > 256 || !request.path("params").isObject()) throw invalid("An exact action and argument object are required.");
        JsonNode root;
        try {
            root = mapper.readTree(version.getManifestJson()).path("actionsConfig");
            if (!root.isObject()) throw invalid("The applied action artifact is unavailable.");
        }
        catch (Exception malformed) { throw invalid("The applied action contract is unavailable."); }
        JsonNode selected = null;
        for (JsonNode candidate : root.path("actions")) {
            if (actionId.equals(candidate.path("name").asText())) {
                if (selected != null) throw invalid("The applied action identity is ambiguous.");
                selected = candidate;
            }
        }
        if (selected == null || !selected.path("enabled").asBoolean(true) || !"mcp-tool".equals(selected.path("adapterType").asText())
            || !selected.path("execution").path("mcp").path("connectionId").isTextual()) throw denied();
        JsonNode auth = request.path("trace").path("authContext");
        requireSubjectScope(deployment, auth, request.path("trace").path("authenticated").asBoolean(false), selected);
        ObjectNode params = request.path("params").deepCopy();
        resolveTrustedParameters(params, selected, auth, request.path("trace"));
        JsonNode mcp = selected.path("execution").path("mcp");
        var resolved = connections.resolve(required(mcp, "connectionId"), deploymentId, targetId,
            required(mcp, "toolName"), required(mcp, "toolSchemaHash"));
        if (!mcp.path("connectionRevision").isIntegralNumber() || resolved.server() == null
            || !resolved.server().path("connectionRevision").isIntegralNumber()
            || mcp.path("connectionRevision").longValue() != resolved.server().path("connectionRevision").longValue()) {
            throw invalid("This MCP connection changed. Review its tools and apply the updated draft before executing it.");
        }

        ObjectNode action = selected.deepCopy();
        action.putObject("mcpServers").set(required(mcp, "serverRef"), resolved.server());
        ObjectNode trace = mapper.createObjectNode();
        trace.set("authContext", auth.deepCopy());
        trace.put("authenticated", request.path("trace").path("authenticated").asBoolean(false));
        trace.put("userId", auth.path("subjectId").asText());
        for (String name : List.of("conversationId", "requestId", "sessionId")) {
            if (request.path("trace").path(name).isTextual()) trace.set(name, request.path("trace").get(name));
        }
        trace.set("actionConfig", action);
        if (!resolved.secretValues().isEmpty()) trace.set("mcpSecretValues", mapper.valueToTree(resolved.secretValues()));
        ObjectNode canonical = mapper.createObjectNode().put("actionId", actionId);
        canonical.set("params", params); canonical.set("trace", trace);
        if (request.path("idempotencyKey").isTextual() && request.path("idempotencyKey").asText().length() <= 128) {
            canonical.set("idempotencyKey", request.get("idempotencyKey"));
        }
        return connections.execute(canonical);
    }

    private void requireSubjectScope(DeploymentEntity deployment, JsonNode auth, boolean authenticated, JsonNode action) {
        if (!auth.isObject() || !deployment.getId().equals(auth.path("deploymentId").asText())
            || !Objects.equals(deployment.getCustomerId(), auth.path("customerId").asText())
            || !Objects.equals(deployment.getTenantId(), auth.path("tenantId").asText())
            || !auth.path("subjectId").isTextual() || auth.path("subjectId").asText().isBlank()) throw denied();
        String subjectType = auth.path("subjectType").asText();
        if (authenticated == "ANONYMOUS_SESSION".equals(subjectType)) throw denied();
        if (!authenticated && !action.path("anonymousAllowed").asBoolean(false)) throw denied();
        if (authenticated && !Set.of("END_USER", "TRUSTED_BACKEND", "INTERNAL_PLATFORM_USER", "SYSTEM_PROCESS").contains(subjectType)) throw denied();
        if (auth.path("expiresAt").isTextual()) {
            try { if (!Instant.parse(auth.path("expiresAt").textValue()).isAfter(Instant.now())) throw denied(); }
            catch (java.time.format.DateTimeParseException malformed) { throw denied(); }
        }
    }

    private void resolveTrustedParameters(ObjectNode params, JsonNode action, JsonNode auth, JsonNode trace) {
        for (JsonNode parameter : action.path("params")) {
            JsonNode resolver = parameter.path("resolveFrom");
            if (!"RUNTIME_CONTEXT".equals(resolver.path("source").asText())) continue;
            String field = resolver.path("field").asText(resolver.path("contextField").asText(parameter.path("name").asText()));
            JsonNode value = switch (field) {
                case "userId", "subjectId" -> auth.get("subjectId");
                case "customerId", "tenantId", "deploymentId", "issuer", "subjectType" -> auth.get(field);
                case "sessionId", "conversationId", "requestId" -> trace.get(field);
                default -> throw invalid("The configured trusted MCP argument needs a supported runtime-context field.");
            };
            if (value == null || value.isNull() || value.isTextual() && value.textValue().isBlank()) {
                throw new ResponseStatusException(FORBIDDEN, "The configured trusted action context is missing.");
            }
            params.set(required(parameter, "name"), value.deepCopy());
        }
    }

    private String required(JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) throw invalid("The applied MCP binding is incomplete.");
        return node.path(field).textValue();
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(FORBIDDEN, "MCP runtime or action access is not authorized."); }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(CONFLICT, message); }
}
