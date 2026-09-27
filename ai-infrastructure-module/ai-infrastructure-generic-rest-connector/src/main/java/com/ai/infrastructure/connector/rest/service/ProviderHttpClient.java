package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.util.UrlBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
import java.util.Locale;
import java.util.Map;

@Service
public class ProviderHttpClient {

    private final RestRoutingConfig config;
    private final ProviderTokenService tokenService;
    private final ProviderRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public ProviderHttpClient(
        RestRoutingConfig config,
        ProviderTokenService tokenService,
        ProviderRateLimiter rateLimiter,
        ObjectMapper objectMapper
    ) {
        this.config = config;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public ProviderResponse execute(ProviderRequest request) {
        return execute(request, false, 1);
    }

    private ProviderResponse execute(ProviderRequest request, boolean refreshed, int attempt) {
        RestRoutingConfig.ConnectionProfile profile = config.getConnectionProfiles().get(request.connectionProfileRef());
        if (profile == null) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "Connection profile is not configured.");
        }
        URI uri = buildUri(profile, request.path(), request.query());
        ProviderTokenService.validateApprovedHost(profile, uri);
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.headers() != null) {
            headers.putAll(request.headers());
        }
        ProviderTokenService.Authentication authentication = tokenService.authentication(request.connectionProfileRef());
        headers.putAll(authentication.headers());
        HttpRequest httpRequest = buildRequest(uri, request, headers);
        ProviderResponse providerResponse;
        try (ProviderRateLimiter.Lease ignored = rateLimiter.acquire(request.connectionProfileRef(), profile.getRatePolicy())) {
            try {
                HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                String body = readBoundedBody(response, request.maxResponseBytes());
                Map<String, String> correlation = new LinkedHashMap<>();
                for (String header : profile.getCorrelationResponseHeaders()) {
                    response.headers().firstValue(header).ifPresent(value -> correlation.put(header, bounded(value, 256)));
                }
                providerResponse = new ProviderResponse(response.statusCode(), body, Map.copyOf(correlation));
            } catch (java.net.http.HttpTimeoutException ex) {
                throw new ProviderCallException(ProviderErrorClass.TIMEOUT, 0, "Provider request timed out.", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Provider request was interrupted.", ex);
            } catch (ProviderCallException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Provider request failed.", ex);
            }
        }
        if (providerResponse.status() == 401 && !refreshed
            && profile.getAuth() != null
            && profile.getAuth().getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.FORM_TOKEN_EXCHANGE) {
            tokenService.invalidateIfCurrent(request.connectionProfileRef(), authentication.generation());
            return execute(request, true, attempt);
        }
        if (providerResponse.status() == 429 || providerResponse.status() == 503) {
            rateLimiter.pause(request.connectionProfileRef(), profile.getRatePolicy(), providerResponse.status());
        }
        RestRoutingConfig.RatePolicy ratePolicy = profile.getRatePolicy();
        if (request.retryAllowed()
            && ratePolicy != null
            && attempt < ratePolicy.getMaxAttempts()
            && ratePolicy.getRetryStatuses() != null
            && ratePolicy.getRetryStatuses().contains(providerResponse.status())) {
            sleep(ratePolicy.getRetryBackoffMs());
            return execute(request, refreshed, attempt + 1);
        }
        return providerResponse;
    }

    public ProviderErrorClass classify(int status) {
        if (status == 400 || status == 404 || status == 409 || status == 422) {
            return ProviderErrorClass.BAD_REQUEST;
        }
        if (status == 401) {
            return ProviderErrorClass.AUTHENTICATION_REQUIRED;
        }
        if (status == 403) {
            return ProviderErrorClass.RESOURCE_ACCESS_DENIED;
        }
        if (status == 429) {
            return ProviderErrorClass.RATE_LIMITED;
        }
        if (status >= 500) {
            return ProviderErrorClass.SERVICE_UNAVAILABLE;
        }
        return ProviderErrorClass.BAD_REQUEST;
    }

    public ProviderErrorClass classify(String profileRef, int status, String body) {
        RestRoutingConfig.ConnectionProfile profile = StringUtils.hasText(profileRef)
            ? config.getConnectionProfiles().get(profileRef.trim())
            : null;
        if (profile != null && profile.getErrorMappings() != null) {
            for (RestRoutingConfig.ErrorMapping mapping : profile.getErrorMappings()) {
                if (mapping == null || mapping.getErrorClass() == null || mapping.getStatus() != status) {
                    continue;
                }
                if (StringUtils.hasText(mapping.getBodyJsonPointer())) {
                    String actual = errorValue(body, mapping.getBodyJsonPointer());
                    if (!StringUtils.hasText(actual) || !actual.equals(mapping.getEqualsValue())) {
                        continue;
                    }
                }
                return ProviderErrorClass.valueOf(mapping.getErrorClass().name());
            }
        }
        return classify(status);
    }

    private String readBoundedBody(HttpResponse<InputStream> response, int configuredLimit) throws Exception {
        int limit = Math.max(1, configuredLimit);
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new ProviderCallException(
                    ProviderErrorClass.MALFORMED_RESPONSE,
                    response.statusCode(),
                    "Provider response exceeded the configured size limit."
                );
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String errorValue(String body, String pointer) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(body).at(pointer);
            return value.isValueNode() ? value.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Provider retry was interrupted.", ex);
        }
    }

    private URI buildUri(RestRoutingConfig.ConnectionProfile profile, String path, Map<String, Object> query) {
        String url = UrlBuilder.join(profile.getBaseUrl(), path);
        if (query == null || query.isEmpty()) {
            return URI.create(url);
        }
        StringBuilder out = new StringBuilder(url);
        boolean first = !url.contains("?");
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            out.append(first ? '?' : '&');
            first = false;
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            out.append('=');
            out.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return URI.create(out.toString());
    }

    private HttpRequest buildRequest(URI uri, ProviderRequest request, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(Math.max(100, request.timeoutMs())));
        String method = StringUtils.hasText(request.method()) ? request.method().trim().toUpperCase(Locale.ROOT) : "GET";
        String body = request.body() != null ? request.body() : "";
        builder.method(method, supportsBody(method)
            ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
            : HttpRequest.BodyPublishers.noBody());
        if (supportsBody(method)) {
            builder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }
        headers.forEach((name, value) -> {
            if (StringUtils.hasText(name) && value != null) {
                builder.header(name.trim(), value);
            }
        });
        return builder.build();
    }

    private boolean supportsBody(String method) {
        return List.of("POST", "PUT", "PATCH").contains(method);
    }

    private String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record ProviderRequest(
        String connectionProfileRef,
        String method,
        String path,
        Map<String, Object> query,
        Map<String, String> headers,
        String body,
        int timeoutMs,
        int maxResponseBytes,
        boolean retryAllowed
    ) {
    }

    public record ProviderResponse(int status, String body, Map<String, String> correlationHeaders) {
        public boolean successful() {
            return status >= 200 && status < 300;
        }
    }
}
