package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

public class PlatformSessionAuthenticationFilter extends OncePerRequestFilter {

    private final PlatformAuthProperties properties;
    private final PlatformIdentityService platformIdentityService;

    public PlatformSessionAuthenticationFilter(PlatformAuthProperties properties,
                                               PlatformIdentityService platformIdentityService) {
        this.properties = properties;
        this.platformIdentityService = platformIdentityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || !properties.sessionEnabled() || PlatformSecurityContext.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String sessionToken = readCookie(request, properties.sessionCookieName());
        PlatformPrincipal principal = platformIdentityService.authenticateSessionToken(sessionToken);
        if (principal != null) {
            SecurityContextHolder.getContext().setAuthentication(
                PlatformAuthenticationSupport.authenticationFor(principal)
            );
        }
        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}
