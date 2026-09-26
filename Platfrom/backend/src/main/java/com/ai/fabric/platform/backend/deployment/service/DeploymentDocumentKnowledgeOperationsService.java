package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentDocumentKnowledgeOperationsService {

    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_RUNTIME_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final String READ_SCOPE = "documents:read";
    private static final String REGISTER_SCOPE = "documents:register";
    private static final String INDEX_SCOPE = "documents:index";
    private static final String DELETE_SCOPE = "documents:delete-index";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentAccessService accessService;
    private final PlatformSecretService secretService;
    private final RuntimePrivateAssertionSigningService assertionSigningService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentDocumentKnowledgeOperationsService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository versionRepository,
        DeploymentAccessService accessService,
        PlatformSecretService secretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this(
            deploymentRepository,
            versionRepository,
            accessService,
            secretService,
            assertionSigningService,
            auditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(RUNTIME_TIMEOUT).build()
        );
    }

    DeploymentDocumentKnowledgeOperationsService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository versionRepository,
        DeploymentAccessService accessService,
        PlatformSecretService secretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.accessService = accessService;
        this.secretService = secretService;
        this.assertionSigningService = assertionSigningService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public RuntimeResponse connectorStatus(String deploymentId) {
        return send(requireAccess(deploymentId, false), "GET", "/api/documents/source-connector/status", null, null, READ_SCOPE);
    }

    public RuntimeResponse discover(String deploymentId, JsonNode body) {
        JsonNode safe = boundedBody(body, Set.of("datasetId", "cursor", "limit"));
        return send(requireAccess(deploymentId, false), "POST", "/api/documents/sources/discover", safe, null, READ_SCOPE);
    }

    public RuntimeResponse list(String deploymentId) {
        return send(requireAccess(deploymentId, false), "GET", "/api/documents/sources", null, null, READ_SCOPE);
    }

    public RuntimeResponse detail(String deploymentId, String sourceId) {
        return send(requireAccess(deploymentId, false), "GET", sourcePath(sourceId), null, null, READ_SCOPE);
    }

    public RuntimeResponse preview(String deploymentId, String sourceId) {
        return send(requireAccess(deploymentId, false), "GET", sourcePath(sourceId) + "/preview", null, null, READ_SCOPE);
    }

    public RuntimeResponse retrievalProof(String deploymentId, JsonNode body) {
        JsonNode safe = boundedBody(body, Set.of("query", "limit"));
        return send(requireAccess(deploymentId, false), "POST", "/api/documents/retrieval-proof", safe, null, READ_SCOPE);
    }

    public RuntimeResponse retentionStatus(String deploymentId) {
        return send(requireAccess(deploymentId, false), "GET", "/api/documents/retention", null, null, READ_SCOPE);
    }

    public RuntimeResponse cleanupRetention(String deploymentId) {
        OperationAccess access = requireAccess(deploymentId, true);
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

    public RuntimeResponse register(String deploymentId, JsonNode body, String idempotencyKey) {
        OperationAccess access = requireAccess(deploymentId, true);
        JsonNode safe = boundedBody(body, Set.of("datasetId", "objectReference", "visibility", "metadata"));
        String key = idempotencyKey(idempotencyKey, "document-register");
        RuntimeResponse response = send(access, "POST", "/api/documents/sources", safe, key, REGISTER_SCOPE);
        auditSuccess("DOCUMENT_SOURCE_REGISTERED", access, response, Map.of("idempotencyKey", key));
        return response;
    }

    public RuntimeResponse refresh(String deploymentId, String sourceId, String idempotencyKey) {
        return command(deploymentId, sourceId, "refresh", idempotencyKey, INDEX_SCOPE, "DOCUMENT_SOURCE_REFRESH_REQUESTED");
    }

    public RuntimeResponse index(String deploymentId, String sourceId, String idempotencyKey) {
        return command(deploymentId, sourceId, "index", idempotencyKey, INDEX_SCOPE, "DOCUMENT_SOURCE_INDEX_REQUESTED");
    }

    public RuntimeResponse reconcile(String deploymentId, String sourceId, String idempotencyKey) {
        return command(deploymentId, sourceId, "reconcile", idempotencyKey, INDEX_SCOPE, "DOCUMENT_SOURCE_RECONCILED");
    }

    public RuntimeResponse removeIndex(String deploymentId, String sourceId, String idempotencyKey) {
        OperationAccess access = requireAccess(deploymentId, true);
        String key = idempotencyKey(idempotencyKey, "document-delete-index");
        RuntimeResponse response = send(access, "DELETE", sourcePath(sourceId), null, key, DELETE_SCOPE);
        auditSuccess("DOCUMENT_INDEX_REMOVAL_REQUESTED", access, response, Map.of(
            "sourceId", bounded(sourceId, 64),
            "idempotencyKey", key,
            "customerSourceObjectDeleted", false
        ));
        return response;
    }

    private RuntimeResponse command(String deploymentId,
                                    String sourceId,
                                    String operation,
                                    String idempotencyKey,
                                    String scope,
                                    String auditAction) {
        OperationAccess access = requireAccess(deploymentId, true);
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

    private OperationAccess requireAccess(String deploymentId, boolean mutate) {
        DeploymentEntity deployment = deploymentRepository.findById(required(deploymentId, "deploymentId", 64))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (mutate) {
            accessService.requireDeploymentOperatorAccess(deployment);
        } else {
            accessService.requireDeploymentAccess(deployment);
        }
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(NOT_FOUND, "Deployment not found.");
        }
        if (!StringUtils.hasText(deployment.getActiveVersionId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Apply a document-enabled deployment version first.");
        }
        DeploymentVersionEntity version = versionRepository.findById(deployment.getActiveVersionId())
            .filter(value -> deployment.getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "The active deployment version is unavailable."));
        if (!claimsDocumentCapability(version)) {
            throw new ResponseStatusException(BAD_REQUEST, "The active deployment version does not claim Document Knowledge Operations.");
        }
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(BAD_REQUEST, "The deployment runtime URL is unavailable.");
        }
        if (!StringUtils.hasText(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            || !assertionSigningService.isConfigured()) {
            throw new ResponseStatusException(BAD_REQUEST, "Secure private-runtime document administration is not configured.");
        }
        return new OperationAccess(deployment, principal);
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
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(runtimeUri(access.deployment().getRuntimeBaseUrl(), path))
                .timeout(RUNTIME_TIMEOUT)
                .header("Accept", "application/json");
            runtimeHeaders(access, List.of(scope)).forEach(builder::header);
            if (StringUtils.hasText(idempotencyKey)) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else {
                builder.header("Content-Type", "application/json").method(
                    method,
                    HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        body == null ? objectMapper.createObjectNode() : body
                    ))
                );
            }
            HttpResponse<InputStream> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
            );
            byte[] responseBytes;
            try (InputStream input = response.body()) {
                responseBytes = input.readNBytes(MAX_RUNTIME_RESPONSE_BYTES + 1);
            }
            if (responseBytes.length > MAX_RUNTIME_RESPONSE_BYTES) {
                throw new ResponseStatusException(BAD_GATEWAY, "Deployment document runtime response exceeded the Platform boundary.");
            }
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            JsonNode payload = StringUtils.hasText(responseBody)
                ? objectMapper.readTree(responseBody)
                : objectMapper.createObjectNode();
            return new RuntimeResponse(response.statusCode(), payload);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach the deployment document runtime.", exception);
        }
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
                "platform-document-operations",
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
        auditService.record(action, "DEPLOYMENT", access.deployment().getId(), safe);
    }

    private String sourcePath(String sourceId) {
        return "/api/documents/sources/" + encode(required(sourceId, "sourceId", 64));
    }

    private URI runtimeUri(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl.trim());
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || !StringUtils.hasText(base.getHost())
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String basePath = base.getRawPath() == null ? "" : base.getRawPath();
            if (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            return URI.create(base.getScheme() + "://" + base.getRawAuthority() + basePath + normalizedPath);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid deployment runtime URL.");
        }
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

    private record OperationAccess(DeploymentEntity deployment, PlatformPrincipal principal) {
    }

    public record RuntimeResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
