package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class VectorizationVerificationProbeService {

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    private static final String SYSTEM_TRACE_USER_ID = "system:platform-vectorization-verification";
    private static final String SYSTEM_TRACE_SESSION_ID = "system:tenant-isolation-smoke";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PlatformSecretService platformSecretService;

    public VectorizationVerificationProbeService(ObjectMapper objectMapper,
                                                 PlatformSecretService platformSecretService) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = objectMapper;
        this.platformSecretService = platformSecretService;
    }

    public JsonNode upsertSentinel(DeploymentEntity deployment,
                                   String entityType,
                                   String recordId,
                                   String content,
                                   Map<String, Object> metadata,
                                   String traceRequestId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("vectorSpace", entityType);
        body.put("id", recordId);
        body.put("content", content);
        body.set("metadata", objectMapper.valueToTree(metadata == null ? Map.of() : metadata));
        ObjectNode trace = body.putObject("trace");
        trace.put("requestId", traceRequestId);
        trace.set("authContext", buildVerifiedAuthContext(deployment));
        ObjectNode traceMetadata = trace.putObject("metadata");
        traceMetadata.put("deploymentId", deployment.getId());
        traceMetadata.put("verificationProbe", "TENANT_SHARED_ISOLATION_SMOKE");
        return operationalJson(deployment, "/api/ai/data-sync/upsert", body);
    }

    public JsonNode deleteSentinel(DeploymentEntity deployment,
                                   String entityType,
                                   String recordId,
                                   String traceRequestId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("vectorSpace", entityType);
        body.put("id", recordId);
        ObjectNode trace = body.putObject("trace");
        trace.put("requestId", traceRequestId);
        trace.set("authContext", buildVerifiedAuthContext(deployment));
        ObjectNode traceMetadata = trace.putObject("metadata");
        traceMetadata.put("deploymentId", deployment.getId());
        traceMetadata.put("verificationProbe", "TENANT_SHARED_ISOLATION_SMOKE");
        return operationalJson(deployment, "/api/ai/data-sync/delete", body);
    }

    public JsonNode fetchRuntimeVectors(DeploymentEntity deployment, String entityType, int limit, Integer offset) {
        String path = "/api/admin/indexing/vectors?entityType=" + encode(entityType.trim())
            + "&limit=" + Math.max(1, Math.min(limit, 500))
            + "&includeContent=true&includeMetadata=true";
        if (offset != null && offset >= 0) {
            path += "&offset=" + offset;
        }
        URI uri = runtimeUri(
            deployment,
            path
        );
        Map<String, String> runtimeHeaders = RuntimePrivateAccessSupport.issueSystemHeaders(
            platformSecretService,
            objectMapper,
            deployment,
            SYSTEM_TRACE_USER_ID,
            SYSTEM_TRACE_SESSION_ID,
            "platform-vectorization-verification",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_INDEXING_VECTORS),
            Duration.ofMinutes(10)
        );
        if (runtimeHeaders.isEmpty()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Secure private-runtime admin access is not configured for runtime vector inspection."
            );
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .GET();
        runtimeHeaders.forEach(requestBuilder::header);
        return send(
            requestBuilder.build(),
            "runtime vector inspection"
        );
    }

    private JsonNode operationalJson(DeploymentEntity deployment, String path, JsonNode body) {
        String runtimeTrustedBackendApiKey = trimToNull(platformSecretService.resolveSecret(RUNTIME_TRUSTED_BACKEND_SECRET_NAME));
        if (StringUtils.hasText(deployment.getRuntimeBaseUrl()) && StringUtils.hasText(runtimeTrustedBackendApiKey)) {
            HttpRequest request = HttpRequest.newBuilder(runtimeUri(deployment, path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER, runtimeTrustedBackendApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(write(body), StandardCharsets.UTF_8))
                .build();
            return send(request, "runtime trusted-backend data-sync verification");
        }

        throw new ResponseStatusException(
            BAD_REQUEST,
            "No runtime-backed operational verification surface is available. Configure runtime URL plus "
                + RUNTIME_TRUSTED_BACKEND_SECRET_NAME
                + "."
        );
    }

    private JsonNode send(HttpRequest request, String label) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY, label + " failed with HTTP " + response.statusCode() + ".");
            }
            return StringUtils.hasText(response.body()) ? objectMapper.readTree(response.body()) : objectMapper.createObjectNode();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach deployment endpoint for " + label + ": " + ex.getMessage(), ex);
        }
    }

    private URI runtimeUri(DeploymentEntity deployment, String path) {
        return resolveAbsoluteUri(trimToNull(deployment == null ? null : deployment.getRuntimeBaseUrl()), path, "runtime");
    }

    private URI resolveAbsoluteUri(String baseUrl, String path, String label) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment " + label + " base URL is not available.");
        }
        try {
            URI base = URI.create(baseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
                throw new IllegalArgumentException("Base URL must be absolute.");
            }
            return base.resolve(path);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid deployment " + label + " base URL: " + baseUrl);
        }
    }

    private String requireSecret(String name, String label) {
        String value = trimToNull(platformSecretService.resolveSecret(name));
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(BAD_REQUEST, name + " is not configured for " + label + ".");
        }
        return value;
    }

    private String write(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize verification probe request.", ex);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ObjectNode buildVerifiedAuthContext(DeploymentEntity deployment) {
        ObjectNode authContext = objectMapper.createObjectNode();
        authContext.put("subjectId", SYSTEM_TRACE_USER_ID);
        authContext.put("subjectType", "SYSTEM_PROCESS");
        authContext.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        authContext.put("callerType", "SYSTEM_PROCESS");
        authContext.put("sessionId", SYSTEM_TRACE_SESSION_ID);
        putIfText(authContext, "deploymentId", deployment == null ? null : deployment.getId());
        putIfText(authContext, "customerId", deployment == null ? null : deployment.getCustomerId());
        putIfText(authContext, "tenantId", deployment == null ? null : deployment.getTenantId());
        authContext.put("issuer", "platform-vectorization-verification");
        authContext.putArray("grantedScopes")
            .add("data-sync:upsert")
            .add("data-sync:delete")
            .add("vectorization:verification");
        return authContext;
    }

    private void putIfText(ObjectNode target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key.trim(), value.trim());
    }
}
