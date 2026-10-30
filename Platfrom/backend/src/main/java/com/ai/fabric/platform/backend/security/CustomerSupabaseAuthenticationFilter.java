package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Map;

public class CustomerSupabaseAuthenticationFilter extends OncePerRequestFilter {
    private final CustomerSupabaseAuthProperties properties;
    private final CustomerSupabaseIdentityService identities;
    private final ObjectMapper mapper;

    public CustomerSupabaseAuthenticationFilter(CustomerSupabaseAuthProperties properties, CustomerSupabaseIdentityService identities, ObjectMapper mapper) {
        this.properties = properties; this.identities = identities; this.mapper = mapper;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/desk/") || path.startsWith("/api/partners/")
            || path.startsWith("/api/public/") || path.startsWith("/api/merchant/") || path.startsWith("/api/vectorization/runner/")
            || path.equals("/api/platform/auth/customer-config") || path.equals("/api/platform/auth/customer-link")
            || path.equals("/api/platform/auth/customer-onboarding")
            || path.equals("/api/integrations/support/desk/context")
            || path.equals("/api/support/staff/intake")
            || path.equals("/api/platform/auth/login") || path.equals("/api/platform/auth/logout");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!properties.enabled() || header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response); return;
        }
        try {
            var principal = identities.authenticate(header.substring(7).trim());
            SecurityContextHolder.getContext().setAuthentication(PlatformAuthenticationSupport.authenticationFor(principal));
        } catch (ResponseStatusException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(ex.getStatusCode().value()); response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(), Map.of("success", false, "errorCode",
                ex.getStatusCode().value() == 403 ? "CUSTOMER_IDENTITY_NOT_LINKED" : "CUSTOMER_AUTHENTICATION_FAILED",
                "message", ex.getReason() == null ? "Customer authentication failed." : ex.getReason()));
            return;
        }
        chain.doFilter(request, response);
    }
}
