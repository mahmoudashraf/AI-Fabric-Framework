package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.model.PlatformAuthSessionSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthProperties properties;

    public PlatformAuthController(PlatformAuthProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/session")
    public PlatformAuthSessionSummary session() {
        if (!properties.enabled()) {
            return new PlatformAuthSessionSummary(
                false,
                properties.headerName(),
                false,
                null,
                null,
                true,
                true
            );
        }

        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        boolean authenticated = principal != null;
        boolean canManageSecrets = principal != null && principal.role() == PlatformRole.PLATFORM_ADMIN;
        boolean canOperateDeployments = principal != null;
        return new PlatformAuthSessionSummary(
            properties.enabled(),
            properties.headerName(),
            authenticated,
            authenticated ? principal.actorId() : null,
            authenticated ? principal.role().name() : null,
            canManageSecrets,
            canOperateDeployments
        );
    }
}
