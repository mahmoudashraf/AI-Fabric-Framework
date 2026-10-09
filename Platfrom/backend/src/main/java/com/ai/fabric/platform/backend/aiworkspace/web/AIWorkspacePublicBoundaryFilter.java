package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceManifestRateLimiter;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceObservability;
import com.ai.fabric.platform.backend.aiworkspace.service.PublicAIWorkspaceManifestService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AIWorkspacePublicBoundaryFilter extends OncePerRequestFilter {

    private static final Pattern MANIFEST = Pattern.compile(
        "^/api/public/ai-workspace/installations/(awi_pub_[a-f0-9]{32})/manifest$");

    private final PublicAIWorkspaceManifestService manifestService;
    private final AIWorkspaceManifestRateLimiter rateLimiter;
    private final AIWorkspaceObservability observability;

    public AIWorkspacePublicBoundaryFilter(PublicAIWorkspaceManifestService manifestService,
                                           AIWorkspaceManifestRateLimiter rateLimiter,
                                           AIWorkspaceObservability observability) {
        this.manifestService = manifestService;
        this.rateLimiter = rateLimiter;
        this.observability = observability;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String surface = surface(requestUri);
        if (surface == null) {
            filterChain.doFilter(request, response);
            return;
        }
        long startedAt = System.nanoTime();
        Matcher matcher = MANIFEST.matcher(requestUri);
        if (!matcher.matches()) {
            projectPublicAssetCors(response);
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                observability.recordPublicRequest(surface, "success", startedAt);
                return;
            }
            observeChain(request, response, filterChain, surface, startedAt, null);
            return;
        }
        String installationId = matcher.group(1);
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String remote = clientAddress(request);
        if (!rateLimiter.allow("manifest-client|" + fingerprint(remote))) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            observability.recordPublicRequest(surface, "rate_limited", startedAt);
            return;
        }
        if (!rateLimiter.allow("manifest-scope|"
            + fingerprint(remote + "\n" + installationId + "\n" + String.valueOf(origin)))) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            observability.recordPublicRequest(surface, "rate_limited", startedAt);
            return;
        }
        boolean originAllowed = manifestService.isOriginAllowed(installationId, origin);
        if (originAllowed) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.setHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS");
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
            response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(originAllowed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_NOT_FOUND);
            observability.recordPublicRequest(surface, originAllowed ? "success" : "origin_rejected", startedAt);
            return;
        }
        observeChain(request, response, filterChain, surface, startedAt, originAllowed ? null : "origin_rejected");
    }

    private void projectPublicAssetCors(HttpServletResponse response) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, HEAD, OPTIONS");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
    }

    private void observeChain(HttpServletRequest request,
                              HttpServletResponse response,
                              FilterChain filterChain,
                              String surface,
                              long startedAt,
                              String outcomeOverride) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            observability.recordPublicRequest(
                surface, outcomeOverride == null ? outcome(response.getStatus()) : outcomeOverride, startedAt);
        }
    }

    private String surface(String requestUri) {
        if ("/api/public/ai-workspace/install.js".equals(requestUri)) return "installer";
        if (MANIFEST.matcher(requestUri).matches()) return "manifest";
        if (requestUri.startsWith("/api/public/ai-workspace/assets/")) return "asset";
        return null;
    }

    private String outcome(int status) {
        if (status == HttpServletResponse.SC_NOT_MODIFIED) return "not_modified";
        if (status >= 200 && status < 300) return "success";
        if (status == HttpServletResponse.SC_NOT_FOUND) return "unavailable";
        if (status == 429) return "rate_limited";
        if (status >= 400 && status < 500) return "invalid_request";
        return "error";
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String value;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            value = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
        } else {
            value = request.getRemoteAddr();
        }
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
