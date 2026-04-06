package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimePublicTokenService;
import com.ai.fabric.runtime.web.dto.PublicRuntimeSessionBootstrapRequest;
import com.ai.fabric.runtime.web.dto.PublicRuntimeSessionBootstrapResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/chat")
public class PublicRuntimeSessionController {

    private final RuntimePublicTokenService runtimePublicTokenService;

    public PublicRuntimeSessionController(RuntimePublicTokenService runtimePublicTokenService) {
        this.runtimePublicTokenService = runtimePublicTokenService;
    }

    @PostMapping("/session")
    public ResponseEntity<PublicRuntimeSessionBootstrapResponse> bootstrapSession(
        @RequestBody(required = false) PublicRuntimeSessionBootstrapRequest request
    ) {
        RuntimePublicTokenService.IssuedPublicRuntimeToken issued = runtimePublicTokenService.issueAnonymousToken(
            request == null ? null : request.sessionId()
        );
        return ResponseEntity.ok(new PublicRuntimeSessionBootstrapResponse(
            true,
            runtimePublicTokenService.tokenScheme(),
            issued.token(),
            issued.authContext().getAuthMode().name(),
            issued.authContext().getSubjectType().name(),
            issued.authContext().getSessionId(),
            issued.authContext().getExpiresAt() == null ? null : issued.authContext().getExpiresAt().toString()
        ));
    }
}
