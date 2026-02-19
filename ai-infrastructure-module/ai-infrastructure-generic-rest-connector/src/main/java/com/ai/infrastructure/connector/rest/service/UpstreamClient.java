package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UpstreamClient {

    private final HttpClient httpClient;

    public UpstreamClient(RestRoutingConfig config) {
        RestRoutingConfig.Http http = config != null && config.getConnector() != null ? config.getConnector().getHttp() : null;
        int connectTimeoutMs = http != null ? http.getConnectTimeoutMs() : 2000;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
            .build();
    }

    public UpstreamResponse execute(URI uri,
                                    String method,
                                    String jsonBody,
                                    Map<String, String> headers,
                                    Duration timeout) throws Exception {
        if (uri == null) {
            throw new IllegalArgumentException("uri is required");
        }

        String m = StringUtils.hasText(method) ? method.trim().toUpperCase() : "POST";
        Duration effectiveTimeout = timeout != null ? timeout : Duration.ofSeconds(8);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(effectiveTimeout);

        if ("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m)) {
            builder.method(m, HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8));
        } else {
            builder.method(m, HttpRequest.BodyPublishers.noBody());
        }

        Map<String, String> safeHeaders = headers != null ? new LinkedHashMap<>(headers) : Map.of();
        safeHeaders.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            builder.header(key.trim(), value);
        });

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> outHeaders = new LinkedHashMap<>();
        if (response.headers() != null && response.headers().map() != null) {
            response.headers().map().forEach((name, values) -> {
                if (!StringUtils.hasText(name) || values == null || values.isEmpty()) {
                    return;
                }
                outHeaders.put(name, values.getFirst());
            });
        }
        return new UpstreamResponse(response.statusCode(), response.body(), outHeaders);
    }

    public record UpstreamResponse(int statusCode, String body, Map<String, Object> headers) {
        public UpstreamResponse {
            headers = headers != null ? Map.copyOf(headers) : Map.of();
        }
    }
}
