package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class VectorizationRuntimeCoverageClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PlatformSecretService platformSecretService;

    @Autowired
    public VectorizationRuntimeCoverageClient(ObjectMapper objectMapper,
                                              PlatformSecretService platformSecretService) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), objectMapper, platformSecretService);
    }

    VectorizationRuntimeCoverageClient(HttpClient httpClient,
                                       ObjectMapper objectMapper,
                                       PlatformSecretService platformSecretService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.platformSecretService = platformSecretService;
    }

    public JsonNode fetchCounts(DeploymentEntity deployment) {
        if (deployment == null || !StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            return objectMapper.createObjectNode();
        }
        if (deployment.getRuntimeBaseUrl().contains(".placeholder.local")) {
            return objectMapper.createObjectNode();
        }
        Map<String, String> runtimeHeaders = RuntimePrivateAccessSupport.issueSystemHeaders(
            platformSecretService,
            objectMapper,
            deployment,
            "platform-runtime-coverage",
            "runtime-coverage-" + deployment.getId(),
            "platform-runtime-coverage",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_INDEXING_OVERVIEW),
            Duration.ofMinutes(10)
        );
        if (runtimeHeaders.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            URI uri = URI.create(deployment.getRuntimeBaseUrl() + "/api/admin/indexing/overview");
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10));
            runtimeHeaders.forEach(builder::header);
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return objectMapper.createObjectNode();
            }
            JsonNode body = objectMapper.readTree(response.body());
            return body.path("countsByEntityType").isObject() ? body.path("countsByEntityType") : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
}
