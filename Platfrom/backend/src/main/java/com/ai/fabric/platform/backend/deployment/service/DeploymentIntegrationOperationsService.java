package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentIntegrationOperationsService {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentAccessService accessService;
    private final PlatformSecretService secretService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeploymentIntegrationOperationsService(
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository versionRepository,
        DeploymentAccessService accessService,
        PlatformSecretService secretService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.accessService = accessService;
        this.secretService = secretService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public RuntimeResponse overview(String deploymentId) {
        return send(requireAccess(deploymentId, false), "GET", "/api/admin/connector/integrations", false);
    }

    public RuntimeResponse source(String deploymentId, String sourceId) {
        return send(requireAccess(deploymentId, false), "GET", sourcePath(sourceId), false);
    }

    public RuntimeResponse reconcile(String deploymentId, String sourceId) {
        OperationAccess access = requireAccess(deploymentId, true);
        RuntimeResponse response = send(access, "POST", sourcePath(sourceId) + "/reconcile", true);
        auditSuccess("DEPLOYMENT_INTEGRATION_RECONCILE_REQUESTED", access.deployment(), response, Map.of(
            "sourceId", required(sourceId, "sourceId")
        ));
        return response;
    }

    public RuntimeResponse webhookEvents(String deploymentId, String sourceId) {
        String path = "/api/admin/connector/integrations/webhooks/" + encode(required(sourceId, "sourceId")) + "/events";
        return send(requireAccess(deploymentId, false), "GET", path, false);
    }

    public RuntimeResponse replayWebhook(String deploymentId, String sourceId, String eventId) {
        OperationAccess access = requireAccess(deploymentId, true);
        String normalizedSource = required(sourceId, "sourceId");
        String normalizedEvent = required(eventId, "eventId");
        String path = "/api/admin/connector/integrations/webhooks/" + encode(normalizedSource)
            + "/events/" + encode(normalizedEvent) + "/replay";
        RuntimeResponse response = send(access, "POST", path, true);
        auditSuccess("DEPLOYMENT_INTEGRATION_WEBHOOK_REPLAY_REQUESTED", access.deployment(), response, Map.of(
            "sourceId", normalizedSource,
            "eventId", normalizedEvent
        ));
        return response;
    }

    private OperationAccess requireAccess(String deploymentId, boolean mutate) {
        DeploymentEntity deployment = deploymentRepository.findById(required(deploymentId, "deploymentId"))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (mutate) {
            accessService.requireDeploymentOperatorAccess(deployment);
        } else {
            accessService.requireDeploymentAccess(deployment);
        }
        if (!StringUtils.hasText(deployment.getActiveVersionId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Apply an integration-enabled deployment version first.");
        }
        DeploymentVersionEntity version = versionRepository.findById(deployment.getActiveVersionId())
            .filter(candidate -> deployment.getId().equals(candidate.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "The active deployment version is unavailable."));
        if (!claimsExternalHttpIntegration(version)) {
            throw new ResponseStatusException(BAD_REQUEST, "The active deployment version does not claim an external HTTP integration.");
        }
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(BAD_REQUEST, "The deployment runtime URL is unavailable.");
        }
        if (!RuntimePrivateAccessSupport.isConfigured(secretService, objectMapper)) {
            throw new ResponseStatusException(BAD_REQUEST, "Secure private-runtime integration administration is not configured.");
        }
        return new OperationAccess(deployment);
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

    private RuntimeResponse send(OperationAccess access, String method, String path, boolean mutate) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(runtimeUri(access.deployment().getRuntimeBaseUrl(), path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json");
            List<String> scopes = mutate
                ? List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_WRITE)
                : List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_READ);
            RuntimePrivateAccessSupport.issuePlatformProxyHeaders(
                secretService,
                objectMapper,
                access.deployment(),
                RuntimePrivateAccessSupport.ISSUER_PLATFORM_INTEGRATION_OPERATIONS,
                scopes,
                Duration.ofMinutes(5)
            ).forEach(request::header);
            if ("POST".equals(method)) {
                request.header("Content-Type", "application/json")
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

    private void auditSuccess(String action, DeploymentEntity deployment, RuntimeResponse response, Map<String, ?> details) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        details.forEach(safe::put);
        safe.put("runtimeStatus", response.statusCode());
        auditService.record(action, "DEPLOYMENT", deployment.getId(), safe);
    }

    private record OperationAccess(DeploymentEntity deployment) {
    }

    public record RuntimeResponse(int statusCode, JsonNode body) {
        public HttpStatus status() {
            HttpStatus resolved = HttpStatus.resolve(statusCode);
            return resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
        }
    }
}
