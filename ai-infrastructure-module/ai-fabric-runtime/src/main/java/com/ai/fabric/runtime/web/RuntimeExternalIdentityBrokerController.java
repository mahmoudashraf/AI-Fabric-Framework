package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeExternalIdentityBrokerService;
import com.ai.fabric.runtime.auth.RuntimePublicTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/public/auth")
public class RuntimeExternalIdentityBrokerController {

    private final RuntimeExternalIdentityBrokerService brokerService;

    public RuntimeExternalIdentityBrokerController(RuntimeExternalIdentityBrokerService brokerService) {
        this.brokerService = brokerService;
    }

    @PostMapping("/runtime-token")
    public ResponseEntity<RuntimeTokenResponse> runtimeToken(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody RuntimeTokenRequest request,
        HttpServletRequest servletRequest
    ) {
        validateRequest(request);
        var brokered = brokerService.authenticate(servletRequest, authorization);
        RuntimePublicTokenService.IssuedPublicRuntimeToken issued = brokered.issued();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(new RuntimeTokenResponse(
                "loomai-workspace-runtime-token-v1",
                issued.token(),
                "Bearer",
                issued.authContext().getExpiresAt(),
                issued.authContext().getSubjectId(),
                brokered.email(),
                issued.authContext().getGrantedScopes()
            ));
    }

    private void validateRequest(RuntimeTokenRequest request) {
        if (request == null
            || !"loomai-workspace-runtime-token-request-v1".equals(request.schemaVersion())) {
            throw new ResponseStatusException(BAD_REQUEST, "Runtime token request is invalid.");
        }
    }

    public record RuntimeTokenRequest(
        String schemaVersion
    ) {
    }

    public record RuntimeTokenResponse(
        String schemaVersion,
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String subjectId,
        String email,
        java.util.List<String> grantedScopes
    ) {
    }
}
