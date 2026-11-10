package com.ai.fabric.platform.backend.deployment.setup;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/setup-checks")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentSetupCheckController {
    private final DeploymentSetupCheckService checks;
    public DeploymentSetupCheckController(DeploymentSetupCheckService checks) { this.checks = checks; }
    @GetMapping
    public SetupCheckContracts.Summary get(@PathVariable String deploymentId) { return checks.inspect(deploymentId); }
    @PostMapping
    public SetupCheckContracts.Summary run(@PathVariable String deploymentId,
            @RequestBody(required = false) SetupCheckContracts.Request request) {
        return checks.run(deploymentId, request == null || request.mode() == null ? SetupCheckContracts.Mode.ALL : request.mode());
    }
}
