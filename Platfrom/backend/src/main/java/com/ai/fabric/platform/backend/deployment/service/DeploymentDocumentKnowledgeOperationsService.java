package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentDocumentKnowledgeOperationsService {

    private static final String READ_SCOPE = "documents:read";
    private static final String REGISTER_SCOPE = "documents:register";
    private static final String INDEX_SCOPE = "documents:index";
    private static final String DELETE_SCOPE = "documents:delete-index";

    private final DeploymentTargetContextService targetContextService;
    private final PlatformSecretService secretService;
    private final RuntimePrivateAssertionSigningService assertionSigningService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final DeploymentRuntimeJsonTransport transport;

    @Autowired
    public DeploymentDocumentKnowledgeOperationsService(
        DeploymentTargetContextService targetContextService,
        PlatformSecretService secretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper,
        DeploymentRuntimeJsonTransport transport
    ) {
        this.targetContextService = targetContextService;
        this.secretService = secretService;
        this.assertionSigningService = assertionSigningService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    DeploymentDocumentKnowledgeOperationsService(
        DeploymentTargetContextService targetContextService,
        PlatformSecretService secretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.targetContextService = targetContextService;
        this.secretService = secretService;
        this.assertionSigningService = assertionSigningService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transport = new DeploymentRuntimeJsonTransport(objectMapper, httpClient);
    }

    public RuntimeResponse connectorStatus(DeploymentTargetReference target) {
        return send(requireAccess(target, false), "GET", "/api/documents/source-connector/status", null, null, READ_SCOPE);
    }

    public RuntimeResponse discover(DeploymentTargetReference target, JsonNode body) {
        JsonNode safe = boundedBody(body, Set.of("datasetId", "cursor", "limit"));
        return send(requireAccess(target, false), "POST", "/api/documents/sources/discover", safe, null, READ_SCOPE);
    }

    public RuntimeResponse list(DeploymentTargetReference target) {
        return send(requireAccess(target, false), "GET", "/api/documents/sources", null, null, READ_SCOPE);
    }

    public RuntimeResponse detail(DeploymentTargetReference target, String sourceId) {
        return send(requireAccess(target, false), "GET", sourcePath(sourceId), null, null, READ_SCOPE);
    }

    public RuntimeResponse preview(DeploymentTargetReference target, String sourceId) {
        return send(requireAccess(target, false), "GET", sourcePath(sourceId) + "/preview", null, null, READ_SCOPE);
    }

    public RuntimeResponse retrievalProof(DeploymentTargetReference target, JsonNode body) {
        JsonNode safe = boundedBody(body, Set.of("query", "limit"));
        return send(requireAccess(target, false), "POST", "/api/documents/retrieval-proof", safe, null, READ_SCOPE);
    }

    public RuntimeResponse retentionStatus(DeploymentTargetReference target) {
        return send(requireAccess(target, false), "GET", "/api/documents/retention", null, null, READ_SCOPE);
    }

    public RuntimeResponse cleanupRetention(DeploymentTargetReference target) {
        OperationAccess access = requireAccess(target, true);
        RuntimeResponse response = send(
            access,
            "POST",
            "/api/documents/retention/cleanup",
            objectMapper.createObjectNode(),
            null,
            INDEX_SCOPE
        );
        auditSuccess("DOCUMENT_RETENTION_CLEANUP_COMPLETED", access, response, Map.of(
            "customerSourceObjectsDeleted", false
        ));
        return response;
    }

    public RuntimeResponse register(DeploymentTargetReference target, JsonNode body, String idempotencyKey) {
        OperationAccess access = requireAccess(target, true);
        JsonNode safe = boundedBody(body, Set.of("datasetId", "objectReference", "visibility", "metadata"));
        String key = idempotencyKey(idempotencyKey, "document-register");
        RuntimeResponse response = send(access, "POST", "/api/documents/sources", safe, key, REGISTER_SCOPE);
        auditSuccess("DOCUMENT_SOURCE_REGISTERED", access, response, Map.of("idempotencyKey", key));
        return response;
    }

    public RuntimeResponse refresh(DeploymentTargetReference target, String sourceId, String idempotencyKey) {
        return command(target, sourceId, "refresh", idempotencyKey, INDEX_SCOPE, "DOCUMENT_SOURCE_REFRESH_REQUESTED");
    }

    public RuntimeResponse index(DeploymentTargetReference target, String sourceId, String idempotencyKey) {
        return command(target, sourceId, "index", idempotencyKey, INDEX_SCOPE, "DOCUMENT_SOURCE_INDEX_REQUESTED");
    }

    public RuntimeResponse reconcile(DeploymentTargetReference target, String sourceId, String idempotencyKey) {
        return command(target, sourceId, "reconcile", idempotencyKey, INDEX_SCOPE, "DOCUMENT_SOURCE_RECONCILED");
    }

    public RuntimeResponse removeIndex(DeploymentTargetReference target, String sourceId, String idempotencyKey) {
        OperationAccess access = requireAccess(target, true);
        String key = idempotencyKey(idempotencyKey, "document-delete-index");
        RuntimeResponse response = send(access, "DELETE", sourcePath(sourceId), null, key, DELETE_SCOPE);
        auditSuccess("DOCUMENT_INDEX_REMOVAL_REQUESTED", access, response, Map.of(
            "sourceId", bounded(sourceId, 64),
            "idempotencyKey", key,
            "customerSourceObjectDeleted", false
        ));
        return response;
    }

    private RuntimeResponse command(DeploymentTargetReference target,
                                    String sourceId,
                                    String operation,
                                    String idempotencyKey,
                                    String scope,
                                    String auditAction) {
        OperationAccess access = requireAccess(target, true);
        String key = idempotencyKey(idempotencyKey, "document-" + operation);
        RuntimeResponse response = send(
            access,
            "POST",
            sourcePath(sourceId) + "/" + operation,
            objectMapper.createObjectNode(),
            key,
            scope
        );
        auditSuccess(auditAction, access, response, Map.of("sourceId", bounded(sourceId, 64), "idempotencyKey", key));
        return response;
    }

    private OperationAccess requireAccess(DeploymentTargetReference target, boolean mutate) {
        if (target == null) throw new ResponseStatusException(BAD_REQUEST, "Explicit target and release context is required.");
        DeploymentTargetContextService.Context context = targetContextService.resolve(
            target.deploymentId(), target.targetProfileId(), target.releaseId(), mutate
        );
        DeploymentEntity deployment = context.deployment();
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(NOT_FOUND, "Deployment not found.");
        }
        DeploymentVersionEntity version = context.version();
        if (!claimsDocumentCapability(version)) {
            throw new ResponseStatusException(BAD_REQUEST, "The selected release version does not claim Document Knowledge Operations.");
        }
        if (!StringUtils.hasText(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            || !assertionSigningService.isConfigured()) {
            throw new ResponseStatusException(BAD_REQUEST, "Secure private-runtime document administration is not configured.");
        }
        return new OperationAccess(context, principal);
    }

    private boolean claimsDocumentCapability(DeploymentVersionEntity version) {
        JsonNode config = readJson(version.getMarketplaceDatasetConfigJson());
        if (!config.path("datasets").isArray()) {
            return false;
        }
        for (JsonNode dataset : config.path("datasets")) {
            if ("EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private RuntimeResponse send(OperationAccess access,
                                 String method,
                                 String path,
                                 JsonNode body,
                                 String idempotencyKey,
                                 String scope) {
        var response = transport.send(
            access.context().runtimeBaseUrl(), method, path,
            runtimeHeaders(access, List.of(scope)), body, idempotencyKey
        );
        return new RuntimeResponse(response.statusCode(), response.body());
    }

    private Map<String, String> runtimeHeaders(OperationAccess access, List<String> scopes) {
        PlatformPrincipal principal = access.principal();
        DeploymentEntity deployment = access.deployment();
        RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims claims =
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                principal.actorId(),
                "INTERNAL_PLATFORM_USER",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "TRUSTED_BACKEND",
                "platform-documents-" + deployment.getId() + "-" + shortHash(principal.actorId()),
                deployment.getId(),
                deployment.getCustomerId(),
                deployment.getTenantId(),
                RuntimePrivateAccessSupport.ISSUER_PLATFORM_DOCUMENT_OPERATIONS,
                Instant.now().plus(Duration.ofMinutes(5)),
                List.of(deployment.getId()),
                scopes
            );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(
            RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER,
            secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME).trim()
        );
        headers.put(
            RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER,
            assertionSigningService.toAuthorizationHeaderValue(claims)
        );
        return headers;
    }

    private JsonNode boundedBody(JsonNode body, Set<String> allowedFields) {
        if (body == null || !body.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "A bounded JSON object is required.");
        }
        body.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported document operation field: " + field);
            }
        });
        if (body.toString().length() > 16_000) {
            throw new ResponseStatusException(BAD_REQUEST, "Document operation metadata exceeds the Platform boundary.");
        }
        return body.deepCopy();
    }

    private void auditSuccess(String action, OperationAccess access, RuntimeResponse response, Map<String, ?> details) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        details.forEach(safe::put);
        safe.put("actorId", access.principal().actorId());
        safe.put("runtimeStatus", response.statusCode());
        safe.put("targetProfileId", access.context().target().getId());
        safe.put("releaseId", access.context().release().getId());
        auditService.record(action, "DEPLOYMENT", access.deployment().getId(), safe);
    }

    private String sourcePath(String sourceId) {
        return "/api/documents/sources/" + encode(required(sourceId, "sourceId", 64));
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "The active document configuration is invalid.");
        }
    }

    private String idempotencyKey(String value, String prefix) {
        return StringUtils.hasText(value)
            ? required(value, "Idempotency-Key", 160)
            : prefix + "-" + UUID.randomUUID();
    }

    private String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new ResponseStatusException(BAD_REQUEST, field + " is required and must not exceed " + max + " characters.");
        }
        return normalized;
    }

    private String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String shortHash(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 12);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive document operator session id.", exception);
        }
    }

    private record OperationAccess(DeploymentTargetContextService.Context context, PlatformPrincipal principal) {
        DeploymentEntity deployment() { return context.deployment(); }
    }

    public record RuntimeResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
