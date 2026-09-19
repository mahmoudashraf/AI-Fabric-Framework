package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentHumanReviewDecisionRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentHumanReviewService {

    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(15);
    private static final String REVIEW_EXTENSION = "HUMAN_REVIEW";
    private static final String SCOPE_REVIEW_TASK_VIEW = "review:tasks:view";
    private static final String SCOPE_REVIEW_TASK_DECIDE = "review:tasks:decide";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final PlatformCustomerAccessService platformCustomerAccessService;
    private final PlatformSecretService platformSecretService;
    private final RuntimePrivateAssertionSigningService assertionSigningService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentHumanReviewService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository deploymentVersionRepository,
        PlatformCustomerAccessService platformCustomerAccessService,
        PlatformSecretService platformSecretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService platformAuditService,
        ObjectMapper objectMapper
    ) {
        this(
            deploymentRepository,
            deploymentVersionRepository,
            platformCustomerAccessService,
            platformSecretService,
            assertionSigningService,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(RUNTIME_TIMEOUT).build()
        );
    }

    DeploymentHumanReviewService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository deploymentVersionRepository,
        PlatformCustomerAccessService platformCustomerAccessService,
        PlatformSecretService platformSecretService,
        RuntimePrivateAssertionSigningService assertionSigningService,
        PlatformAuditService platformAuditService,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.platformCustomerAccessService = platformCustomerAccessService;
        this.platformSecretService = platformSecretService;
        this.assertionSigningService = assertionSigningService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public RuntimeReviewResponse inbox(String deploymentId, int limit) {
        ReviewAccess access = requireReviewAccess(deploymentId, false);
        return sendRuntimeJson(
            access.deployment(),
            "GET",
            "/api/reviews/v1/tasks?limit=" + Math.max(1, Math.min(limit, 200)),
            null,
            access.principal(),
            List.of(SCOPE_REVIEW_TASK_VIEW)
        );
    }

    public RuntimeReviewResponse detail(String deploymentId, String taskId) {
        ReviewAccess access = requireReviewAccess(deploymentId, false);
        return sendRuntimeJson(
            access.deployment(),
            "GET",
            "/api/reviews/v1/tasks/" + encodePathSegment(requireText(taskId, "taskId", 120)),
            null,
            access.principal(),
            List.of(SCOPE_REVIEW_TASK_VIEW)
        );
    }

    public RuntimeReviewResponse decide(
        String deploymentId,
        String taskId,
        SubmitDeploymentHumanReviewDecisionRequest request
    ) {
        ReviewAccess access = requireReviewAccess(deploymentId, true);
        if (request == null || request.expectedVersion() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "A non-negative expectedVersion is required.");
        }
        String decision = requireText(request.decision(), "decision", 20).toUpperCase(Locale.ROOT);
        if (!List.of("APPROVE", "REJECT").contains(decision)) {
            throw new ResponseStatusException(BAD_REQUEST, "decision must be APPROVE or REJECT.");
        }
        String decisionId = StringUtils.hasText(request.decisionId())
            ? requireText(request.decisionId(), "decisionId", 160)
            : "platform-review-" + UUID.randomUUID();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("decisionId", decisionId);
        body.put("decision", decision);
        body.put("expectedVersion", request.expectedVersion());

        RuntimeReviewResponse response = sendRuntimeJson(
            access.deployment(),
            "POST",
            "/api/reviews/v1/tasks/" + encodePathSegment(requireText(taskId, "taskId", 120)) + "/decisions",
            body,
            access.principal(),
            List.of(SCOPE_REVIEW_TASK_VIEW, SCOPE_REVIEW_TASK_DECIDE)
        );
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            platformAuditService.record(
                "DEPLOYMENT_HUMAN_REVIEW_DECIDED",
                "DEPLOYMENT",
                access.deployment().getId(),
                Map.of(
                    "taskId", taskId,
                    "decision", decision,
                    "decisionId", decisionId,
                    "reviewer", access.principal().actorId()
                )
            );
        }
        return response;
    }

    private ReviewAccess requireReviewAccess(String deploymentId, boolean decision) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null
            || principal.role() != PlatformRole.CUSTOMER_ADMIN
            || !"SESSION".equalsIgnoreCase(principal.authenticationMode())) {
            throw new ResponseStatusException(
                FORBIDDEN,
                decision
                    ? "A session-authenticated customer administrator is required to decide customer reviews."
                    : "A session-authenticated customer administrator is required to view customer reviews."
            );
        }
        platformCustomerAccessService.requireDeploymentCustomerAccess(deployment.getCustomerId());
        requireHumanReviewEnabled(deployment);
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment runtime URL is not available. Apply the deployment before opening its review inbox."
            );
        }
        if (!StringUtils.hasText(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            || !assertionSigningService.isConfigured()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Secure private-runtime reviewer access is not configured."
            );
        }
        return new ReviewAccess(deployment, principal);
    }

    private void requireHumanReviewEnabled(DeploymentEntity deployment) {
        if (!StringUtils.hasText(deployment.getActiveVersionId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Human Review requires an applied deployment version.");
        }
        DeploymentVersionEntity version = deploymentVersionRepository.findById(deployment.getActiveVersionId())
            .filter(candidate -> deployment.getId().equals(candidate.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "The active deployment version is unavailable."));
        JsonNode extensions = readJson(version.getBehaviorConfigJson()).path("executionExtensions");
        boolean enabled = extensions.isArray()
            && java.util.stream.StreamSupport.stream(extensions.spliterator(), false)
                .anyMatch(value -> REVIEW_EXTENSION.equalsIgnoreCase(value.asText("")));
        if (!enabled) {
            throw new ResponseStatusException(BAD_REQUEST, "Human Review is not enabled on the active deployment version.");
        }
    }

    private RuntimeReviewResponse sendRuntimeJson(
        DeploymentEntity deployment,
        String method,
        String pathWithQuery,
        JsonNode body,
        PlatformPrincipal principal,
        List<String> scopes
    ) {
        URI target = runtimeUri(deployment.getRuntimeBaseUrl(), pathWithQuery);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(RUNTIME_TIMEOUT)
                .header("Accept", "application/json");
            reviewerHeaders(deployment, principal, scopes).forEach(builder::header);
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body)
                    ));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode payload = StringUtils.hasText(response.body())
                ? objectMapper.readTree(response.body())
                : objectMapper.createObjectNode();
            return new RuntimeReviewResponse(response.statusCode(), payload);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach deployment review runtime: " + ex.getMessage(), ex);
        }
    }

    private Map<String, String> reviewerHeaders(
        DeploymentEntity deployment,
        PlatformPrincipal principal,
        List<String> scopes
    ) {
        String trustedBackendApiKey = platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME);
        RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims claims =
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                principal.actorId(),
                "END_USER",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "TRUSTED_BACKEND",
                "customer-review-" + deployment.getId() + "-" + shortHash(principal.actorId()),
                deployment.getId(),
                deployment.getCustomerId(),
                deployment.getTenantId(),
                "platform-runtime:" + principal.authenticationMode().trim().toUpperCase(Locale.ROOT),
                Instant.now().plus(Duration.ofMinutes(5)),
                List.of(deployment.getId()),
                scopes
            );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, trustedBackendApiKey.trim());
        headers.put(
            RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER,
            assertionSigningService.toAuthorizationHeaderValue(claims)
        );
        return Map.copyOf(headers);
    }

    private URI runtimeUri(String runtimeBaseUrl, String pathWithQuery) {
        try {
            URI base = URI.create(runtimeBaseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
                throw new IllegalArgumentException("Runtime base URL must be absolute.");
            }
            String suffix = pathWithQuery.startsWith("/") ? pathWithQuery : "/" + pathWithQuery;
            int queryIndex = suffix.indexOf('?');
            String path = queryIndex >= 0 ? suffix.substring(0, queryIndex) : suffix;
            String query = queryIndex >= 0 ? suffix.substring(queryIndex + 1) : null;
            String basePath = base.getPath() == null ? "" : base.getPath();
            String normalizedPath = (basePath.endsWith("/")
                ? basePath.substring(0, basePath.length() - 1)
                : basePath) + path;
            return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), normalizedPath, query, null);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid runtime base URL: " + runtimeBaseUrl);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return StringUtils.hasText(value) ? objectMapper.readTree(value) : objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "The active behavior configuration is invalid.");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new ResponseStatusException(BAD_REQUEST, field + " is required and must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String shortHash(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 12);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive reviewer session id.", ex);
        }
    }

    private record ReviewAccess(DeploymentEntity deployment, PlatformPrincipal principal) {
    }

    public record RuntimeReviewResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
