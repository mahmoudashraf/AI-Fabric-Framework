package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingKind;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Setting;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.admission.CustomerSourcePrerequisiteRefreshService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentIntegrationOperationsService {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final DeploymentTargetContextService targetContexts;
    private final DeploymentRuntimeAuthorityService runtimeAuthority;
    private final CustomerSelfServiceOperationBoundary offerBoundary;
    private final CustomerSourcePrerequisiteRefreshService sourcePrerequisites;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeploymentIntegrationOperationsService(
        DeploymentTargetContextService targetContexts,
        DeploymentRuntimeAuthorityService runtimeAuthority,
        CustomerSelfServiceOperationBoundary offerBoundary,
        CustomerSourcePrerequisiteRefreshService sourcePrerequisites,
        PlatformAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.targetContexts = targetContexts;
        this.runtimeAuthority = runtimeAuthority;
        this.offerBoundary = offerBoundary;
        this.sourcePrerequisites = sourcePrerequisites;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public RuntimeResponse overview(DeploymentTargetReference target) {
        return send(requireAccess(target, false, Capability.EXTERNAL_HTTP, Operation.READ, null), "GET", "/api/admin/connector/integrations", false);
    }

    public RuntimeResponse source(DeploymentTargetReference target, String sourceId) {
        return send(requireAccess(target, false, Capability.EXTERNAL_HTTP, Operation.READ, null), "GET", sourcePath(sourceId), false);
    }

    public RuntimeResponse reconcile(DeploymentTargetReference target, String sourceId, String operationKey) {
        String key = DeploymentOperationIdempotencyKey.require(operationKey);
        String normalizedSource = required(sourceId, "sourceId");
        OperationAccess access = requireSourceAccess(target, normalizedSource, key);
        RuntimeResponse response = send(access, "POST", sourcePath(normalizedSource) + "/reconcile", true, key);
        auditSuccess("DEPLOYMENT_INTEGRATION_RECONCILE_REQUESTED", access, response, Map.of(
            "sourceId", normalizedSource
        ));
        return response;
    }

    public RuntimeResponse webhookEvents(DeploymentTargetReference target, String sourceId) {
        String path = "/api/admin/connector/integrations/webhooks/" + encode(required(sourceId, "sourceId")) + "/events";
        return send(requireAccess(target, false, Capability.EXTERNAL_HTTP, Operation.READ, null), "GET", path, false);
    }

    public RuntimeResponse replayWebhook(DeploymentTargetReference target, String sourceId, String eventId, String operationKey) {
        String key = DeploymentOperationIdempotencyKey.require(operationKey);
        String normalizedSource = required(sourceId, "sourceId");
        String normalizedEvent = required(eventId, "eventId");
        OperationAccess access = requireSourceAccess(target, normalizedSource, key);
        String path = "/api/admin/connector/integrations/webhooks/" + encode(normalizedSource)
            + "/events/" + encode(normalizedEvent) + "/replay";
        RuntimeResponse response = send(access, "POST", path, true, key);
        auditSuccess("DEPLOYMENT_INTEGRATION_WEBHOOK_REPLAY_REQUESTED", access, response, Map.of(
            "sourceId", normalizedSource,
            "eventId", normalizedEvent
        ));
        return response;
    }

    public RuntimeResponse conversationHandoverOverview(DeploymentTargetReference target) {
        OperationAccess access = requireAccess(target, false, Capability.CONVERSATION_HANDOVER, Operation.READ, null);
        RuntimeResponse runtime = send(
            access,
            "GET",
            "/api/admin/conversation-handover/overview",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_READ)
        );
        if (runtime.statusCode() < 200 || runtime.statusCode() >= 300) {
            return runtime;
        }
        RuntimeResponse connector = send(
            access,
            "GET",
            "/api/admin/connector/conversation-channel",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_READ)
        );
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", true);
        body.set("runtime", runtime.body());
        ObjectNode connectorProjection = body.putObject("connector");
        connectorProjection.put("reachable", connector.statusCode() >= 200 && connector.statusCode() < 300);
        connectorProjection.put("statusCode", connector.statusCode());
        if (connector.statusCode() >= 200 && connector.statusCode() < 300) {
            connectorProjection.set("status", connector.body());
        }
        String operatorConsoleUrl = operatorConsoleUrl(access.version());
        if (StringUtils.hasText(operatorConsoleUrl)) {
            body.put("operatorConsoleUrl", operatorConsoleUrl);
        }
        return new RuntimeResponse(200, body);
    }

    public RuntimeResponse retryConversationHandoverDispatch(DeploymentTargetReference target, String dispatchId,
                                                               String operationKey) {
        String key = DeploymentOperationIdempotencyKey.require(operationKey);
        OperationAccess access = requireAccess(target, true, Capability.CONVERSATION_HANDOVER,
            Operation.INBOX_OPERATOR_DELIVER, key);
        String normalizedDispatch = required(dispatchId, "dispatchId");
        RuntimeResponse response = send(
            access,
            "POST",
            "/api/admin/conversation-handover/dispatches/" + encode(normalizedDispatch) + "/retry",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_RECOVER), key
        );
        auditSuccess("CONVERSATION_HANDOVER_DISPATCH_RETRY_REQUESTED", access, response, Map.of(
            "dispatchId", normalizedDispatch
        ));
        return response;
    }

    public RuntimeResponse expireConversationHandover(DeploymentTargetReference target, String handoverId,
                                                       String operationKey) {
        String key = DeploymentOperationIdempotencyKey.require(operationKey);
        OperationAccess access = requireAccess(target, true, Capability.CONVERSATION_HANDOVER,
            Operation.INBOX_OPERATOR_DELIVER, key);
        String normalizedHandover = required(handoverId, "handoverId");
        RuntimeResponse response = send(
            access,
            "POST",
            "/api/admin/conversation-handover/handovers/" + encode(normalizedHandover) + "/expire",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_RECOVER), key
        );
        auditSuccess("CONVERSATION_HANDOVER_EXPIRE_REQUESTED", access, response, Map.of(
            "handoverId", normalizedHandover
        ));
        return response;
    }

    private OperationAccess requireAccess(DeploymentTargetReference target, boolean mutate, Capability capability,
                                          Operation operation, String operationKey) {
        if (target == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Explicit target and release context is required.");
        }
        DeploymentTargetContextService.Context context = targetContexts.resolve(
            target.deploymentId(), target.targetProfileId(), target.releaseId(), mutate
        );
        boolean claimed = capability == Capability.EXTERNAL_HTTP
            ? claimsExternalHttpIntegration(context.version())
            : claimsConversationHandover(context.version());
        if (!claimed) {
            String label = capability == Capability.EXTERNAL_HTTP
                ? "an external HTTP integration"
                : "Conversation Handover";
            throw new ResponseStatusException(BAD_REQUEST, "The selected release version does not claim " + label + ".");
        }
        if (!mutate && operation == Operation.READ) {
            offerBoundary.requireOperationalRead(context.deployment(), target.targetProfileId(), target.releaseId(), null);
        } else if (operation == Operation.INBOX_OPERATOR_DELIVER) {
            offerBoundary.requireBindingKind(context.deployment(), target.targetProfileId(), target.releaseId(),
                operation, operationKey, BindingKind.INBOX_OPERATOR);
        } else {
            offerBoundary.require(context.deployment(), target.targetProfileId(), target.releaseId(), operation,
                operationKey, Map.of(), null, null, null);
        }
        return new OperationAccess(context);
    }

    private OperationAccess requireSourceAccess(DeploymentTargetReference target, String sourceId, String operationKey) {
        if (target == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Explicit target and release context is required.");
        }
        DeploymentTargetContextService.Context context = targetContexts.resolve(
            target.deploymentId(), target.targetProfileId(), target.releaseId(), true
        );
        if (!claimsExternalHttpIntegration(context.version())) {
            throw new ResponseStatusException(BAD_REQUEST,
                "The selected release version does not claim an external HTTP integration.");
        }
        Set<DocumentAudience> audiences = configuredSourceAudiences(context.version(), sourceId);
        Set<DocumentAudience> reviewedAudiences = offerBoundary.reviewedDocumentAudiences(context.deployment());
        if (audiences.isEmpty()) {
            if (reviewedAudiences.size() == 1) {
                audiences = reviewedAudiences;
            } else if (!reviewedAudiences.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST,
                    "The selected integration source does not declare a unique reviewed document audience.");
            }
        }
        if (audiences.isEmpty()) {
            offerBoundary.require(context.deployment(), target.targetProfileId(), target.releaseId(),
                Operation.SOURCE_REFRESH, operationKey, Map.of(), null, null, null);
            return new OperationAccess(context);
        }
        sourcePrerequisites.ensureFresh(target, Operation.SOURCE_REFRESH, operationKey, audiences);
        for (DocumentAudience audience : audiences) {
            offerBoundary.require(context.deployment(), target.targetProfileId(), target.releaseId(),
                Operation.SOURCE_REFRESH, operationKey,
                Map.of(Setting.DOCUMENT_AUDIENCE, objectMapper.getNodeFactory().textNode(audience.name())),
                null, null, audience);
        }
        return new OperationAccess(context);
    }

    private Set<DocumentAudience> configuredSourceAudiences(DeploymentVersionEntity version, String sourceId) {
        Set<String> datasetIds = new LinkedHashSet<>();
        JsonNode datasets = readJson(version.getMarketplaceDatasetConfigJson()).path("datasets");
        if (datasets.isArray()) {
            for (JsonNode dataset : datasets) {
                if (!"EXTERNAL_SYNC_HTTP".equals(dataset.path("ingestionMode").asText(""))) continue;
                JsonNode connector = dataset.path("syncConnector");
                String httpSourceId = connector.path("httpSource").path("sourceId").asText("").trim();
                String webhookSourceId = connector.path("webhook").path("sourceId").asText("").trim();
                if (sourceId.equals(httpSourceId) || sourceId.equals(webhookSourceId)) {
                    String datasetId = dataset.path("datasetId").asText("").trim();
                    if (StringUtils.hasText(datasetId)) datasetIds.add(datasetId);
                }
            }
        }
        if (datasetIds.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST,
                "The selected integration source is not present in the immutable release configuration.");
        }
        Set<DocumentAudience> audiences = new LinkedHashSet<>();
        JsonNode sources = readJson(version.getKnowledgeSourceConfigJson()).path("sources");
        if (!sources.isArray()) return Set.of();
        for (JsonNode source : sources) {
            if (!datasetIds.contains(source.path("datasetRef").asText(""))) continue;
            for (JsonNode mode : source.path("authModes")) {
                String value = mode.asText("");
                if ("PUBLIC_RUNTIME_ANONYMOUS".equals(value)) audiences.add(DocumentAudience.PUBLIC);
                if ("PUBLIC_RUNTIME_AUTHENTICATED".equals(value)
                    || "PRIVATE_RUNTIME_BACKEND_MEDIATED".equals(value)) {
                    audiences.add(DocumentAudience.AUTHENTICATED);
                }
            }
        }
        return Set.copyOf(audiences);
    }

    private boolean claimsExternalHttpIntegration(DeploymentVersionEntity version) {
        JsonNode datasets = readJson(version.getMarketplaceDatasetConfigJson()).path("datasets");
        if (!datasets.isArray()) {
            return false;
        }
        for (JsonNode dataset : datasets) {
            if ("EXTERNAL_SYNC_HTTP".equals(dataset.path("ingestionMode").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean claimsConversationHandover(DeploymentVersionEntity version) {
        return readJson(version.getBehaviorConfigJson())
            .path("conversationHandover")
            .path("enabled")
            .asBoolean(false);
    }

    private RuntimeResponse send(OperationAccess access, String method, String path, boolean mutate) {
        return send(access, method, path, mutate, null);
    }

    private RuntimeResponse send(OperationAccess access, String method, String path, boolean mutate, String operationKey) {
        List<String> scopes = mutate
            ? List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_WRITE)
            : List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_READ);
        return send(access, method, path, scopes, operationKey);
    }

    private RuntimeResponse send(OperationAccess access, String method, String path, List<String> scopes) {
        return send(access, method, path, scopes, null);
    }

    private RuntimeResponse send(OperationAccess access, String method, String path, List<String> scopes, String operationKey) {
        String key = "POST".equals(method) ? DeploymentOperationIdempotencyKey.require(operationKey) : null;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(runtimeUri(access.context().runtimeBaseUrl(), path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json");
            runtimeAuthority.issuePlatformProxyHeaders(
                access.deployment().getId(),
                access.context().target().getId(),
                access.context().release().getId(),
                access.context().release().getRuntimeAuthorityFingerprint(),
                scopes,
                Duration.ofMinutes(5)
            ).forEach(request::header);
            if ("POST".equals(method)) {
                request.header("Idempotency-Key", key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));
            } else {
                request.GET();
            }
            HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new ResponseStatusException(BAD_GATEWAY, "Deployment integration response exceeded the Platform boundary.");
            }
            String body = new String(bytes, StandardCharsets.UTF_8);
            return new RuntimeResponse(
                response.statusCode(),
                StringUtils.hasText(body) ? objectMapper.readTree(body) : objectMapper.createObjectNode()
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach the deployment integration runtime.", exception);
        }
    }

    private URI runtimeUri(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl.trim());
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || !StringUtils.hasText(base.getHost()) || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String basePath = base.getRawPath() == null ? "" : base.getRawPath();
            if (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            return URI.create(base.getScheme() + "://" + base.getRawAuthority() + basePath + path);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid deployment runtime URL.");
        }
    }

    private String sourcePath(String sourceId) {
        return "/api/admin/connector/integrations/sources/" + encode(required(sourceId, "sourceId"));
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "The active integration configuration is invalid.");
        }
    }

    private String operatorConsoleUrl(DeploymentVersionEntity version) {
        String value = readJson(version.getBehaviorConfigJson())
            .path("conversationHandover")
            .path("channel")
            .path("operatorConsoleUrl")
            .asText("")
            .trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 160 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(BAD_REQUEST, field + " is invalid.");
        }
        return normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void auditSuccess(String action, OperationAccess access, RuntimeResponse response, Map<String, ?> details) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        details.forEach(safe::put);
        safe.put("runtimeStatus", response.statusCode());
        safe.put("targetProfileId", access.context().target().getId());
        safe.put("releaseId", access.context().release().getId());
        auditService.record(action, "DEPLOYMENT", access.deployment().getId(), safe);
    }

    private enum Capability {
        EXTERNAL_HTTP,
        CONVERSATION_HANDOVER
    }

    private record OperationAccess(DeploymentTargetContextService.Context context) {
        DeploymentEntity deployment() {
            return context.deployment();
        }

        DeploymentVersionEntity version() {
            return context.version();
        }
    }

    public record RuntimeResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            HttpStatus resolved = HttpStatus.resolve(statusCode);
            return resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
        }
    }
}
