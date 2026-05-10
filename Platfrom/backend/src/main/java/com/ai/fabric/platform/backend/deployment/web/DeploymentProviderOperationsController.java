package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentSourceArtifactRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceHandleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLogsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetProfileSummary;
import com.ai.fabric.platform.backend.deployment.model.PatchDeploymentTargetProfileRequest;
import com.ai.fabric.platform.backend.deployment.model.PromoteDeploymentSourceArtifactRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentProviderResourceActionService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deployment-provider")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class DeploymentProviderOperationsController {

    private final DeploymentTargetProfileService targetProfileService;
    private final DeploymentSourceArtifactService sourceArtifactService;
    private final DeploymentProviderResourceActionService providerResourceActionService;

    public DeploymentProviderOperationsController(DeploymentTargetProfileService targetProfileService,
                                                  DeploymentSourceArtifactService sourceArtifactService,
                                                  DeploymentProviderResourceActionService providerResourceActionService) {
        this.targetProfileService = targetProfileService;
        this.sourceArtifactService = sourceArtifactService;
        this.providerResourceActionService = providerResourceActionService;
    }

    @GetMapping("/target-profiles")
    public List<DeploymentTargetProfileSummary> listTargetProfiles(@RequestParam(required = false) DeploymentProviderType providerType) {
        return targetProfileService.listProfiles(providerType);
    }

    @GetMapping("/target-profiles/{targetProfileId}/preflight")
    public DeploymentProviderPreflightSummary preflight(@PathVariable String targetProfileId) {
        return providerResourceActionService.preflight(targetProfileId);
    }

    @PatchMapping("/target-profiles/{targetProfileId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentTargetProfileSummary patchTargetProfile(@PathVariable String targetProfileId,
                                                             @RequestBody PatchDeploymentTargetProfileRequest request) {
        return targetProfileService.patchProfile(targetProfileId, request);
    }

    @GetMapping("/source-artifacts")
    public List<DeploymentSourceArtifactSummary> listSourceArtifacts(@RequestParam(required = false) String serviceName) {
        return sourceArtifactService.list(serviceName);
    }

    @PostMapping("/source-artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentSourceArtifactSummary createSourceArtifact(@RequestBody CreateDeploymentSourceArtifactRequest request) {
        return sourceArtifactService.create(request);
    }

    @PostMapping("/source-artifacts/{artifactId}/promote")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentSourceArtifactSummary promoteSourceArtifact(@PathVariable String artifactId,
                                                                 @RequestBody(required = false) PromoteDeploymentSourceArtifactRequest request) {
        return sourceArtifactService.promote(artifactId, request);
    }

    @GetMapping("/resources")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<DeploymentProviderResourceHandleSummary> listResources(@RequestParam(required = false) DeploymentProviderType providerType,
                                                                       @RequestParam(required = false) String deploymentId,
                                                                       @RequestParam(required = false) String targetProfileId) {
        return providerResourceActionService.listResources(providerType, deploymentId, targetProfileId);
    }

    @GetMapping("/resources/{handleId}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceStatusSummary resourceStatus(@PathVariable String handleId) {
        return providerResourceActionService.status(handleId);
    }

    @GetMapping("/resources/{handleId}/logs")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceLogsSummary resourceLogs(@PathVariable String handleId,
                                                              @RequestParam(defaultValue = "200") int lines) {
        return providerResourceActionService.logs(handleId, lines);
    }

    @PostMapping("/resources/{handleId}/start")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary startResource(@PathVariable String handleId,
                                                                 @RequestBody(required = false) DeploymentProviderResourceActionRequest request) {
        return providerResourceActionService.start(handleId, request);
    }

    @PostMapping("/resources/{handleId}/stop")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary stopResource(@PathVariable String handleId,
                                                                @RequestBody(required = false) DeploymentProviderResourceActionRequest request) {
        return providerResourceActionService.stop(handleId, request);
    }

    @PostMapping("/resources/{handleId}/restart")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary restartResource(@PathVariable String handleId,
                                                                   @RequestBody(required = false) DeploymentProviderResourceActionRequest request) {
        return providerResourceActionService.restart(handleId, request);
    }

    @DeleteMapping("/resources/{handleId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary deleteResource(@PathVariable String handleId,
                                                                  @RequestBody(required = false) DeploymentProviderResourceActionRequest request) {
        return providerResourceActionService.delete(handleId, request);
    }
}
