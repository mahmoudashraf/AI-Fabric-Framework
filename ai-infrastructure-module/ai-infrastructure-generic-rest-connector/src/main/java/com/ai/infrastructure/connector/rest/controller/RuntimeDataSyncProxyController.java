package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.runtime.RuntimeProxyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional "alias" endpoints that forward runtime-managed indexing calls (Data Sync push API)
 * through the connector.
 *
 * <p>Enable via {@code rest-connector.runtime-proxy.enabled=true} and configure the runtime base URL
 * via {@code rest-connector.runtime-proxy.base-url}.</p>
 */
@RestController
@RequestMapping("/api/ai/data-sync")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rest-connector.runtime-proxy", name = "enabled", havingValue = "true")
public class RuntimeDataSyncProxyController {

    private final RuntimeProxyClient runtimeProxyClient;

    @GetMapping("/vector-spaces")
    public ResponseEntity<String> vectorSpaces() {
        return forward("GET", "/api/ai/data-sync/vector-spaces", null);
    }

    @PostMapping("/upsert")
    public ResponseEntity<String> upsert(@RequestBody(required = false) String body) {
        return forward("POST", "/api/ai/data-sync/upsert", body);
    }

    @PostMapping("/delete")
    public ResponseEntity<String> delete(@RequestBody(required = false) String body) {
        return forward("POST", "/api/ai/data-sync/delete", body);
    }

    @PostMapping("/batch")
    public ResponseEntity<String> batch(@RequestBody(required = false) String body) {
        return forward("POST", "/api/ai/data-sync/batch", body);
    }

    private ResponseEntity<String> forward(String method, String path, String body) {
        RuntimeProxyClient.ProxyResponse response = runtimeProxyClient.forward(method, path, body);
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

