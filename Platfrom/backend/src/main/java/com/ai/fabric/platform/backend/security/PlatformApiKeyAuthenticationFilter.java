package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class PlatformApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformApiKeyAuthenticationFilter.class);

    private final PlatformAuthProperties properties;

    public PlatformApiKeyAuthenticationFilter(PlatformAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled()) {
            SecurityContextHolder.getContext().setAuthentication(authenticationFor(
                new PlatformPrincipal("platform-bypass", PlatformRole.PLATFORM_ADMIN)
            ));
            filterChain.doFilter(request, response);
            return;
        }

        String presentedKey = request.getHeader(properties.headerName());
        if (!StringUtils.hasText(presentedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        PlatformRole role = matchRole(presentedKey);
        if (role == null) {
            log.warn(
                "Platform auth rejected request: method={}, path={}, header={}",
                request.getMethod(),
                request.getRequestURI(),
                properties.headerName()
            );
            filterChain.doFilter(request, response);
            return;
        }

        PlatformPrincipal principal = new PlatformPrincipal(
            role == PlatformRole.PLATFORM_ADMIN ? "platform-admin" : "platform-operator",
            role
        );
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(principal));
        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authenticationFor(PlatformPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
    }

    private PlatformRole matchRole(String presentedKey) {
        if (matches(properties.adminApiKey(), presentedKey)) {
            return PlatformRole.PLATFORM_ADMIN;
        }
        if (matches(properties.operatorApiKey(), presentedKey)) {
            return PlatformRole.PLATFORM_OPERATOR;
        }
        return null;
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
