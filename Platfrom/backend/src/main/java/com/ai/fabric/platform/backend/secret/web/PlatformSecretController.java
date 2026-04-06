package com.ai.fabric.platform.backend.secret.web;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.model.PlatformDeploymentOverrideSecretSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.model.UpdatePlatformSecretRequest;
import com.ai.fabric.platform.backend.secret.model.UpsertPlatformDeploymentOverrideSecretRequest;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretOverrideService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/secrets")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class PlatformSecretController {

    private final PlatformSecretService platformSecretService;
    private final DeploymentProviderSecretOverrideService deploymentProviderSecretOverrideService;
    private final PlatformAuditService platformAuditService;

    public PlatformSecretController(PlatformSecretService platformSecretService,
                                    DeploymentProviderSecretOverrideService deploymentProviderSecretOverrideService,
                                    PlatformAuditService platformAuditService) {
        this.platformSecretService = platformSecretService;
        this.deploymentProviderSecretOverrideService = deploymentProviderSecretOverrideService;
        this.platformAuditService = platformAuditService;
    }

    @GetMapping
    public List<PlatformSecretSummary> listSecrets() {
        return platformSecretService.listSecrets();
    }

    @GetMapping("/deployment-overrides")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<PlatformDeploymentOverrideSecretSummary> listDeploymentOverrideSecrets() {
        return deploymentProviderSecretOverrideService.listOverrideSecrets();
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<PlatformAuditEventSummary> listSecretAuditEvents() {
        return platformAuditService.listRecentEventsForTargetType("PLATFORM_SECRET", 200);
    }

    @GetMapping("/{name}/audit-events")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<PlatformAuditEventSummary> listSecretAuditEventsForName(@PathVariable String name) {
        return platformAuditService.listRecentEventsForTarget("PLATFORM_SECRET", name, 50);
    }

    @PutMapping("/{name}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformSecretSummary updateSecret(@PathVariable String name,
                                              @RequestBody UpdatePlatformSecretRequest request) {
        return platformSecretService.updateSecret(name, request.value());
    }

    @PutMapping("/deployment-overrides/{name}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformDeploymentOverrideSecretSummary upsertDeploymentOverrideSecret(@PathVariable String name,
                                                                                  @RequestBody UpsertPlatformDeploymentOverrideSecretRequest request) {
        return deploymentProviderSecretOverrideService.upsertOverrideSecret(name, request);
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformSecretSummary clearSecret(@PathVariable String name) {
        return platformSecretService.clearSecret(name);
    }

    @DeleteMapping("/deployment-overrides/{name}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformDeploymentOverrideSecretSummary clearDeploymentOverrideSecret(@PathVariable String name) {
        return deploymentProviderSecretOverrideService.clearOverrideSecret(name);
    }
}
