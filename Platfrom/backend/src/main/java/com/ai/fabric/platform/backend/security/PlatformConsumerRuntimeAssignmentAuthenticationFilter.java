package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformConsumerRuntimeAssignmentApiProperties;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class PlatformConsumerRuntimeAssignmentAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformConsumerRuntimeAssignmentAuthenticationFilter.class);
    private static final String ASSIGNMENT_PATH_PREFIX = "/api/public/consumers/";
    private static final String ASSIGNMENT_PATH_SUFFIX = "/runtime-assignment";

    private final PlatformConsumerRuntimeAssignmentApiProperties properties;
    private final PlatformSecretService platformSecretService;

    public PlatformConsumerRuntimeAssignmentAuthenticationFilter(
        PlatformConsumerRuntimeAssignmentApiProperties properties,
        PlatformSecretService platformSecretService
    ) {
        this.properties = properties;
        this.platformSecretService = platformSecretService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !configuredConsumerId().equals(normalize(extractConsumerId(request.getRequestURI())));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || PlatformSecurityContext.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String presentedKey = request.getHeader(apiKeyHeaderName());
        if (!StringUtils.hasText(presentedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!matches(configuredApiKey(), presentedKey)) {
            log.warn(
                "Consumer runtime assignment auth rejected request: method={}, path={}, header={}",
                request.getMethod(),
                request.getRequestURI(),
                apiKeyHeaderName()
            );
            filterChain.doFilter(request, response);
            return;
        }

        String consumerId = configuredConsumerId();
        PlatformPrincipal principal = new PlatformPrincipal(
            "consumer-runtime-assignment:" + consumerId,
            PlatformRole.CONSUMER_RUNTIME_ASSIGNMENT_CLIENT,
            consumerId + " Runtime Assignment Client",
            "CONSUMER_RUNTIME_ASSIGNMENT_API_KEY"
        );
        SecurityContextHolder.getContext().setAuthentication(PlatformAuthenticationSupport.authenticationFor(principal));
        filterChain.doFilter(request, response);
    }

    private String extractConsumerId(String requestUri) {
        if (!StringUtils.hasText(requestUri) || !requestUri.startsWith(ASSIGNMENT_PATH_PREFIX)) {
            return "";
        }
        String remainder = requestUri.substring(ASSIGNMENT_PATH_PREFIX.length());
        if (!remainder.endsWith(ASSIGNMENT_PATH_SUFFIX)) {
            return "";
        }
        String consumerId = remainder.substring(0, remainder.length() - ASSIGNMENT_PATH_SUFFIX.length());
        return consumerId.contains("/") ? "" : consumerId;
    }

    private String configuredApiKey() {
        String configured = firstNonBlank(properties.apiKey(), resolveConfiguredSecret());
        return configured == null ? "" : configured;
    }

    private String resolveConfiguredSecret() {
        if (!StringUtils.hasText(properties.apiKeySecretName())) {
            return null;
        }
        return platformSecretService.resolveSecret(properties.apiKeySecretName().trim());
    }

    private String apiKeyHeaderName() {
        return firstNonBlank(properties.apiKeyHeaderName(), "X-LOOMAI-ASSIGNMENT-API-KEY");
    }

    private String configuredConsumerId() {
        return normalize(firstNonBlank(properties.consumerId(), "produs-staging"));
    }

    private boolean matches(String configuredKey, String presentedKey) {
        if (!StringUtils.hasText(configuredKey) || !StringUtils.hasText(presentedKey)) {
            return false;
        }
        return MessageDigest.isEqual(
            configuredKey.getBytes(StandardCharsets.UTF_8),
            presentedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String firstNonBlank(String primary, String secondary) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(secondary)) {
            return secondary.trim();
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
