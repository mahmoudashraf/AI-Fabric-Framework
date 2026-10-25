package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** A dedicated credential may only enter this route family, never an operator or customer API. */
public final class LifecycleMachineFilter extends OncePerRequestFilter {
    public static final String PREFIX = "/internal/v1/deployment-lifecycle/";
    public static final String AUTHORITY = "SERVICE_DEPLOYMENT_LIFECYCLE";
    private final LifecycleGatewayProperties properties;
    public LifecycleMachineFilter(LifecycleGatewayProperties properties) { this.properties = properties; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        List<String> headers = Collections.list(request.getHeaders("Authorization"));
        String key = properties.getApiKey();
        boolean valid = properties.isEnabled() && key != null && key.matches("[!-~]{32,512}")
            && headers.size() == 1 && MessageDigest.isEqual(("Bearer " + key).getBytes(StandardCharsets.UTF_8),
                headers.getFirst().getBytes(StandardCharsets.UTF_8));
        if (!valid) { response.sendError(401); return; }
        HttpServletRequest bounded = request;
        if ("POST".equals(request.getMethod())) {
            if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].equalsIgnoreCase("application/json")
                || request.getHeader("Content-Encoding") != null) { response.sendError(415); return; }
            if (request.getContentLengthLong() > 8192) { response.sendError(413); return; }
            byte[] body = request.getInputStream().readNBytes(8193);
            if (body.length > 8192) { response.sendError(413); return; }
            bounded = new CachedRequest(request, body);
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("deployment-operations-service", null,
            List.of(new SimpleGrantedAuthority(AUTHORITY))));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(bounded, response); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private CachedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read() { return input.read(); }
                public boolean isFinished() { return input.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous internal commands only."); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
