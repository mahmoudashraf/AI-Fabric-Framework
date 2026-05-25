package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.integration.json.JsonNavigation;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
                String header = firstText(datasetConfig, "authHeaderName", descriptor.config(), "authHeaderName");
                if (!StringUtils.hasText(header)) {
                    header = firstText(datasetConfig, "apiKeyHeader", descriptor.config(), "apiKeyHeader");
                }
                if (!StringUtils.hasText(header)) {
                    header = "Authorization";
                }
                String apiKey = authMaterial.secret("apiKey");
                if (StringUtils.hasText(apiKey)) {
                    putHeader(headers, header, apiKey, "API_KEY");
                }
            }
            case "BEARER" -> {
                String token = authMaterial.secret("token");
                if (StringUtils.hasText(token)) {
                    putHeader(headers, "Authorization", "Bearer " + token.trim(), "BEARER");
                }
            }
            case "BASIC" -> {
                String username = authMaterial.secret("username");
                String password = authMaterial.secret("password");
                if (StringUtils.hasText(username) && password != null) {
                    String basic = Base64.getEncoder().encodeToString((username.trim() + ":" + password).getBytes(StandardCharsets.UTF_8));
                    putHeader(headers, "Authorization", "Basic " + basic, "BASIC");
                }
            }
            default -> {
            }
        }
        return headers;
    }

    private static void putHeader(Map<String, String> headers, String name, String value, String authMode) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedValue = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalizedName) || containsHeaderControl(normalizedName) || normalizedName.contains(":")) {
            throw new IllegalArgumentException("REST_API source " + authMode + " auth contains an invalid header name.");
        }
        if (containsHeaderControl(normalizedValue)) {
            throw new IllegalArgumentException("REST_API source " + authMode + " auth contains an invalid header value.");
        }
        headers.put(normalizedName, normalizedValue);
    }

    private static boolean containsHeaderControl(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char candidate = value.charAt(index);
            if (candidate == '\r' || candidate == '\n') {
                return true;
            }
        }
        return false;
    }

    static URI buildUri(String baseUrl, String relativePath, Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder(trimTrailingSlash(baseUrl));
        if (relativePath != null && !relativePath.isBlank()) {
            String path = relativePath.trim();
            if (isAbsoluteOrNetworkPath(path)) {
                throw new IllegalArgumentException("REST_API vectorization source path must be relative.");
            }
            if (!path.startsWith("/")) {
                builder.append('/');
            }
            builder.append(path);
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
        return requirePublicHttpsUri(URI.create(builder.toString()));
    }

    private static boolean isAbsoluteOrNetworkPath(String path) {
        if (path.startsWith("//")) {
            return true;
        }
        int colonIndex = path.indexOf(':');
        if (colonIndex <= 0) {
            return false;
        }
        for (int index = 0; index < colonIndex; index++) {
            char candidate = path.charAt(index);
            boolean validSchemeChar = Character.isLetterOrDigit(candidate) || candidate == '+' || candidate == '-' || candidate == '.';
            if (!validSchemeChar) {
                return false;
            }
        }
        return Character.isLetter(path.charAt(0));
    }

    private static URI requirePublicHttpsUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("REST_API vectorization source URL must use HTTPS.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("REST_API vectorization source URL must not contain user info.");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("REST_API vectorization source URL must include a host.");
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new IllegalArgumentException("REST_API vectorization source URL host is not allowed.");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("REST_API vectorization source URL resolves to a private address.");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("REST_API vectorization source URL host cannot be resolved.", exception);
        }
        return uri;
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19));
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
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
