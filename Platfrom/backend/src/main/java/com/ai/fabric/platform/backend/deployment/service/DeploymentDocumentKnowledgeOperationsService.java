package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.operations.CoreSourceAccessService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentDocumentKnowledgeOperationsService {

    private static final String READ_SCOPE = "documents:read";
    private static final String REGISTER_SCOPE = "documents:register";
    private static final String INDEX_SCOPE = "documents:index";
    private static final String DELETE_SCOPE = "documents:delete-index";

    private final DeploymentTargetContextService targetContextService;
    private final DeploymentRuntimeAuthorityService runtimeAuthority;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final DeploymentRuntimeJsonTransport transport;
    private final CustomerSelfServiceOperationBoundary offerBoundary;
    private final CoreSourceAccessService sourceAccess;

    @Autowired
    public DeploymentDocumentKnowledgeOperationsService(
        DeploymentTargetContextService targetContextService,
        DeploymentRuntimeAuthorityService runtimeAuthority,
        PlatformAuditService auditService,
        ObjectMapper objectMapper,
        DeploymentRuntimeJsonTransport transport,
        CustomerSelfServiceOperationBoundary offerBoundary,
        CoreSourceAccessService sourceAccess
    ) {
        this.targetContextService = targetContextService;
        this.runtimeAuthority = runtimeAuthority;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.offerBoundary = offerBoundary;
        this.sourceAccess = sourceAccess;
    }

    DeploymentDocumentKnowledgeOperationsService(
        DeploymentTargetContextService targetContextService,
        DeploymentRuntimeAuthorityService runtimeAuthority,
        PlatformAuditService auditService,
        ObjectMapper objectMapper,
        HttpClient httpClient,
        CustomerSelfServiceOperationBoundary offerBoundary,
        CoreSourceAccessService sourceAccess
    ) {
        this.targetContextService = targetContextService;
        this.runtimeAuthority = runtimeAuthority;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transport = new DeploymentRuntimeJsonTransport(objectMapper, httpClient);
        this.offerBoundary = offerBoundary;
        this.sourceAccess = sourceAccess;
    }

    public RuntimeResponse connectorStatus(DeploymentTargetReference target) {
        return send(requireAccess(target, false, Operation.READ, null), "GET", "/api/documents/source-connector/status", null, null, READ_SCOPE);
    }

    public RuntimeResponse discover(DeploymentTargetReference target, JsonNode body) {
        JsonNode safe = boundedBody(body, Set.of("datasetId", "cursor", "limit"));
        return send(requireAccess(target, false, Operation.SOURCE_DISCOVER, null), "POST", "/api/documents/sources/discover", safe, null, READ_SCOPE);
    }

    public RuntimeResponse list(DeploymentTargetReference target) {
        return send(requireAccess(target, false, Operation.READ, null), "GET", "/api/documents/sources", null, null, READ_SCOPE);
    }

    public RuntimeResponse detail(DeploymentTargetReference target, String sourceId) {
        return send(requireAccess(target, false, Operation.READ, null), "GET", sourcePath(sourceId), null, null, READ_SCOPE);
    }

    public RuntimeResponse preview(DeploymentTargetReference target, String sourceId) {
        return send(requireAccess(target, false, Operation.SOURCE_PREVIEW, null), "GET", sourcePath(sourceId) + "/preview", null, null, READ_SCOPE);
    }

    public RuntimeResponse retrievalProof(DeploymentTargetReference target, JsonNode body) {
        JsonNode safe = boundedBody(body, Set.of("query", "limit"));
        return send(requireAccess(target, false, Operation.SOURCE_PREVIEW, null), "POST", "/api/documents/retrieval-proof", safe, null, READ_SCOPE);
    }

    public RuntimeResponse retentionStatus(DeploymentTargetReference target) {
        return send(requireAccess(target, false, Operation.READ, null), "GET", "/api/documents/retention", null, null, READ_SCOPE);
    }

    public RuntimeResponse cleanupRetention(DeploymentTargetReference target, String operationKey) {
        String key = DeploymentOperationIdempotencyKey.require(operationKey);
        OperationAccess access = requireAccess(target, true, Operation.SOURCE_REMOVE, key);
        RuntimeResponse response = send(
            access,
            "POST",
            "/api/documents/retention/cleanup",
            objectMapper.createObjectNode(),
            key,
            INDEX_SCOPE
        );
        auditSuccess("DOCUMENT_RETENTION_CLEANUP_COMPLETED", access, response, Map.of(
            "customerSourceObjectsDeleted", false
        ));
        return response;
    }

    public RuntimeResponse register(DeploymentTargetReference target, JsonNode body, String idempotencyKey) {
        String key = DeploymentOperationIdempotencyKey.require(idempotencyKey);
        JsonNode safe = boundedBody(body, Set.of("datasetId", "objectReference", "visibility", "metadata"));
        JsonNode visibility = safe.path("visibility");
        if (!visibility.isMissingNode() && !visibility.isNull()
            && (!visibility.isTextual() || !visibility.asText().isBlank()
                && !Set.of("internal", "private", "tenant").contains(visibility.asText().trim().toLowerCase(Locale.ROOT)))) {
            throw new ResponseStatusException(BAD_REQUEST, "visibility is not allowed by the document policy.");
        }
        OperationAccess access = requireAccess(target, true, Operation.SOURCE_INDEX, key);
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
        String key = DeploymentOperationIdempotencyKey.require(idempotencyKey);
        OperationAccess access = requireAccess(target, true, Operation.SOURCE_REMOVE, key);
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
        String key = DeploymentOperationIdempotencyKey.require(idempotencyKey);
        OperationAccess access = requireAccess(target, true,
            "refresh".equals(operation) ? Operation.SOURCE_REFRESH : Operation.SOURCE_INDEX, key);
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

    private OperationAccess requireAccess(DeploymentTargetReference target, boolean mutate, Operation operation, String operationKey) {
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
        java.util.Set<DocumentAudience> audiences = new java.util.HashSet<>();
        java.util.Set<String> documentDatasets = new java.util.HashSet<>();
        for (JsonNode dataset : readJson(version.getMarketplaceDatasetConfigJson()).path("datasets")) {
            if ("EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText())) {
                documentDatasets.add(dataset.path("datasetId").asText());
            }
        }
        for (JsonNode source : readJson(version.getKnowledgeSourceConfigJson()).path("sources")) {
            if (!documentDatasets.contains(source.path("datasetRef").asText())) continue;
            JsonNode modes = source.path("authModes");
            if (!modes.isArray() || modes.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Document source authModes must be a nonempty array.");
            }
            for (JsonNode mode : modes) {
                switch (mode.asText("")) {
                    case "PUBLIC_RUNTIME_ANONYMOUS" -> audiences.add(DocumentAudience.PUBLIC);
                    case "PUBLIC_RUNTIME_AUTHENTICATED", "PRIVATE_RUNTIME_BACKEND_MEDIATED" ->
                        audiences.add(DocumentAudience.AUTHENTICATED);
                    default -> throw new ResponseStatusException(BAD_REQUEST, "Document source authModes contains an unsupported mode.");
                }
            }
        }
        if (audiences.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Document source audience evidence is required.");
        }
        Set<DocumentAudience> reviewed = offerBoundary.reviewedDocumentAudiences(deployment);
        if (!reviewed.isEmpty()) {
            // Transport access does not grant additional document audiences beyond the reviewed offer.
            audiences.retainAll(reviewed);
            if (audiences.isEmpty()) {
                throw com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny(
                    com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.SETTING_FORBIDDEN);
            }
        }
        if (switch (operation) {
            case SOURCE_DISCOVER, SOURCE_PREVIEW, SOURCE_INDEX, SOURCE_REFRESH, SOURCE_REMOVE -> true;
            default -> false;
        }) {
            sourceAccess.require(target, operation, operationKey, Set.copyOf(audiences));
        }
        for (DocumentAudience audience : audiences) {
            if (!mutate && operation == Operation.READ) {
                offerBoundary.requireOperationalRead(deployment, target.targetProfileId(), target.releaseId(), audience);
                continue;
            }
            Map<Setting, JsonNode> settings = operation == Operation.SOURCE_INDEX || operation == Operation.SOURCE_REFRESH
                ? Map.of(Setting.DOCUMENT_AUDIENCE, objectMapper.getNodeFactory().textNode(audience.name())) : Map.of();
            offerBoundary.require(deployment, target.targetProfileId(), target.releaseId(), operation,
                operationKey, settings, null, null, audience);
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
        DeploymentTargetContextService.Context context = access.context();
        return runtimeAuthority.issuePlatformProxyHeaders(
            access.deployment().getId(),
            context.target().getId(),
            context.release().getId(),
            context.release().getRuntimeAuthorityFingerprint(),
            scopes,
            Duration.ofMinutes(5)
        );
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

    private record OperationAccess(DeploymentTargetContextService.Context context, PlatformPrincipal principal) {
        DeploymentEntity deployment() { return context.deployment(); }
    }

    public record RuntimeResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
