package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentHumanReviewDecisionRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentHumanReviewService {

    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(15);
    private static final String REVIEW_EXTENSION = "HUMAN_REVIEW";
    private static final String SCOPE_REVIEW_TASK_VIEW = "review:tasks:view";
    private static final String SCOPE_REVIEW_TASK_DECIDE = "review:tasks:decide";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final PlatformCustomerAccessService platformCustomerAccessService;
    private final DeploymentRuntimeAuthorityService runtimeAuthorityService;
    private final DeploymentReleaseEndpointService endpointService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentHumanReviewService(
        DeploymentRepository deploymentRepository,
        DeploymentReleaseRepository releaseRepository,
        DeploymentVersionRepository deploymentVersionRepository,
        PlatformCustomerAccessService platformCustomerAccessService,
        DeploymentRuntimeAuthorityService runtimeAuthorityService,
        DeploymentReleaseEndpointService endpointService,
        PlatformAuditService platformAuditService,
        ObjectMapper objectMapper
    ) {
        this(
            deploymentRepository,
            releaseRepository,
            deploymentVersionRepository,
            platformCustomerAccessService,
            runtimeAuthorityService,
            endpointService,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(RUNTIME_TIMEOUT).build()
        );
    }

    DeploymentHumanReviewService(
        DeploymentRepository deploymentRepository,
        DeploymentReleaseRepository releaseRepository,
        DeploymentVersionRepository deploymentVersionRepository,
        PlatformCustomerAccessService platformCustomerAccessService,
        DeploymentRuntimeAuthorityService runtimeAuthorityService,
        DeploymentReleaseEndpointService endpointService,
        PlatformAuditService platformAuditService,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.platformCustomerAccessService = platformCustomerAccessService;
        this.runtimeAuthorityService = runtimeAuthorityService;
        this.endpointService = endpointService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public RuntimeReviewResponse inbox(String deploymentId, String targetProfileId, int limit) {
        ReviewAccess access = requireReviewAccess(deploymentId, targetProfileId, false);
        return sendRuntimeJson(
            access,
            "GET",
            "/api/reviews/v1/tasks?limit=" + Math.max(1, Math.min(limit, 200)),
            null,
            List.of(SCOPE_REVIEW_TASK_VIEW)
        );
    }

    public RuntimeReviewResponse detail(String deploymentId, String targetProfileId, String taskId) {
        ReviewAccess access = requireReviewAccess(deploymentId, targetProfileId, false);
        return sendRuntimeJson(
            access,
            "GET",
            "/api/reviews/v1/tasks/" + encodePathSegment(requireText(taskId, "taskId", 120)),
            null,
            List.of(SCOPE_REVIEW_TASK_VIEW)
        );
    }

    public RuntimeReviewResponse decide(
        String deploymentId,
        String targetProfileId,
        String taskId,
        SubmitDeploymentHumanReviewDecisionRequest request
    ) {
        ReviewAccess access = requireReviewAccess(deploymentId, targetProfileId, true);
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
            access,
            "POST",
            "/api/reviews/v1/tasks/" + encodePathSegment(requireText(taskId, "taskId", 120)) + "/decisions",
            body,
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
                    "reviewer", access.principal().actorId(),
                    "targetProfileId", access.release().getTargetProfileId(),
                    "releaseId", access.release().getId()
                )
            );
        }
        return response;
    }

    private ReviewAccess requireReviewAccess(String deploymentId, String targetProfileId, boolean decision) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (!com.ai.fabric.platform.backend.security.CustomerSessionSupport.isCustomerSession(principal)) {
            throw new ResponseStatusException(
                FORBIDDEN,
                decision
                    ? "A session-authenticated customer administrator is required to decide customer reviews."
                    : "A session-authenticated customer administrator is required to view customer reviews."
            );
        }
        platformCustomerAccessService.requireDeploymentCustomerAccess(deployment.getCustomerId());
        String target = requireText(targetProfileId, "targetProfileId", 120);
        // The selected target's serving release owns both the endpoint and reviewer authority.
        DeploymentReleaseEntity release = releaseRepository
            .findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, target).stream()
            .filter(candidate -> "ACTIVE".equals(candidate.getProvisioningStatus()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "The selected target has no active release."));
        if (!deploymentId.equals(release.getDeploymentId()) || !target.equals(release.getTargetProfileId())
            || !"APPLIED_VERIFIED".equals(release.getStatus()) || !"PASSED".equals(release.getVerificationStatus())) {
            throw new ResponseStatusException(CONFLICT, "Human Review requires the selected target's current verified release.");
        }
        DeploymentVersionEntity version = deploymentVersionRepository.findById(release.getDeploymentVersionId())
            .filter(candidate -> deploymentId.equals(candidate.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "The selected target's release version is unavailable."));
        requireHumanReviewEnabled(version);
        return new ReviewAccess(deployment, version, release, principal);
    }

    private void requireHumanReviewEnabled(DeploymentVersionEntity version) {
        JsonNode extensions = readJson(version.getBehaviorConfigJson()).path("executionExtensions");
        boolean enabled = extensions.isArray()
            && java.util.stream.StreamSupport.stream(extensions.spliterator(), false)
                .anyMatch(value -> REVIEW_EXTENSION.equalsIgnoreCase(value.asText("")));
        if (!enabled) {
            throw new ResponseStatusException(BAD_REQUEST, "Human Review is not enabled on the selected target's release version.");
        }
    }

    private RuntimeReviewResponse sendRuntimeJson(
        ReviewAccess access,
        String method,
        String pathWithQuery,
        JsonNode body,
        List<String> scopes
    ) {
        URI target = runtimeUri(endpointService.runtimeBaseUrl(access.release()), pathWithQuery);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(RUNTIME_TIMEOUT)
                .header("Accept", "application/json");
            runtimeAuthorityService.issueReviewedIntegrationSessionHeaders(
                access.deployment(), access.version(), access.release(), access.principal().actorId(), scopes
            ).forEach(builder::header);
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

    private record ReviewAccess(DeploymentEntity deployment, DeploymentVersionEntity version,
                                DeploymentReleaseEntity release, PlatformPrincipal principal) {
    }

    public record RuntimeReviewResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
