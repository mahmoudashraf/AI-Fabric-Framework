package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Bounded JSON transport shared by exact-target Platform-to-runtime operations. */
@Service
public class DeploymentRuntimeJsonTransport {
    static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(45);
    static final int MAX_RUNTIME_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentRuntimeJsonTransport(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(RUNTIME_TIMEOUT).build());
    }

    DeploymentRuntimeJsonTransport(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public Response send(String baseUrl,
                         String method,
                         String path,
                         Map<String, String> headers,
                         JsonNode body,
                         String idempotencyKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(runtimeUri(baseUrl, path))
                .timeout(RUNTIME_TIMEOUT)
                .header("Accept", "application/json");
            if (headers != null) headers.forEach(builder::header);
            if (StringUtils.hasText(idempotencyKey)) builder.header("Idempotency-Key", idempotencyKey);
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else {
                builder.header("Content-Type", "application/json").method(
                    method,
                    HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        body == null ? objectMapper.createObjectNode() : body
                    ))
                );
            }
            HttpResponse<InputStream> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
            );
            byte[] responseBytes;
            try (InputStream input = response.body()) {
                responseBytes = input.readNBytes(MAX_RUNTIME_RESPONSE_BYTES + 1);
            }
            if (responseBytes.length > MAX_RUNTIME_RESPONSE_BYTES) {
                throw new ResponseStatusException(BAD_GATEWAY, "Deployment runtime response exceeded the Platform boundary.");
            }
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            JsonNode payload = StringUtils.hasText(responseBody)
                ? objectMapper.readTree(responseBody)
                : objectMapper.createObjectNode();
            return new Response(response.statusCode(), payload);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach the deployment runtime.", exception);
        }
    }

    private URI runtimeUri(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl.trim());
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || !StringUtils.hasText(base.getHost())
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String basePath = base.getRawPath() == null ? "" : base.getRawPath();
            if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            return URI.create(base.getScheme() + "://" + base.getRawAuthority() + basePath + normalizedPath);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid deployment runtime URL.");
        }
    }

    public record Response(int statusCode, JsonNode body) {
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }
    }
}
