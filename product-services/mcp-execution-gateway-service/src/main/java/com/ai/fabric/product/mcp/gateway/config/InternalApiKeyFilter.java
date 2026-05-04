package com.ai.fabric.product.mcp.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final McpGatewayProperties properties;

    public InternalApiKeyFilter(McpGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return path == null || !path.startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.internalApiKeyConfigured()) {
            writeError(response, 503, "MCP gateway internal API key is not configured.");
            return;
        }
        String presented = request.getHeader(properties.internalApiKeyHeader());
        if (!StringUtils.hasText(presented) || !constantTimeEquals(properties.internalApiKey(), presented.trim())) {
            writeError(response, 401, "Invalid MCP gateway internal API key.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int diff = left.length ^ right.length;
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            byte l = i < left.length ? left[i] : 0;
            byte r = i < right.length ? right[i] : 0;
            diff |= l ^ r;
        }
        return diff == 0;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
