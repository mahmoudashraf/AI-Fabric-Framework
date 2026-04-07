package com.ai.fabric.runtime.web.admin;

import com.ai.fabric.runtime.admin.RuntimeConnectorAdminProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/connector")
@RequiredArgsConstructor
public class RuntimeConnectorAdminProxyController {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConnectorAdminProxyController.class);

    private final RuntimeConnectorAdminProxyService proxyService;

    @Value("${app.admin.api-key:}")
    private String adminApiKey;

    @Value("${app.admin.api-key-header:X-ADMIN-API-KEY}")
    private String adminApiKeyHeader;

    @GetMapping("/overview")
    public ResponseEntity<String> overview(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/overview remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        return toResponse(proxyService.forwardGet("/api/admin/overview"));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/health remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        return toResponse(proxyService.forwardGet("/actuator/health"));
    }

    @GetMapping("/actions/overview")
    public ResponseEntity<String> actionsOverview(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/actions/overview remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        return toResponse(proxyService.forwardGet("/api/admin/actions/overview"));
    }

    @GetMapping("/config")
    public ResponseEntity<String> config(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/config remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        return toResponse(proxyService.forwardGet("/api/admin/overview"));
    }

    @GetMapping("/logs")
    public ResponseEntity<String> logs(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/logs remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        return toResponse(proxyService.forwardGet("/actuator/logfile"));
    }

    @GetMapping("/actions/{actionId}")
    public ResponseEntity<String> action(@PathVariable String actionId, HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/actions/{} remoteAddr={}",
                actionId,
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        if (!StringUtils.hasText(actionId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"message\":\"actionId is required\"}");
        }
        String encodedActionId = UriUtils.encodePathSegment(actionId.trim(), StandardCharsets.UTF_8);
        return toResponse(proxyService.forwardGet("/api/admin/actions/" + encodedActionId));
    }

    @GetMapping({"/proxy", "/proxy/{*proxyPath}"})
    public ResponseEntity<String> proxy(@PathVariable(name = "proxyPath", required = false) String proxyPath,
                                        @RequestParam MultiValueMap<String, String> queryParams,
                                        HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/connector/proxy remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return unauthorized();
        }
        String upstreamPath = normalizeProxyPath(proxyPath, queryParams);
        if (upstreamPath == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"message\":\"proxyPath must target /api/admin/** or /actuator/**\"}");
        }
        return toResponse(proxyService.forwardGet(upstreamPath));
    }

    private String normalizeProxyPath(String proxyPath, MultiValueMap<String, String> queryParams) {
        String normalized = StringUtils.hasText(proxyPath) ? proxyPath.trim() : null;
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!(normalized.startsWith("/api/admin/") || normalized.startsWith("/actuator/"))) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance().path(normalized);
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.queryParams(queryParams);
        }
        return builder.build(true).toUriString();
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"success\":false,\"message\":\"Unauthorized\"}");
    }

    private ResponseEntity<String> toResponse(RuntimeConnectorAdminProxyService.ProxyResponse response) {
        HttpStatus status = HttpStatus.resolve(response != null ? response.status() : 503);
        if (status == null) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        }
        MediaType contentType = MediaType.APPLICATION_JSON;
        if (response != null && StringUtils.hasText(response.contentType())) {
            try {
                contentType = MediaType.parseMediaType(response.contentType());
            } catch (Exception ignored) {
                contentType = MediaType.APPLICATION_JSON;
            }
        }
        String body = response != null ? response.body() : null;
        if (!StringUtils.hasText(body) && status.isError()) {
            body = "{\"success\":false,\"message\":\"Connector admin proxy failed.\"}";
        }
        return ResponseEntity.status(status)
            .contentType(contentType)
            .body(body);
    }
}
