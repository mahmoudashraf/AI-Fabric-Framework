package com.ai.infrastructure.connector.rest.runtime;

import com.ai.infrastructure.connector.rest.config.RestConnectorRuntimeProxyProperties;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Minimal proxy client for forwarding a subset of requests to an AI Fabric Runtime instance.
 *
 * <p>Used by "alias" endpoints such as {@code /api/ai/data-sync/*} exposed by this connector
 * (when enabled).</p>
 */
@Slf4j
@Component
public class RuntimeProxyClient {

    public record ProxyResponse(int status, String body, String contentType) {
    }

    private final RestConnectorRuntimeProxyProperties properties;
    private final HttpClient httpClient;

    public RuntimeProxyClient(RestConnectorRuntimeProxyProperties properties, RestRoutingConfig routingConfig) {
        this.properties = properties;

        int connectTimeoutMs = 2000;
        try {
            Integer configured = routingConfig != null
                && routingConfig.getConnector() != null
                && routingConfig.getConnector().getHttp() != null
                ? routingConfig.getConnector().getHttp().getConnectTimeoutMs()
                : null;
            if (configured != null && configured > 0) {
                connectTimeoutMs = configured;
            }
        } catch (Exception ignored) {
            // Keep a safe default even if the routing config is partially invalid.
        }

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
            .build();
    }

    public ProxyResponse forward(String method, String pathAndQuery, String jsonBody) {
        if (properties == null || !properties.isEnabled()) {
            return new ProxyResponse(404, "{\"success\":false,\"message\":\"Runtime proxy is disabled.\"}", "application/json");
        }

        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return new ProxyResponse(500, "{\"success\":false,\"message\":\"Runtime proxy baseUrl is not configured.\"}", "application/json");
        }

        String p = StringUtils.hasText(pathAndQuery) ? pathAndQuery.trim() : "";
        if (!p.startsWith("/")) {
            p = "/" + p;
        }

        URI uri;
        try {
            uri = URI.create(trimTrailingSlash(baseUrl.trim()) + p);
        } catch (Exception ex) {
            return new ProxyResponse(500, "{\"success\":false,\"message\":\"Invalid runtime proxy baseUrl.\"}", "application/json");
        }

        String m = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "GET";
        int timeoutMs = properties.getTimeoutMs() != null ? properties.getTimeoutMs() : 8000;
        Duration timeout = Duration.ofMillis(Math.max(100, timeoutMs));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout)
            .header("Accept", "application/json");

        if (StringUtils.hasText(properties.getApiKey())) {
            String headerName = StringUtils.hasText(properties.getApiKeyHeader())
                ? properties.getApiKeyHeader().trim()
                : "X-ADMIN-API-KEY";
            builder.header(headerName, properties.getApiKey().trim());
        }

        boolean hasBody = jsonBody != null;
        if (hasBody) {
            builder.header("Content-Type", "application/json");
        }

        HttpRequest request = switch (m) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8)).build();
            case "DELETE" -> {
                if (jsonBody != null) {
                    yield builder.method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
                }
                yield builder.DELETE().build();
            }
            default -> builder.GET().build();
        };

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String contentType = response != null
                ? response.headers().firstValue("content-type").orElse("application/json")
                : "application/json";
            int status = response != null ? response.statusCode() : 503;
            String body = response != null ? response.body() : null;
            return new ProxyResponse(status, body, contentType);
        } catch (HttpTimeoutException ex) {
            log.warn("Runtime proxy call timed out (method={}, uri={}, timeoutMs={}): {}", m, uri, timeoutMs, ex.getMessage());
            return new ProxyResponse(504, "{\"success\":false,\"message\":\"Runtime request timed out.\"}", "application/json");
        } catch (InterruptedException ex) {
            // Preserve the interrupt flag for cooperative cancellation.
            Thread.currentThread().interrupt();
            log.warn("Runtime proxy call interrupted (method={}, uri={}): {}", m, uri, ex.getMessage());
            return new ProxyResponse(503, "{\"success\":false,\"message\":\"Runtime request interrupted.\"}", "application/json");
        } catch (Exception ex) {
            log.warn("Runtime proxy call failed (method={}, uri={}): {}", m, uri, ex.getMessage());
            return new ProxyResponse(503, "{\"success\":false,\"message\":\"Runtime service is unavailable.\"}", "application/json");
        }
    }

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
