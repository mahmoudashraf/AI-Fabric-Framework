package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimePublicTokenService;
import com.ai.fabric.runtime.web.dto.PublicRuntimeSessionBootstrapRequest;
import com.ai.fabric.runtime.web.dto.PublicRuntimeSessionBootstrapResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/chat")
public class PublicRuntimeSessionController {

    private final RuntimePublicTokenService runtimePublicTokenService;

    public PublicRuntimeSessionController(RuntimePublicTokenService runtimePublicTokenService) {
        this.runtimePublicTokenService = runtimePublicTokenService;
    }

    @PostMapping("/session")
    public ResponseEntity<PublicRuntimeSessionBootstrapResponse> bootstrapSession(
        @RequestBody(required = false) PublicRuntimeSessionBootstrapRequest request,
        jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        runtimePublicTokenService.authorizeAnonymousBootstrap(servletRequest);
        rejectUnexpectedFields(request);
        RuntimePublicTokenService.IssuedPublicRuntimeToken issued = runtimePublicTokenService.issueAnonymousToken();
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(new PublicRuntimeSessionBootstrapResponse(
                true,
                runtimePublicTokenService.tokenScheme(),
                issued.token(),
                issued.authContext().getAuthMode().name(),
                issued.authContext().getSubjectType().name(),
                issued.authContext().getSessionId(),
                issued.authContext().getDeploymentId(),
                issued.authContext().getCustomerId(),
                issued.authContext().getTenantId(),
                issued.authContext().getGrantedScopes(),
                issued.authContext().getAudiences(),
                issued.authContext().getExpiresAt() == null ? null : issued.authContext().getExpiresAt().toString()
            ));
    }

    private void rejectUnexpectedFields(PublicRuntimeSessionBootstrapRequest request) {
        if (request == null || request.getUnexpectedFields().isEmpty()) {
            return;
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Unexpected request fields are not allowed on public runtime bootstrap: "
                + String.join(", ", request.getUnexpectedFields().keySet())
                + ". Runtime issues anonymous session identity."
        );
    }
}
