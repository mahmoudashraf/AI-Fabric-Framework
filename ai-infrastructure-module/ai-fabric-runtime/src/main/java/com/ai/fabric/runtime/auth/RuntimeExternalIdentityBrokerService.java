package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeExternalIdentityBrokerService {

    private static final int MAX_RATE_LIMIT_BUCKETS = 4_096;

    private final RuntimeAuthProperties properties;
    private final RuntimePublicTokenService tokenService;
    private final Map<String, Deque<Instant>> rateLimitBuckets = new ConcurrentHashMap<>();
    private volatile JwtDecoder decoder;

    public RuntimeExternalIdentityBrokerService(RuntimeAuthProperties properties,
                                                RuntimePublicTokenService tokenService) {
        this(properties, tokenService, null);
    }

    RuntimeExternalIdentityBrokerService(RuntimeAuthProperties properties,
                                         RuntimePublicTokenService tokenService,
                                         JwtDecoder decoder) {
        this.properties = properties != null ? properties : new RuntimeAuthProperties();
        this.tokenService = tokenService;
        this.decoder = decoder;
    }

    public BrokeredIdentity authenticate(HttpServletRequest request, String authorizationHeader) {
        RuntimeAuthProperties.ExternalIdentityBroker config = configuration();
        if (!config.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "External identity broker is not enabled.");
        }
        String origin = normalizeOrigin(request == null ? null : request.getHeader("Origin"));
        if (!StringUtils.hasText(origin) || !normalizedOrigins(config.getAllowedOrigins()).contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "External identity broker origin is not allowed.");
        }
        enforceRateLimit(origin + "|" + (request == null ? "unknown" : request.getRemoteAddr()), config);
        String token = bearerToken(authorizationHeader);
        Jwt jwt;
        try {
            jwt = decoder().decode(token);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "External identity token is invalid.");
        }
        if (Boolean.TRUE.equals(jwt.getClaim("is_anonymous"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anonymous provider identities cannot obtain authenticated runtime access.");
        }
        if (config.isRequireVerifiedEmail() && !verifiedEmail(jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A verified customer email is required.");
        }
        String subject = trim(jwt.getSubject());
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "External identity subject is missing.");
        }
        String sessionId = trim(jwt.getClaimAsString("session_id"));
        RuntimePublicTokenService.IssuedPublicRuntimeToken issued = tokenService.issueAuthenticatedToken(
            subject,
            RuntimeAuthSubjectType.END_USER,
            sessionId,
            trim(properties.getPublicTokens().getDefaults().getDeploymentId()),
            trim(properties.getPublicTokens().getDefaults().getCustomerId()),
            trim(properties.getPublicTokens().getDefaults().getTenantId()),
            config.getGrantedScopes(),
            null,
            List.of()
        );
        return new BrokeredIdentity(issued, trim(jwt.getClaimAsString("email")));
    }

    public boolean isEnabled() {
        return configuration().isEnabled();
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current != null) return current;
        synchronized (this) {
            if (decoder != null) return decoder;
            RuntimeAuthProperties.ExternalIdentityBroker config = configuration();
            String issuer = required(config.getIssuer(), "External identity issuer is required.");
            String jwkSetUri = required(config.getJwkSetUri(), "External identity JWK set URI is required.");
            requireHttps(issuer, "issuer");
            requireHttps(jwkSetUri, "JWK set URI");
            NimbusJwtDecoder created = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
            OAuth2TokenValidator<Jwt> audience = jwt -> {
                String expected = trim(config.getAudience());
                if (expected == null || jwt.getAudience().contains(expected)) return OAuth2TokenValidatorResult.success();
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "External identity audience is invalid.", null));
            };
            created.setJwtValidator(jwt -> {
                OAuth2TokenValidatorResult base = defaults.validate(jwt);
                return base.hasErrors() ? base : audience.validate(jwt);
            });
            decoder = created;
            return created;
        }
    }

    private RuntimeAuthProperties.ExternalIdentityBroker configuration() {
        RuntimeAuthProperties.ExternalIdentityBroker config = properties.getExternalIdentityBroker();
        return config == null ? new RuntimeAuthProperties.ExternalIdentityBroker() : config;
    }

    private void enforceRateLimit(String key, RuntimeAuthProperties.ExternalIdentityBroker config) {
        int limit = Math.max(1, config.getMaxRequestsPerWindow());
        Instant cutoff = Instant.now().minusSeconds(Math.max(1, config.getRateLimitWindowSeconds()));
        if (!rateLimitBuckets.containsKey(key) && rateLimitBuckets.size() >= MAX_RATE_LIMIT_BUCKETS) {
            key = "bounded-overflow";
        }
        Deque<Instant> bucket = rateLimitBuckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) bucket.removeFirst();
            if (bucket.size() >= limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "External identity broker rate limit exceeded.");
            }
            bucket.addLast(Instant.now());
        }
    }

    private List<String> normalizedOrigins(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalizeOrigin).filter(StringUtils::hasText).distinct().toList();
    }

    private String normalizeOrigin(String value) {
        try {
            if (!StringUtils.hasText(value)) return null;
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() >= 0
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) return null;
            return "https://" + uri.getHost().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String bearerToken(String value) {
        if (!StringUtils.hasText(value) || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "External bearer token is required.");
        }
        String token = value.substring(7).trim();
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "External bearer token is required.");
        return token;
    }

    private boolean verifiedEmail(Jwt jwt) {
        return Boolean.TRUE.equals(jwt.getClaim("email_verified"))
            || StringUtils.hasText(jwt.getClaimAsString("email_confirmed_at"));
    }

    private String required(String value, String message) {
        String normalized = trim(value);
        if (normalized == null) throw new IllegalStateException(message);
        return normalized;
    }

    private void requireHttps(String value, String label) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("External identity " + label + " must use HTTPS.");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record BrokeredIdentity(
        RuntimePublicTokenService.IssuedPublicRuntimeToken issued,
        String email
    ) {
    }
}
