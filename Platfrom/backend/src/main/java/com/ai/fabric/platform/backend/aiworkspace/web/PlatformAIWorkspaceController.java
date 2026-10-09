package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
public class PlatformAIWorkspaceController {

    private final AIWorkspaceInstallationService service;

    public PlatformAIWorkspaceController(AIWorkspaceInstallationService service) {
        this.service = service;
    }

    @GetMapping("/ai-workspaces/catalog")
    public AIWorkspaceCatalogSummary catalog() {
        return service.catalog();
    }

    @GetMapping("/customers/{customerId}/ai-workspace-installations")
    public List<AIWorkspaceInstallationSummary> list(@PathVariable String customerId) {
        return service.list(customerId);
    }

    @PostMapping("/customers/{customerId}/ai-workspace-installations")
    @ResponseStatus(HttpStatus.CREATED)
    public AIWorkspaceInstallationSummary create(@PathVariable String customerId,
                                                  @RequestBody CreateAIWorkspaceInstallationRequest request) {
        return service.create(customerId, request);
    }

    @GetMapping("/customers/{customerId}/ai-workspace-installations/{installationId}")
    public AIWorkspaceInstallationSummary get(@PathVariable String customerId,
                                               @PathVariable String installationId) {
        return service.get(customerId, installationId);
    }

    @PutMapping("/customers/{customerId}/ai-workspace-installations/{installationId}")
    public AIWorkspaceInstallationSummary update(@PathVariable String customerId,
                                                  @PathVariable String installationId,
                                                  @RequestBody UpdateAIWorkspaceInstallationRequest request) {
        return service.update(customerId, installationId, request);
    }

    @PostMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/activate")
    public AIWorkspaceInstallationSummary activate(@PathVariable String customerId,
                                                    @PathVariable String installationId) {
        return service.activate(customerId, installationId);
    }

    @PostMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/disable")
    public AIWorkspaceInstallationSummary disable(@PathVariable String customerId,
                                                   @PathVariable String installationId) {
        return service.disable(customerId, installationId);
    }

    @DeleteMapping("/customers/{customerId}/ai-workspace-installations/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable String customerId,
                            @PathVariable String installationId) {
        service.deleteDraft(customerId, installationId);
    }

    @GetMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/readiness")
    public AIWorkspaceReadinessSummary readiness(@PathVariable String customerId,
                                                 @PathVariable String installationId) {
        return service.readiness(customerId, installationId);
    }
}
