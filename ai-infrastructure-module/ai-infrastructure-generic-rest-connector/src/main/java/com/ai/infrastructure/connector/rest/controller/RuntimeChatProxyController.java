package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.runtime.RuntimeProxyClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional proxy endpoints that forward Runtime chat API calls through the connector.
 *
 * <p>This exists for demo/UI setups that want a single base URL for:
 * actions ({@code /actions/execute}) and runtime chat ({@code /api/chat/*}).</p>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rest-connector.runtime-proxy", name = "enabled", havingValue = "true")
public class RuntimeChatProxyController {

    private final RuntimeProxyClient runtimeProxyClient;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> forward(HttpServletRequest request, @RequestBody(required = false) String body) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(
                "{\"success\":false,\"message\":\"Invalid request.\"}"
            );
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            path = "/api/chat";
        }
        String query = request.getQueryString();
        String pathAndQuery = StringUtils.hasText(query) ? (path + "?" + query) : path;

        // Avoid sending a body on GET; keep runtime behavior predictable.
        String jsonBody = "GET".equalsIgnoreCase(method) ? null : body;
        RuntimeProxyClient.ProxyResponse response = runtimeProxyClient.forward(method, pathAndQuery, jsonBody);

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

