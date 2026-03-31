package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformPublicApiProperties;
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
import java.util.Map;

public class PlatformPublicApiAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformPublicApiAuthenticationFilter.class);

    private final PlatformPublicApiProperties properties;

    public PlatformPublicApiAuthenticationFilter(PlatformPublicApiProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || PlatformSecurityContext.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = request.getHeader(properties.clientIdHeaderName());
        String presentedKey = request.getHeader(properties.apiKeyHeaderName());
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(presentedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, String> clients = properties.clients() == null ? Map.of() : properties.clients();
        String configuredKey = clients.get(clientId.trim());
        if (!matches(configuredKey, presentedKey)) {
            log.warn(
                "Public API auth rejected request: clientId={}, method={}, path={}",
                clientId,
                request.getMethod(),
                request.getRequestURI()
            );
            filterChain.doFilter(request, response);
            return;
        }

        PlatformPrincipal principal = new PlatformPrincipal(
            clientId.trim(),
            PlatformRole.PUBLIC_API_CLIENT,
            clientId.trim(),
            "PUBLIC_API_KEY"
        );
        SecurityContextHolder.getContext().setAuthentication(PlatformAuthenticationSupport.authenticationFor(principal));
        filterChain.doFilter(request, response);
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
}
