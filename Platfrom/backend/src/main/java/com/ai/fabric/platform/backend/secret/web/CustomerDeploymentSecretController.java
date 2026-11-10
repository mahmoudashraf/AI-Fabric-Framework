package com.ai.fabric.platform.backend.secret.web;

import com.ai.fabric.platform.backend.secret.model.DeploymentSecretContracts;
import com.ai.fabric.platform.backend.secret.service.CustomerDeploymentSecretService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/secrets")
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class CustomerDeploymentSecretController {
    private final CustomerDeploymentSecretService secrets;
    public CustomerDeploymentSecretController(CustomerDeploymentSecretService secrets) { this.secrets = secrets; }

    @GetMapping
    public DeploymentSecretContracts.Catalogue list(@PathVariable String deploymentId) { return secrets.list(deploymentId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<DeploymentSecretContracts.Secret> create(@PathVariable String deploymentId, @RequestBody DeploymentSecretContracts.Create request) {
        return secrets.create(deploymentId, request);
    }

    @PutMapping("/{name}")
    public DeploymentSecretContracts.Secret rotate(@PathVariable String deploymentId, @PathVariable String name, @RequestBody DeploymentSecretContracts.Rotate request) {
        return secrets.rotate(deploymentId, name, request);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String deploymentId, @PathVariable String name) { secrets.delete(deploymentId, name); }
}
