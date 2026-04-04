package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.integration.json.JsonNavigation;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class VectorizationSourceAdapterSupport {

    private VectorizationSourceAdapterSupport() {
    }

    static JsonNode datasetConfig(VectorizationExecutionBundle bundle, String entityType) {
        JsonNode mappingConfig = bundle.mappingConfig();
        String datasetKey = entityType;
        JsonNode mapping = mappingConfig.path("entityMappings").path(entityType);
        if (mapping.isObject() && StringUtils.hasText(mapping.path("dataset").asText(null))) {
            datasetKey = mapping.path("dataset").asText().trim();
        }
        JsonNode config = bundle.connectionDescriptor().config();
        JsonNode datasets = config.path("datasets");
        JsonNode dataset = datasets.path(datasetKey);
        return dataset.isObject() ? dataset : config;
    }

    static int pageSize(JsonNode datasetConfig, JsonNode executionConfig, int fallback) {
        int configured = datasetConfig.path("pageSize").asInt(0);
        if (configured > 0) {
            return configured;
        }
        configured = executionConfig.path("pageSize").asInt(0);
        if (configured > 0) {
            return configured;
        }
        configured = executionConfig.path("batchSize").asInt(0);
        return configured > 0 ? configured : fallback;
    }

    static Map<String, String> authHeaders(ConnectionDescriptor descriptor, ResolvedSourceAuthMaterial authMaterial, JsonNode datasetConfig) {
        String authMode = descriptor.authMode() == null ? "" : descriptor.authMode().trim().toUpperCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        switch (authMode) {
            case "API_KEY" -> {
                String header = firstText(datasetConfig, "apiKeyHeader", descriptor.config(), "apiKeyHeader");
                if (!StringUtils.hasText(header)) {
                    header = "Authorization";
                }
                String apiKey = authMaterial.secret("apiKey");
                if (StringUtils.hasText(apiKey)) {
                    headers.put(header.trim(), apiKey.trim());
                }
            }
            case "BEARER" -> {
                String token = authMaterial.secret("token");
                if (StringUtils.hasText(token)) {
                    headers.put("Authorization", "Bearer " + token.trim());
                }
            }
            case "BASIC" -> {
                String username = authMaterial.secret("username");
                String password = authMaterial.secret("password");
                if (StringUtils.hasText(username) && password != null) {
                    String basic = Base64.getEncoder().encodeToString((username.trim() + ":" + password).getBytes(StandardCharsets.UTF_8));
                    headers.put("Authorization", "Basic " + basic);
                }
            }
            default -> {
            }
        }
        return headers;
    }

    static URI buildUri(String baseUrl, String relativePath, Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder(trimTrailingSlash(baseUrl));
        if (relativePath != null && !relativePath.isBlank()) {
            if (!relativePath.startsWith("/")) {
                builder.append('/');
            }
            builder.append(relativePath.trim());
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            List<String> parts = new ArrayList<>();
            queryParams.forEach((key, value) -> {
                if (!StringUtils.hasText(key) || value == null) {
                    return;
                }
                parts.add(URLEncoder.encode(key.trim(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8));
            });
            if (!parts.isEmpty()) {
                builder.append(builder.indexOf("?") >= 0 ? '&' : '?');
                builder.append(String.join("&", parts));
            }
        }
        return URI.create(builder.toString());
    }

    static String text(JsonNode node, String field, String fallback) {
        if (node != null && node.isObject() && StringUtils.hasText(node.path(field).asText(null))) {
            return node.path(field).asText().trim();
        }
        return fallback;
    }

    static String firstText(JsonNode firstNode, String firstField, JsonNode secondNode, String secondField) {
        String primary = text(firstNode, firstField, null);
        return StringUtils.hasText(primary) ? primary : text(secondNode, secondField, null);
    }

    static String nextCursor(JsonNode response, JsonNode datasetConfig) {
        String cursorPath = text(datasetConfig, "nextCursorPath", null);
        return StringUtils.hasText(cursorPath) ? JsonNavigation.asText(response, cursorPath) : null;
    }

    static boolean hasMore(JsonNode response, JsonNode datasetConfig, int recordCount, int pageSize, String paginationMode) {
        String hasMorePath = text(datasetConfig, "hasMorePath", null);
        if (StringUtils.hasText(hasMorePath)) {
            return JsonNavigation.isTruthy(response, hasMorePath);
        }
        return switch (paginationMode) {
            case "NONE" -> false;
            case "CURSOR" -> StringUtils.hasText(nextCursor(response, datasetConfig));
            default -> pageSize > 0 && recordCount >= pageSize;
        };
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
