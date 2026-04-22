package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentWebhookOperationsService {

    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(15);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeploymentWebhookOperationsService(DeploymentRepository deploymentRepository,
                                              DeploymentAccessService deploymentAccessService,
                                              PlatformSecretService platformSecretService,
                                              PlatformAuditService platformAuditService,
                                              ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public JsonNode getOverview(String deploymentId, int limit) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment runtime URL is not available. Apply the deployment before inspecting webhook deliveries.");
        }

        Map<String, String> headers = RuntimePrivateAccessSupport.issuePlatformProxyHeaders(
            platformSecretService,
            objectMapper,
            deployment,
            "webhook-admin-overview",
            java.util.List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_WEBHOOKS_OVERVIEW),
            Duration.ofMinutes(10)
        );
        if (headers.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Secure private-runtime administration is not configured for webhook operations.");
        }
        return sendRuntimeJson(
            deployment.getRuntimeBaseUrl(),
            "GET",
            "/api/admin/webhooks/overview?limit=" + Math.max(1, Math.min(limit, 200)),
            headers
        );
    }

    public JsonNode retryDelivery(String deploymentId, String deliveryId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment runtime URL is not available. Apply the deployment before retrying webhook deliveries.");
        }

        Map<String, String> headers = RuntimePrivateAccessSupport.issuePlatformProxyHeaders(
            platformSecretService,
            objectMapper,
            deployment,
            "webhook-admin-retry",
            java.util.List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_WEBHOOKS_RETRY),
            Duration.ofMinutes(10)
        );
        if (headers.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Secure private-runtime administration is not configured for webhook operations.");
        }

        JsonNode response = sendRuntimeJson(
            deployment.getRuntimeBaseUrl(),
            "POST",
            "/api/admin/webhooks/deliveries/" + encodePathSegment(deliveryId) + "/retry",
            headers
        );
        platformAuditService.record(
            "DEPLOYMENT_WEBHOOK_DELIVERY_RETRY_TRIGGERED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("deliveryId", deliveryId)
        );
        return response;
    }

    private JsonNode sendRuntimeJson(String runtimeBaseUrl,
                                     String method,
                                     String pathWithQuery,
                                     Map<String, String> headers) {
        URI target = runtimeUri(runtimeBaseUrl, pathWithQuery);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(RUNTIME_TIMEOUT)
                .header("Accept", "application/json");
            if (headers != null) {
                headers.forEach(builder::header);
            }
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString("{}"));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY, "Runtime webhook admin request failed with HTTP " + response.statusCode() + ".");
            }
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach deployment runtime: " + ex.getMessage(), ex);
        }
    }

    private URI runtimeUri(String runtimeBaseUrl, String pathWithQuery) {
        try {
            URI base = URI.create(runtimeBaseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
                throw new IllegalArgumentException("Runtime base URL must be absolute.");
            }
            String basePath = base.getPath() == null ? "" : base.getPath();
            String suffix = pathWithQuery.startsWith("/") ? pathWithQuery : "/" + pathWithQuery;
            String query = null;
            String path = suffix;
            int queryIndex = suffix.indexOf('?');
            if (queryIndex >= 0) {
                path = suffix.substring(0, queryIndex);
                query = suffix.substring(queryIndex + 1);
            }
            String normalizedPath = (basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath) + path;
            return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), normalizedPath, query, null);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid runtime base URL: " + runtimeBaseUrl);
        }
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
    }

    private String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
