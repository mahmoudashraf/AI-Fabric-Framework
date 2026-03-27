package com.ai.infrastructure.connector.rest.security;

import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;
    private final com.ai.infrastructure.connector.rest.config.CorsConfiguration.CorsProperties corsProperties;

    public ApiKeyAuthFilter(RestRoutingConfig config,
                            ObjectMapper objectMapper,
                            com.ai.infrastructure.connector.rest.config.CorsConfiguration.CorsProperties corsProperties) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.corsProperties = corsProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            return true;
        }
        // Protect action execution, admin verification endpoints, and (optional) runtime proxy endpoints.
        return !(path.startsWith("/actions/execute")
            || path.startsWith("/api/admin")
            || path.startsWith("/api/ai/data-sync"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RestRoutingConfig.Connector connector = config != null ? config.getConnector() : null;
        RestRoutingConfig.InboundAuth inbound = connector != null ? connector.getInboundAuth() : null;
        if (inbound == null || inbound.isAllowUnauthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        RestRoutingConfig.ApiKey apiKey = inbound.getApiKey();
        if (apiKey == null || !apiKey.isEnabled()) {
            reject(request, response, "Inbound auth is not configured.");
            return;
        }

        String header = apiKey.getHeader();
        String expected = apiKey.getValue();
        if (!StringUtils.hasText(header) || !StringUtils.hasText(expected)) {
            reject(request, response, "Inbound auth is not configured.");
            return;
        }

        String actual = request.getHeader(header.trim());
        if (!StringUtils.hasText(actual) || !constantTimeEquals(expected.trim(), actual.trim())) {
            reject(request, response, "Unauthorized.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        if (request == null || response == null) {
            return;
        }
        maybeApplyCorsHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ActionResultDto body = ActionResultDto.failure(ERROR_UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    private void maybeApplyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (request == null || response == null || corsProperties == null) {
            return;
        }
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            return;
        }

        if (!isOriginAllowed(origin.trim(), corsProperties.allowedOrigins(), corsProperties.allowedOriginPatterns())) {
            return;
        }

        response.setHeader("Access-Control-Allow-Origin", origin.trim());
        // Ensure caches vary by origin so different allowed origins don't leak.
        response.addHeader("Vary", "Origin");
    }

    private boolean isOriginAllowed(String origin, List<String> allowedOrigins, List<String> allowedOriginPatterns) {
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        List<String> origins = allowedOrigins != null ? allowedOrigins : List.of();
        for (String allowed : origins) {
            if (!StringUtils.hasText(allowed)) {
                continue;
            }
            String value = allowed.trim();
            if ("*".equals(value) || value.equalsIgnoreCase(origin)) {
                return true;
            }
        }
        List<String> patterns = allowedOriginPatterns != null ? allowedOriginPatterns : List.of();
        for (String pattern : patterns) {
            if (!StringUtils.hasText(pattern)) {
                continue;
            }
            String p = pattern.trim();
            if ("*".equals(p) || PatternMatchUtils.simpleMatch(p, origin)) {
                return true;
            }
        }
        return false;
    }
}
