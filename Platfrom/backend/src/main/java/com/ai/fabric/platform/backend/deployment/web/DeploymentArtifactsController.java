package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentArtifactService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/versions/{versionId}/artifacts")
public class DeploymentArtifactsController {

    private static final MediaType YAML_MEDIA_TYPE = MediaType.parseMediaType("application/yaml");

    private final DeploymentArtifactService deploymentArtifactService;

    public DeploymentArtifactsController(DeploymentArtifactService deploymentArtifactService) {
        this.deploymentArtifactService = deploymentArtifactService;
    }

    @GetMapping
    public DeploymentArtifactBundleSummary getArtifactBundle(@PathVariable String deploymentId,
                                                             @PathVariable String versionId) {
        return deploymentArtifactService.getBundleSummary(deploymentId, versionId);
    }

    @GetMapping("/ai-actions.yml")
    public ResponseEntity<String> getActionsArtifact(@PathVariable String deploymentId,
                                                     @PathVariable String versionId) {
        return yamlResponse("ai-actions.yml", deploymentArtifactService.readActionsArtifact(deploymentId, versionId));
    }

    @GetMapping("/ai-entity-config.yml")
    public ResponseEntity<String> getEntityArtifact(@PathVariable String deploymentId,
                                                    @PathVariable String versionId) {
        return yamlResponse(
            "ai-entity-config.yml",
            deploymentArtifactService.readEntityArtifact(deploymentId, versionId)
        );
    }

    @GetMapping("/actions-routing.yml")
    public ResponseEntity<String> getRoutingArtifact(@PathVariable String deploymentId,
                                                     @PathVariable String versionId) {
        return yamlResponse(
            "actions-routing.yml",
            deploymentArtifactService.readRoutingArtifact(deploymentId, versionId)
        );
    }

    @GetMapping("/deployment-manifest.json")
    public ResponseEntity<String> getManifestArtifact(@PathVariable String deploymentId,
                                                      @PathVariable String versionId) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"deployment-manifest.json\"")
            .body(deploymentArtifactService.readManifestArtifact(deploymentId, versionId));
    }

    private ResponseEntity<String> yamlResponse(String filename, String body) {
        return ResponseEntity.ok()
            .contentType(YAML_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(body);
    }
}
