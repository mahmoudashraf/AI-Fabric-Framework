package com.ai.fabric.realapps.chat.migration.client;

import jakarta.annotation.Nullable;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Connector → Runtime admin client for clearing vectors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeVectorClearClient {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${connector.indexing.runtime-base-url:}")
    private String runtimeBaseUrl;

    /**
     * Optional: protect the runtime admin endpoint.
     * Runtime uses {@code app.admin.api-key} / {@code app.admin.api-key-header}.
     */
    @Value("${connector.runtime.admin.api-key:}")
    private String runtimeAdminApiKey;

    @Value("${connector.runtime.admin.api-key-header:X-ADMIN-API-KEY}")
    private String runtimeAdminApiKeyHeader;

    public Map<String, Object> clearRuntimeVectors() {
        return clearRuntimeVectors(null);
    }

    public Map<String, Object> clearRuntimeVectors(@Nullable String reason) {
        if (!StringUtils.hasText(runtimeBaseUrl)) {
            return Map.of(
                "success", false,
                "skipped", true,
                "message", "connector.indexing.runtime-base-url is blank; skipping runtime vector clear"
            );
        }

        String trimmed = runtimeBaseUrl.trim();
        URI uri;
        try {
            // Runtime endpoint supports confirm via query param (no body required).
            String path = "/api/admin/migration/clear?confirm=true";
            uri = URI.create(trimTrailingSlash(trimmed) + path);
        } catch (IllegalArgumentException ex) {
            return Map.of(
                "success", false,
                "skipped", true,
                "message", "Invalid runtime base URL: " + ex.getMessage()
            );
        }

        if (!uri.isAbsolute()) {
            return Map.of(
                "success", false,
                "skipped", true,
                "message", "Runtime base URL must be absolute (include scheme, e.g. https://...). Got: " + runtimeBaseUrl
            );
        }

        RestTemplate restTemplate = restTemplateBuilder.build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(runtimeAdminApiKey)) {
            String headerName = StringUtils.hasText(runtimeAdminApiKeyHeader) ? runtimeAdminApiKeyHeader.trim() : "X-ADMIN-API-KEY";
            headers.set(headerName, runtimeAdminApiKey.trim());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(reason)) {
            payload.put("reason", reason.trim());
        }

        try {
            Object responseBody = restTemplate.postForEntity(uri, new HttpEntity<>(payload, headers), Object.class).getBody();
            return Map.of(
                "success", true,
                "skipped", false,
                "uri", uri.toString(),
                "response", responseBody
            );
        } catch (RestClientException ex) {
            log.warn("Runtime vector clear failed (uri={}): {}", uri, ex.getMessage());
            return Map.of(
                "success", false,
                "skipped", false,
                "uri", uri.toString(),
                "message", ex.getMessage()
            );
        }
    }

    private String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}

