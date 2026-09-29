package com.loomai.demo.dealership.security;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final DealershipDemoProperties properties;

    public InternalApiKeyFilter(DealershipDemoProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configured = properties.getInternal().getApiKey();
        String headerName = properties.getInternal().getApiKeyHeader();
        String supplied = StringUtils.hasText(headerName) ? request.getHeader(headerName.trim()) : null;
        if (!StringUtils.hasText(configured)
            || !StringUtils.hasText(supplied)
            || !MessageDigest.isEqual(
                configured.trim().getBytes(StandardCharsets.UTF_8),
                supplied.trim().getBytes(StandardCharsets.UTF_8)
            )) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"errorCode\":\"INTERNAL_AUTH_REQUIRED\",\"message\":\"Internal authentication is required.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
