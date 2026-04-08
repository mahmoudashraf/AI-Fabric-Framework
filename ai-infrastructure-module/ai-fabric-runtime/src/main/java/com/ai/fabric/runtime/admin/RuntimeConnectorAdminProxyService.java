package com.ai.fabric.runtime.admin;

import com.ai.infrastructure.intent.action.connector.AIActionConnectorProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class RuntimeConnectorAdminProxyService {

    private final AIActionConnectorProperties connectorProperties;
    private final HttpClient httpClient;

    public RuntimeConnectorAdminProxyService(AIActionConnectorProperties connectorProperties) {
        this.connectorProperties = connectorProperties != null ? connectorProperties : new AIActionConnectorProperties();
        Duration connectTimeout = this.connectorProperties.getConnectTimeout() != null
            ? this.connectorProperties.getConnectTimeout()
            : Duration.ofSeconds(3);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
    }

    public ProxyResponse forwardGet(String upstreamPath) {
        String baseUrl = trimToNull(connectorProperties.getBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            return ProxyResponse.json(503, "{\"success\":false,\"message\":\"Connector admin proxy baseUrl is not configured.\"}");
        }

        URI target;
        try {
            target = URI.create(joinUrl(baseUrl, upstreamPath));
        } catch (Exception ex) {
            return ProxyResponse.json(500, "{\"success\":false,\"message\":\"Invalid connector admin proxy baseUrl.\"}");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
            .timeout(connectorProperties.getReadTimeout() != null ? connectorProperties.getReadTimeout() : Duration.ofSeconds(15))
            .GET();
        if (connectorProperties.getApiKey() != null
            && StringUtils.hasText(connectorProperties.getApiKey().getHeader())
            && StringUtils.hasText(connectorProperties.getApiKey().getValue())) {
            builder.header(
                connectorProperties.getApiKey().getHeader().trim(),
                connectorProperties.getApiKey().getValue().trim()
            );
        }

        try {
            HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse(MediaType.APPLICATION_JSON_VALUE);
            return new ProxyResponse(response.statusCode(), response.body(), contentType);
        } catch (java.net.http.HttpTimeoutException ex) {
            return ProxyResponse.json(504, "{\"success\":false,\"message\":\"Connector admin request timed out.\"}");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ProxyResponse.json(503, "{\"success\":false,\"message\":\"Connector admin request interrupted.\"}");
        } catch (Exception ex) {
            return ProxyResponse.json(503, "{\"success\":false,\"message\":\"Connector admin service is unavailable.\"}");
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String suffix = StringUtils.hasText(path) ? path.trim() : "/api/admin/overview";
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return base + suffix;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ProxyResponse(int status, String body, String contentType) {
        static ProxyResponse json(int status, String body) {
            return new ProxyResponse(status, body, MediaType.APPLICATION_JSON_VALUE);
        }
    }
}
