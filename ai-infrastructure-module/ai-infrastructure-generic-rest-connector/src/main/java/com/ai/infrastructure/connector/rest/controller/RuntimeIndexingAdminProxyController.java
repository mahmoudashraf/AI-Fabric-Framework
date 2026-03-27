package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.runtime.RuntimeProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional admin proxy endpoints that forward runtime indexing inspection calls.
 *
 * <p>These are intended for demos and debugging only.</p>
 */
@RestController
@RequestMapping("/api/admin/indexing")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rest-connector.runtime-proxy", name = "enabled", havingValue = "true")
public class RuntimeIndexingAdminProxyController {

    private final RuntimeProxyClient runtimeProxyClient;

    @GetMapping("/overview")
    public ResponseEntity<String> overview(HttpServletRequest request) {
        return forwardGet(request);
    }

    @GetMapping("/vectors")
    public ResponseEntity<String> vectors(HttpServletRequest request) {
        return forwardGet(request);
    }

    private ResponseEntity<String> forwardGet(HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        if (!StringUtils.hasText(path)) {
            path = "/api/admin/indexing/overview";
        }
        String query = request != null ? request.getQueryString() : null;
        String pathAndQuery = StringUtils.hasText(query) ? (path + "?" + query) : path;

        RuntimeProxyClient.ProxyResponse response = runtimeProxyClient.forward("GET", pathAndQuery, null);
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

