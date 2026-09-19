package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentAgenticExecutionRequest;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentSmartBrainTriggerRequest;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentBehaviorOperationsService {

    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(30);
    private static final String AGENTIC_BEHAVIOR = "AGENTIC_SPECIALIST_TEAM";
    private static final String SMART_BRAIN_BEHAVIOR = "SMART_BRAIN";
    private static final String AGENTIC_EXECUTE_SCOPE = "agentic:deployment-intelligence:execute";
    private static final String AGENTIC_READ_SCOPE = "agentic:deployment-intelligence:read";
    private static final String AGENTIC_CANCEL_SCOPE = "agentic:deployment-intelligence:cancel";
    private static final String SMART_BRAIN_TRIGGER_SCOPE = "smart-brain:trigger";
    private static final String SMART_BRAIN_READ_SCOPE = "smart-brain:read";
    private static final String SMART_BRAIN_CANCEL_SCOPE = "smart-brain:cancel";
    private static final String SMART_BRAIN_REPLAY_SCOPE = "smart-brain:replay";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformSecretService platformSecretService;
    private final RuntimePrivateAssertionSigningService assertionSigningService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentBehaviorOperationsService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository deploymentVersionRepository,
        DeploymentAccessService deploymentAccessService,
        PlatformSecretService platformSecretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService platformAuditService,
        ObjectMapper objectMapper
    ) {
        this(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentAccessService,
            platformSecretService,
            assertionSigningService,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(RUNTIME_TIMEOUT).build()
        );
    }

    DeploymentBehaviorOperationsService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository deploymentVersionRepository,
        DeploymentAccessService deploymentAccessService,
        PlatformSecretService platformSecretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService platformAuditService,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformSecretService = platformSecretService;
        this.assertionSigningService = assertionSigningService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public RuntimeOperationResponse submitAgentic(
        String deploymentId,
        SubmitDeploymentAgenticExecutionRequest request
    ) {
        OperationAccess access = requireAccess(deploymentId, AGENTIC_BEHAVIOR, true);
        String question = requireText(request == null ? null : request.question(), "question", 2_000);
        String idempotencyKey = idempotencyKey(request == null ? null : request.idempotencyKey(), "agentic-submit");
        JsonNode body = objectMapper.createObjectNode().put("question", question);
        RuntimeOperationResponse response = send(
            access,
            "POST",
            "/api/agentic/v1/executions",
            body,
            "application/json",
            idempotencyKey,
            List.of(AGENTIC_EXECUTE_SCOPE)
        );
        response = withIdempotencyKey(response, idempotencyKey);
        auditSuccess("DEPLOYMENT_AGENTIC_EXECUTION_SUBMITTED", access, response, Map.of("idempotencyKey", idempotencyKey));
        return response;
    }

    public RuntimeOperationResponse agenticStatus(String deploymentId, String executionId) {
        OperationAccess access = requireAccess(deploymentId, AGENTIC_BEHAVIOR, false);
        return send(
            access,
            "GET",
            "/api/agentic/v1/executions/" + encode(requireText(executionId, "executionId", 160)),
            null,
            null,
            null,
            List.of(AGENTIC_READ_SCOPE)
        );
    }

    public RuntimeOperationResponse cancelAgentic(String deploymentId, String executionId) {
        OperationAccess access = requireAccess(deploymentId, AGENTIC_BEHAVIOR, true);
        RuntimeOperationResponse response = send(
            access,
            "POST",
            "/api/agentic/v1/executions/" + encode(requireText(executionId, "executionId", 160)) + "/cancel",
            objectMapper.createObjectNode(),
            "application/json",
            null,
            List.of(AGENTIC_CANCEL_SCOPE)
        );
        auditSuccess("DEPLOYMENT_AGENTIC_EXECUTION_CANCELLED", access, response, Map.of("executionId", executionId));
        return response;
    }

    public RuntimeOperationResponse replayAgentic(
        String deploymentId,
        String executionId,
        SubmitDeploymentAgenticExecutionRequest request
    ) {
        OperationAccess access = requireAccess(deploymentId, AGENTIC_BEHAVIOR, true);
        String question = requireText(request == null ? null : request.question(), "question", 2_000);
        String idempotencyKey = idempotencyKey(request == null ? null : request.idempotencyKey(), "agentic-replay");
        RuntimeOperationResponse response = send(
            access,
            "POST",
            "/api/agentic/v1/executions/" + encode(requireText(executionId, "executionId", 160)) + "/replay",
            objectMapper.createObjectNode().put("question", question),
            "application/json",
            idempotencyKey,
            List.of(AGENTIC_EXECUTE_SCOPE)
        );
        response = withIdempotencyKey(response, idempotencyKey);
        auditSuccess("DEPLOYMENT_AGENTIC_EXECUTION_REPLAYED", access, response, Map.of("executionId", executionId));
        return response;
    }

    public RuntimeOperationResponse triggerSmartBrain(
        String deploymentId,
        String triggerCode,
        SubmitDeploymentSmartBrainTriggerRequest request
    ) {
        OperationAccess access = requireAccess(deploymentId, SMART_BRAIN_BEHAVIOR, true);
        if (request == null || request.cloudEvent() == null || !request.cloudEvent().isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "cloudEvent must be a structured JSON object.");
        }
        String idempotencyKey = idempotencyKey(request.idempotencyKey(), "smart-brain-trigger");
        RuntimeOperationResponse response = send(
            access,
            "POST",
            "/api/smart-brain/v1/triggers/" + encode(requireText(triggerCode, "triggerCode", 64)),
            request.cloudEvent(),
            "application/cloudevents+json",
            idempotencyKey,
            List.of(SMART_BRAIN_TRIGGER_SCOPE)
        );
        auditSuccess("DEPLOYMENT_SMART_BRAIN_TRIGGERED", access, response, Map.of(
            "triggerCode", triggerCode,
            "idempotencyKey", idempotencyKey
        ));
        return response;
    }

    public RuntimeOperationResponse smartBrainStatus(String deploymentId, String operationId) {
        OperationAccess access = requireAccess(deploymentId, SMART_BRAIN_BEHAVIOR, false);
        return send(
            access,
            "GET",
            "/api/smart-brain/v1/operations/" + encode(requireText(operationId, "operationId", 160)),
            null,
            null,
            null,
            List.of(SMART_BRAIN_READ_SCOPE)
        );
    }

    public RuntimeOperationResponse cancelSmartBrain(String deploymentId, String operationId) {
        return mutateSmartBrain(deploymentId, operationId, "cancel", SMART_BRAIN_CANCEL_SCOPE, "DEPLOYMENT_SMART_BRAIN_CANCELLED");
    }

    public RuntimeOperationResponse replaySmartBrain(String deploymentId, String operationId) {
        return mutateSmartBrain(deploymentId, operationId, "replay", SMART_BRAIN_REPLAY_SCOPE, "DEPLOYMENT_SMART_BRAIN_REPLAYED");
    }

    private RuntimeOperationResponse mutateSmartBrain(
        String deploymentId,
        String operationId,
        String operation,
        String scope,
        String auditAction
    ) {
        OperationAccess access = requireAccess(deploymentId, SMART_BRAIN_BEHAVIOR, true);
        String normalizedId = requireText(operationId, "operationId", 160);
        RuntimeOperationResponse response = send(
            access,
            "POST",
            "/api/smart-brain/v1/operations/" + encode(normalizedId) + "/" + operation,
            objectMapper.createObjectNode(),
            "application/json",
            null,
            List.of(scope)
        );
        auditSuccess(auditAction, access, response, Map.of("operationId", normalizedId));
        return response;
    }

    private OperationAccess requireAccess(String deploymentId, String requiredBehavior, boolean mutate) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        if (mutate) {
            deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        } else {
            deploymentAccessService.requireDeploymentAccess(deployment);
        }
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId);
        }
        if (!StringUtils.hasText(deployment.getActiveVersionId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Apply a deployment version before using behavior operations.");
        }
        DeploymentVersionEntity activeVersion = deploymentVersionRepository.findById(deployment.getActiveVersionId())
            .filter(version -> deployment.getId().equals(version.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "The active deployment version is unavailable."));
        JsonNode behavior = readJson(activeVersion.getBehaviorConfigJson());
        if (!requiredBehavior.equals(behavior.path("type").asText(""))) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "This operation requires an active " + requiredBehavior + " deployment."
            );
        }
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(BAD_REQUEST, "The deployment runtime URL is unavailable.");
        }
        if (!StringUtils.hasText(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            || !assertionSigningService.isConfigured()) {
            throw new ResponseStatusException(BAD_REQUEST, "Secure private-runtime operator access is not configured.");
        }
        return new OperationAccess(deployment, principal);
    }

    private RuntimeOperationResponse send(
        OperationAccess access,
        String method,
        String path,
        JsonNode body,
        String contentType,
        String idempotencyKey,
        List<String> scopes
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(runtimeUri(access.deployment().getRuntimeBaseUrl(), path))
                .timeout(RUNTIME_TIMEOUT)
                .header("Accept", "application/json");
            runtimeHeaders(access, scopes).forEach(builder::header);
            if (StringUtils.hasText(idempotencyKey)) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.header("Content-Type", contentType == null ? "application/json" : contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body)
                    ));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode payload = StringUtils.hasText(response.body())
                ? objectMapper.readTree(response.body())
                : objectMapper.createObjectNode();
            return new RuntimeOperationResponse(response.statusCode(), payload);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach deployment runtime: " + exception.getMessage(), exception);
        }
    }

    private Map<String, String> runtimeHeaders(OperationAccess access, List<String> scopes) {
        PlatformPrincipal principal = access.principal();
        DeploymentEntity deployment = access.deployment();
        String issuer = "platform-runtime:" + principal.authenticationMode().trim().toUpperCase();
        RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims claims =
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                principal.actorId(),
                "INTERNAL_PLATFORM_USER",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "TRUSTED_BACKEND",
                "platform-behavior-" + deployment.getId() + "-" + shortHash(principal.actorId()),
                deployment.getId(),
                deployment.getCustomerId(),
                deployment.getTenantId(),
                issuer,
                Instant.now().plus(Duration.ofMinutes(5)),
                List.of(deployment.getId()),
                scopes
            );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(
            RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER,
            platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME).trim()
        );
        headers.put(
            RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER,
            assertionSigningService.toAuthorizationHeaderValue(claims)
        );
        return headers;
    }

    private void auditSuccess(
        String action,
        OperationAccess access,
        RuntimeOperationResponse response,
        Map<String, String> details
    ) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>(details);
        values.put("actorId", access.principal().actorId());
        values.put("runtimeStatus", response.statusCode());
        platformAuditService.record(action, "DEPLOYMENT", access.deployment().getId(), values);
    }

    private URI runtimeUri(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
                throw new IllegalArgumentException();
            }
            String normalizedBasePath = base.getPath() == null ? "" : base.getPath();
            if (normalizedBasePath.endsWith("/")) {
                normalizedBasePath = normalizedBasePath.substring(0, normalizedBasePath.length() - 1);
            }
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            return new URI(
                base.getScheme(),
                base.getUserInfo(),
                base.getHost(),
                base.getPort(),
                normalizedBasePath + normalizedPath,
                null,
                null
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid deployment runtime URL.");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "The active behavior configuration is invalid.");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                field + " is required and must not exceed " + maxLength + " characters."
            );
        }
        return normalized;
    }

    private String idempotencyKey(String value, String prefix) {
        if (StringUtils.hasText(value)) {
            return requireText(value, "idempotencyKey", 160);
        }
        return prefix + "-" + UUID.randomUUID();
    }

    private RuntimeOperationResponse withIdempotencyKey(RuntimeOperationResponse response, String idempotencyKey) {
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !response.body().isObject()) {
            return response;
        }
        com.fasterxml.jackson.databind.node.ObjectNode body = response.body().deepCopy();
        body.put("idempotencyKey", idempotencyKey);
        return new RuntimeOperationResponse(response.statusCode(), body);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String shortHash(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 12);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive runtime operator session id.", exception);
        }
    }

    private record OperationAccess(DeploymentEntity deployment, PlatformPrincipal principal) {
    }

    public record RuntimeOperationResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
