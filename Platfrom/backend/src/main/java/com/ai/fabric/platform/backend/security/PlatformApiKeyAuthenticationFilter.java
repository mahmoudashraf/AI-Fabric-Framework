package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
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

public class PlatformApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformApiKeyAuthenticationFilter.class);
    private static final String OPERATOR_API_KEY_SECRET = "PLATFORM_OPERATOR_API_KEY";
    private static final String ADMIN_API_KEY_SECRET = "PLATFORM_ADMIN_API_KEY";

    private final PlatformAuthProperties properties;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedProductServiceRepository productServiceRepository;

    public PlatformApiKeyAuthenticationFilter(PlatformAuthProperties properties,
                                              PlatformSecretService platformSecretService,
                                              PlatformManagedProductServiceRepository productServiceRepository) {
        this.properties = properties;
        this.platformSecretService = platformSecretService;
        this.productServiceRepository = productServiceRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (PlatformSecurityContext.isAuthenticated() || !authAttemptAllowed(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String presentedKey = request.getHeader(properties.headerName());
        if (!StringUtils.hasText(presentedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        PlatformPrincipal principal = matchPrincipal(presentedKey, request.getRequestURI());
        if (principal == null) {
            log.warn(
                "Platform auth rejected request: method={}, path={}, header={}",
                request.getMethod(),
                request.getRequestURI(),
                properties.headerName()
            );
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(PlatformAuthenticationSupport.authenticationFor(principal));
        filterChain.doFilter(request, response);
    }

    private PlatformPrincipal matchPrincipal(String presentedKey, String requestPath) {
        if (properties.apiKeyEnabled() && matches(resolveAdminApiKey(), presentedKey)) {
            return new PlatformPrincipal(
                "platform-admin",
                PlatformRole.PLATFORM_ADMIN,
                "Platform Admin API Key",
                "API_KEY"
            );
        }
        if (properties.apiKeyEnabled() && matches(resolveOperatorApiKey(), presentedKey)) {
            return new PlatformPrincipal(
                "platform-operator",
                PlatformRole.PLATFORM_OPERATOR,
                "Platform Operator API Key",
                "API_KEY"
            );
        }
        if (supportsProductServiceAuth(requestPath)) {
            for (PlatformManagedProductServiceEntity service : productServiceRepository.findAllByStatusIgnoreCaseOrderByDisplayNameAsc("ACTIVE")) {
                if (!hasText(service.getSecretName())) {
                    continue;
                }
                if (matches(platformSecretService.resolveSecret(service.getSecretName()), presentedKey)) {
                    return new PlatformPrincipal(
                        service.getServiceRef(),
                        PlatformRole.PLATFORM_PRODUCT_SERVICE,
                        firstNonBlank(service.getDisplayName(), service.getServiceRef()),
                        "PRODUCT_SERVICE_API_KEY"
                    );
                }
            }
        }
        return null;
    }

    private boolean supportsProductServiceAuth(String requestPath) {
        if (!hasText(requestPath)) {
            return false;
        }
        return requestPath.startsWith("/api/shopify/")
            || requestPath.startsWith("/api/public/consumers/")
            || requestPath.equals("/api/merchant/partner-access/requests")
            || requestPath.startsWith("/api/merchant/partner-access/requests/");
    }

    private boolean authAttemptAllowed(String requestPath) {
        boolean adminOrOperatorAuthEnabled = properties.apiKeyEnabled()
            && (hasText(resolveAdminApiKey()) || hasText(resolveOperatorApiKey()));
        return adminOrOperatorAuthEnabled || supportsProductServiceAuth(requestPath);
    }

    private String resolveAdminApiKey() {
        return firstNonBlank(properties.adminApiKey(), platformSecretService.resolveSecret(ADMIN_API_KEY_SECRET));
    }

    private String resolveOperatorApiKey() {
        return firstNonBlank(properties.operatorApiKey(), platformSecretService.resolveSecret(OPERATOR_API_KEY_SECRET));
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

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
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
}
