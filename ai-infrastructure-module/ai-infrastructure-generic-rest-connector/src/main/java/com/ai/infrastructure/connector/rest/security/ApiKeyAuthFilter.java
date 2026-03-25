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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(RestRoutingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            return true;
        }
        return !path.startsWith("/actions/execute");
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
            reject(response, "Inbound auth is not configured.");
            return;
        }

        String header = apiKey.getHeader();
        String expected = apiKey.getValue();
        if (!StringUtils.hasText(header) || !StringUtils.hasText(expected)) {
            reject(response, "Inbound auth is not configured.");
            return;
        }

        String actual = request.getHeader(header.trim());
        if (!StringUtils.hasText(actual) || !expected.trim().equals(actual.trim())) {
            reject(response, "Unauthorized.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        if (response == null) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ActionResultDto body = ActionResultDto.failure(ERROR_UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}

