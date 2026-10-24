package com.loomai.operations.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.web.OperationsErrorHandler.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationsApiFilter extends OncePerRequestFilter {

    private static final Set<String> HEALTH_PATHS = Set.of(
        "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness");
    private final byte[] expectedKeyHash;
    private final int maxBodyBytes;
    private final ObjectMapper mapper;

    public OperationsApiFilter(OperationsProperties properties, ObjectMapper mapper) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.length() < 32 || apiKey.length() > 512
            || apiKey.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalStateException("DEPLOYMENT_OPERATIONS_API_KEY must be 32-512 printable non-space ASCII characters.");
        }
        this.expectedKeyHash = sha256(apiKey);
        this.maxBodyBytes = properties.maxBodyBytes();
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (request.getMethod().equals("GET") && HEALTH_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        var authorization = Collections.list(request.getHeaders("Authorization"));
        String header = authorization.size() == 1 ? authorization.getFirst() : null;
        String supplied = header != null && header.length() <= 1024 && header.startsWith("Bearer ")
            ? header.substring(7) : "";
        if (!MessageDigest.isEqual(expectedKeyHash, sha256(supplied))) {
            response.setHeader("WWW-Authenticate", "Bearer");
            reject(response, 401, "AUTHENTICATION_REQUIRED", "Service authentication is required.");
            return;
        }
        if (request.getRequestURI().startsWith("/internal/") && request.getMethod().equals("POST")) {
            if (request.getHeader("Content-Encoding") != null) {
                reject(response, 415, "ENCODING_UNSUPPORTED", "Encoded request bodies are not supported.");
                return;
            }
            MediaType contentType;
            try {
                contentType = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            } catch (IllegalArgumentException exception) {
                contentType = null;
            }
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                reject(response, 415, "CONTENT_TYPE_UNSUPPORTED", "Use application/json.");
                return;
            }
            if (request.getContentLengthLong() > maxBodyBytes) {
                reject(response, 413, "REQUEST_TOO_LARGE", "Request body exceeds the configured limit.");
                return;
            }
            byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
            if (body.length > maxBodyBytes) {
                reject(response, 413, "REQUEST_TOO_LARGE", "Request body exceeds the configured limit.");
                return;
            }
            chain.doFilter(new BoundedBodyRequest(request, body), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(code, message));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required.", exception);
        }
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BoundedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous request body reads are not supported.");
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
