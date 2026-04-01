package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.DeploymentGitHubVerificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/releases/{releaseId}/github-actions")
public class DeploymentGitHubVerificationController {

    private final DeploymentGitHubVerificationService deploymentGitHubVerificationService;

    public DeploymentGitHubVerificationController(DeploymentGitHubVerificationService deploymentGitHubVerificationService) {
        this.deploymentGitHubVerificationService = deploymentGitHubVerificationService;
    }

    @GetMapping("/context.json")
    public ResponseEntity<String> getContext(@PathVariable String deploymentId,
                                             @PathVariable String releaseId,
                                             @RequestParam(required = false) String profile,
                                             @RequestParam(required = false) Boolean verifyWrite,
                                             @RequestParam(name = "expires", required = false) Long expires,
                                             @RequestParam(name = "sig", required = false) String signature) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                deploymentGitHubVerificationService.readSignedContext(
                    deploymentId,
                    releaseId,
                    profile,
                    verifyWrite,
                    expires,
                    signature
                )
            );
    }
}
