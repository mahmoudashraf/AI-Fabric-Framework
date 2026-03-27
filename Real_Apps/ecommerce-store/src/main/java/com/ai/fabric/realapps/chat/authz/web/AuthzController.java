package com.ai.fabric.realapps.chat.authz.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Demo authorization API for AI Fabric Runtime product deployments.
 *
 * <p>In production, customers should replace this with their real authorization service.
 * This demo implementation is intentionally simple: it grants when {@code userId} is present.</p>
 *
 * <p>Contract: {@code POST /api/authz/check} -> {@code {granted: boolean, reason: string}}</p>
 */
@RestController
@RequestMapping("/api/authz")
public class AuthzController {

    @PostMapping(value = "/check", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthzCheckResponse> check(@RequestBody(required = false) AuthzCheckRequest request) {
        if (request == null || !StringUtils.hasText(request.userId())) {
            return ResponseEntity.ok(new AuthzCheckResponse(false, "MISSING_USER", "demo-v1"));
        }

        // Demo behavior: allow all authenticated/identified callers.
        return ResponseEntity.ok(new AuthzCheckResponse(true, "OK", "demo-v1"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthzCheckRequest(
        String requestId,
        String userId,
        String sessionId,
        String resourceId,
        String operationType,
        Map<String, Object> metadata,
        Map<String, Object> userAttributes
    ) { }

    public record AuthzCheckResponse(
        boolean granted,
        String reason,
        String policyVersion
    ) { }
}

