package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.runtime.RuntimeProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional proxy endpoint for runtime maintenance APIs.
 *
 * <p>Currently supports forwarding {@code POST /api/admin/migration/clear} to the configured runtime.
 * Enabled via {@code rest-connector.runtime-proxy.enabled=true}.</p>
 */
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rest-connector.runtime-proxy", name = "enabled", havingValue = "true")
public class RuntimeMigrationAdminProxyController {

    private final RuntimeProxyClient runtimeProxyClient;

    @PostMapping("/clear")
    public ResponseEntity<String> clear(HttpServletRequest request, @RequestBody(required = false) String body) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(
                "{\"success\":false,\"message\":\"Invalid request.\"}"
            );
        }

        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            path = "/api/admin/migration/clear";
        }
        String query = request.getQueryString();
        String pathAndQuery = StringUtils.hasText(query) ? (path + "?" + query) : path;

        RuntimeProxyClient.ProxyResponse response = runtimeProxyClient.forward("POST", pathAndQuery, body);

        HttpStatus status = HttpStatus.resolve(response != null ? response.status() : 503);
        if (status == null) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        }

        String contentType = response != null ? response.contentType() : null;
        MediaType mediaType = MediaType.APPLICATION_JSON;
        if (StringUtils.hasText(contentType)) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_JSON;
            }
        }

        return ResponseEntity.status(status)
            .contentType(mediaType)
            .body(response != null ? response.body() : null);
    }
}

