package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicCreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentStatusResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.PublicProvisioningApiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/deployments")
@PreAuthorize("hasRole('PUBLIC_API_CLIENT')")
public class PublicProvisioningApiController {

    private final PublicProvisioningApiService publicProvisioningApiService;

    public PublicProvisioningApiController(PublicProvisioningApiService publicProvisioningApiService) {
        this.publicProvisioningApiService = publicProvisioningApiService;
    }

    @PostMapping
    public ResponseEntity<PublicDeploymentSummary> createDeployment(@Valid @RequestBody PublicCreateDeploymentRequest request) {
        PublicDeploymentSummary response = publicProvisioningApiService.createDeployment(request);
        return response.created()
            ? ResponseEntity.status(HttpStatus.CREATED).body(response)
            : ResponseEntity.ok(response);
    }

    @GetMapping("/{deploymentId}")
    public PublicDeploymentSummary getDeployment(@PathVariable String deploymentId) {
        return publicProvisioningApiService.getDeployment(deploymentId);
    }

    @GetMapping("/{deploymentId}/status")
    public PublicDeploymentStatusResponse getStatus(@PathVariable String deploymentId) {
        return publicProvisioningApiService.getDeploymentStatus(deploymentId);
    }

    @PostMapping("/{deploymentId}/apply")
    public ResponseEntity<PublicApplyDeploymentResponse> apply(@PathVariable String deploymentId,
                                                               @RequestBody(required = false) PublicApplyDeploymentRequest request) {
        PublicApplyDeploymentResponse response = publicProvisioningApiService.applyDeployment(
            deploymentId,
            request == null ? new PublicApplyDeploymentRequest(null) : request
        );
        return response.idempotentReplay()
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{deploymentId}/credentials")
    public PublicDeploymentCredentialsResponse credentials(@PathVariable String deploymentId) {
        return publicProvisioningApiService.getDeploymentCredentials(deploymentId);
    }
}
