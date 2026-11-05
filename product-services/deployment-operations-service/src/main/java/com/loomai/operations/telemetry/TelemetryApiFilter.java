package com.loomai.operations.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Explicit delegated boundary, not a second auto-registered servlet filter. */
@Component
public class TelemetryApiFilter {
    public static final String PREFIX = "/internal/v1/telemetry";
    public static final int MAX_BODY_BYTES = 256 * 1024;
    static final String SOURCE_ATTRIBUTE = TelemetryApiFilter.class.getName() + ".source";
    static final String BODY_ATTRIBUTE = TelemetryApiFilter.class.getName() + ".body";
    static final String CREDENTIAL_ATTRIBUTE = TelemetryApiFilter.class.getName() + ".credential";
    private final TelemetryRegistry registry;
    private final ObjectMapper mapper;

    public TelemetryApiFilter(TelemetryRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    public static boolean owns(String path) {
        return path.equals(PREFIX) || path.startsWith(PREFIX + "/");
    }

    public void filter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!registry.enabled()) {
            reject(response, 503, "TELEMETRY_DISABLED", "Telemetry is disabled.");
            return;
        }
        var headers = Collections.list(request.getHeaders("Authorization"));
        String header = headers.size() == 1 ? headers.getFirst() : "";
        var credential = registry.authenticate(header.length() <= 1024 && header.startsWith("Bearer ")
            ? header.substring(7) : "");
        if (credential == null) {
            response.setHeader("WWW-Authenticate", "Bearer");
            reject(response, 401, "AUTHENTICATION_REQUIRED", "Telemetry authentication is required.");
            return;
        }
        String path = request.getRequestURI();
        boolean ingest = request.getMethod().equals("POST") && path.equals(PREFIX + "/samples");
        boolean read = request.getMethod().equals("GET") && (path.equals(PREFIX + "/servers")
            || path.matches(PREFIX + "/servers/" + TelemetryContracts.ID + "(?:/history)?"));
        if (!ingest && !read) {
            reject(response, 404, "TELEMETRY_ROUTE_NOT_FOUND", "Telemetry route is not available.");
            return;
        }
        if (ingest == credential.reader()) {
            reject(response, 403, "TELEMETRY_SCOPE_DENIED", "Credential does not permit this telemetry operation.");
            return;
        }
        if (request.getQueryString() != null && request.getQueryString().length() > 1024) {
            reject(response, 400, "TELEMETRY_QUERY_INVALID", "Telemetry query is invalid.");
            return;
        }
        if (ingest) {
            MediaType type;
            try {
                type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            } catch (IllegalArgumentException exception) {
                type = null;
            }
            if (request.getHeader("Content-Encoding") != null || type == null
                || !MediaType.APPLICATION_JSON.isCompatibleWith(type)) {
                reject(response, 415, "TELEMETRY_MEDIA_UNSUPPORTED", "Use unencoded application/json.");
                return;
            }
            if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                reject(response, 413, "TELEMETRY_REQUEST_TOO_LARGE", "Telemetry batch exceeds 256 KiB.");
                return;
            }
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                reject(response, 413, "TELEMETRY_REQUEST_TOO_LARGE", "Telemetry batch exceeds 256 KiB.");
                return;
            }
            request.setAttribute(SOURCE_ATTRIBUTE, credential.serverId());
            request.setAttribute(BODY_ATTRIBUTE, body);
        }
        request.setAttribute(CREDENTIAL_ATTRIBUTE, credential);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new TelemetryErrorHandler.ApiError(code, message));
    }
}
