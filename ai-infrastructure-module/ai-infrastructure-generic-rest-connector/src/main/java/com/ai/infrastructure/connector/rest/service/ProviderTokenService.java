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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProviderTokenService {

    private static final int MAX_TOKEN_RESPONSE_BYTES = 64 * 1024;

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Map<String, TokenPosture> postures = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> generations = new ConcurrentHashMap<>();

    public ProviderTokenService(RestRoutingConfig config, ObjectMapper objectMapper, Clock clock) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Map<String, String> authenticationHeaders(String profileId, boolean forceRefresh) {
        return authentication(profileId, forceRefresh).headers();
    }

    public Authentication authentication(String profileId) {
        return authentication(profileId, false);
    }

    private Authentication authentication(String profileId, boolean forceRefresh) {
        RestRoutingConfig.ConnectionProfile profile = requireProfile(profileId);
        RestRoutingConfig.ProviderAuth auth = profile.getAuth();
        if (auth == null || auth.getStrategy() == null || auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.NONE) {
            return new Authentication(Map.of(), 0);
        }
        if (auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.API_KEY) {
            if (!StringUtils.hasText(auth.getApiKeyHeader()) || !StringUtils.hasText(auth.getApiKeyValue())) {
                throw new ProviderCallException(ProviderErrorClass.AUTHENTICATION_REQUIRED, 0, "Provider API-key credentials are not configured.");
            }
            postures.put(profileId, new TokenPosture("READY", null, null));
            return new Authentication(Map.of(auth.getApiKeyHeader().trim(), auth.getApiKeyValue()), 0);
        }
        CachedToken token = token(profileId, profile, forceRefresh);
        String scheme = StringUtils.hasText(auth.getAuthorizationScheme()) ? auth.getAuthorizationScheme().trim() + " " : "";
        return new Authentication(
            Map.of(auth.getAuthorizationHeader().trim(), scheme + token.value()),
            token.generation()
        );
    }

    public void invalidate(String profileId) {
        if (StringUtils.hasText(profileId)) {
            tokens.remove(profileId.trim());
        }
    }

    public void invalidateIfCurrent(String profileId, long generation) {
        if (!StringUtils.hasText(profileId) || generation <= 0) {
            return;
        }
        tokens.computeIfPresent(profileId.trim(), (ignored, current) ->
            current.generation() == generation ? null : current
        );
    }

    public Map<String, TokenPosture> posture() {
        Map<String, TokenPosture> out = new LinkedHashMap<>();
        config.getConnectionProfiles().keySet().stream().sorted().forEach(profileId -> {
            RestRoutingConfig.ConnectionProfile profile = config.getConnectionProfiles().get(profileId);
            RestRoutingConfig.ProviderAuth auth = profile != null ? profile.getAuth() : null;
            if (auth == null || auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.NONE) {
                out.put(profileId, new TokenPosture("NOT_REQUIRED", null, null));
                return;
            }
            TokenPosture current = postures.get(profileId);
            CachedToken cached = tokens.get(profileId);
            if (cached != null && current != null && cached.expiresAt() != null) {
                String status = cached.expiresAt().isBefore(clock.instant().plusSeconds(Math.max(120, auth.getExpirySkewSeconds() * 2L)))
                    ? "EXPIRING" : "READY";
                out.put(profileId, new TokenPosture(status, cached.expiresAt(), current.lastErrorClass()));
            } else {
                out.put(profileId, current != null ? current : new TokenPosture("NOT_ACQUIRED", null, null));
            }
        });
        return Map.copyOf(out);
    }

    private CachedToken token(String profileId, RestRoutingConfig.ConnectionProfile profile, boolean forceRefresh) {
        RestRoutingConfig.ProviderAuth auth = profile.getAuth();
        CachedToken current = tokens.get(profileId);
        if (!forceRefresh && isUsable(current, auth)) {
            return current;
        }
        synchronized (locks.computeIfAbsent(profileId, ignored -> new Object())) {
            current = tokens.get(profileId);
            if (!forceRefresh && isUsable(current, auth)) {
                return current;
            }
            try {
                CachedToken acquired = acquire(profileId, profile, auth);
                tokens.put(profileId, acquired);
                postures.put(profileId, new TokenPosture("READY", acquired.expiresAt(), null));
                return acquired;
            } catch (ProviderCallException ex) {
                postures.put(profileId, new TokenPosture("AUTH_FAILED", null, ex.errorClass().name()));
                throw ex;
            }
        }
    }

    private CachedToken acquire(
        String profileId,
        RestRoutingConfig.ConnectionProfile profile,
        RestRoutingConfig.ProviderAuth auth
    ) {
        if (auth.getStrategy() != RestRoutingConfig.ProviderAuth.Strategy.FORM_TOKEN_EXCHANGE) {
            throw new ProviderCallException(ProviderErrorClass.AUTHENTICATION_REQUIRED, 0, "Unsupported provider authentication strategy.");
        }
        String tokenBaseUrl = StringUtils.hasText(auth.getTokenBaseUrl())
            ? auth.getTokenBaseUrl().trim()
            : profile.getBaseUrl();
        String endpoint = UrlBuilder.join(tokenBaseUrl, auth.getTokenPath());
        validateApprovedHost(profile, URI.create(endpoint));
        Map<String, String> fields = new LinkedHashMap<>();
        if (auth.getStaticFields() != null) {
            fields.putAll(auth.getStaticFields());
        }
        if (auth.getCredentialFields() != null) {
            fields.putAll(auth.getCredentialFields());
        }
        if (fields.isEmpty() || fields.values().stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw new ProviderCallException(ProviderErrorClass.AUTHENTICATION_REQUIRED, 0, "Provider token credential fields are incomplete.");
        }
        String body = fields.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofMillis(Math.max(100, auth.getTimeoutMs())))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .method(auth.getTokenMethod().trim().toUpperCase(), HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String responseBody = readBoundedBody(response);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderCallException(
                    response.statusCode() == 401 || response.statusCode() == 403
                        ? ProviderErrorClass.AUTHENTICATION_REQUIRED
                        : ProviderErrorClass.SERVICE_UNAVAILABLE,
                    response.statusCode(),
                    "Provider token exchange was rejected."
                );
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode tokenNode = root.at(auth.getTokenJsonPointer());
            if (!tokenNode.isTextual() || !StringUtils.hasText(tokenNode.asText())) {
                throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, response.statusCode(), "Provider token response did not contain a usable token.");
            }
            long generation = generations.computeIfAbsent(profileId, ignored -> new AtomicLong()).incrementAndGet();
            return new CachedToken(tokenNode.asText(), resolveExpiry(root, auth), generation);
        } catch (ProviderCallException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ProviderCallException(ProviderErrorClass.TIMEOUT, 0, "Provider token exchange timed out.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Provider token exchange was interrupted.", ex);
        } catch (Exception ex) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider token exchange failed.", ex);
        }
    }

    private String readBoundedBody(HttpResponse<InputStream> response) throws Exception {
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(MAX_TOKEN_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_TOKEN_RESPONSE_BYTES) {
                throw new ProviderCallException(
                    ProviderErrorClass.MALFORMED_RESPONSE,
                    response.statusCode(),
                    "Provider token response exceeded the configured size limit."
                );
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private Instant resolveExpiry(JsonNode root, RestRoutingConfig.ProviderAuth auth) {
        if (StringUtils.hasText(auth.getAbsoluteExpiryJsonPointer())) {
            JsonNode node = root.at(auth.getAbsoluteExpiryJsonPointer());
            if (node.isNumber()) {
                long value = node.asLong();
                return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
            }
            if (node.isTextual()) {
                String raw = node.asText().trim();
                try {
                    return Instant.parse(raw);
                } catch (Exception ignored) {
                    try {
                        return OffsetDateTime.parse(raw).toInstant();
                    } catch (Exception ignoredAgain) {
                        try {
                            long value = Long.parseLong(raw);
                            return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
                        } catch (Exception invalid) {
                            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider token expiry was invalid.");
                        }
                    }
                }
            }
        }
        if (StringUtils.hasText(auth.getRelativeExpiryJsonPointer())) {
            long seconds = root.at(auth.getRelativeExpiryJsonPointer()).asLong(-1);
            if (seconds > 0) {
                return clock.instant().plusSeconds(seconds);
            }
        }
        throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider token response did not contain a usable expiry.");
    }

    private boolean isUsable(CachedToken token, RestRoutingConfig.ProviderAuth auth) {
        return token != null
            && token.expiresAt() != null
            && token.expiresAt().isAfter(clock.instant().plusSeconds(Math.max(0, auth.getExpirySkewSeconds())));
    }

    private RestRoutingConfig.ConnectionProfile requireProfile(String profileId) {
        RestRoutingConfig.ConnectionProfile profile = StringUtils.hasText(profileId)
            ? config.getConnectionProfiles().get(profileId.trim())
            : null;
        if (profile == null) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "Connection profile is not configured.");
        }
        return profile;
    }

    static void validateApprovedHost(RestRoutingConfig.ConnectionProfile profile, URI uri) {
        if (profile == null || uri == null || !StringUtils.hasText(uri.getHost())) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "Provider URL is invalid.");
        }
        boolean approved = profile.getAllowedHosts() != null && profile.getAllowedHosts().stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .anyMatch(host -> host.equalsIgnoreCase(uri.getHost()));
        if (!approved) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Provider host is outside the connection profile allowlist.");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String value, Instant expiresAt, long generation) {
    }

    public record Authentication(Map<String, String> headers, long generation) {
    }

    public record TokenPosture(String status, Instant expiresAt, String lastErrorClass) {
    }
}
