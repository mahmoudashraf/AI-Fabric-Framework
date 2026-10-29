package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceConnectionRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionService;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
public class PlatformAIWorkspaceController {

    private final AIWorkspaceInstallationService service;
    private final AIWorkspaceConnectionService connections;

    public PlatformAIWorkspaceController(AIWorkspaceInstallationService service, AIWorkspaceConnectionService connections) {
        this.service = service;
        this.connections = connections;
    }

    @PostMapping("/customers/{customerId}/ai-workspaces/connections")
    @PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformConsumerSummary createConnection(@PathVariable String customerId,
                                                     @Valid @RequestBody CreateAIWorkspaceConnectionRequest request) {
        return connections.create(customerId, request);
    }

    @GetMapping("/customers/{customerId}/ai-workspaces/connections/{consumerId}/preview-capabilities")
    @PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN')")
    public AIWorkspaceConnectionService.PreviewCapabilities previewCapabilities(@PathVariable String customerId,
                                                                               @PathVariable String consumerId) {
        return connections.previewCapabilities(customerId, consumerId);
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
                                                  @RequestBody CreateAIWorkspaceInstallationRequest request,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String operationKey) {
        return service.create(customerId, request, operationKey);
    }

    @GetMapping("/customers/{customerId}/ai-workspace-installations/{installationId}")
    public AIWorkspaceInstallationSummary get(@PathVariable String customerId,
                                               @PathVariable String installationId) {
        return service.get(customerId, installationId);
    }

    @PutMapping("/customers/{customerId}/ai-workspace-installations/{installationId}")
    public AIWorkspaceInstallationSummary update(@PathVariable String customerId,
                                                  @PathVariable String installationId,
                                                  @RequestBody UpdateAIWorkspaceInstallationRequest request,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String operationKey) {
        return service.update(customerId, installationId, request, operationKey);
    }

    @PostMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/activate")
    public AIWorkspaceInstallationSummary activate(@PathVariable String customerId,
                                                    @PathVariable String installationId,
                                                    @RequestHeader(name = "Idempotency-Key", required = false) String operationKey) {
        return service.activate(customerId, installationId, operationKey);
    }

    @PostMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/disable")
    public AIWorkspaceInstallationSummary disable(@PathVariable String customerId,
                                                   @PathVariable String installationId,
                                                   @RequestHeader(name = "Idempotency-Key", required = false) String operationKey) {
        return service.disable(customerId, installationId, operationKey);
    }

    @DeleteMapping("/customers/{customerId}/ai-workspace-installations/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable String customerId,
                            @PathVariable String installationId,
                            @RequestHeader(name = "Idempotency-Key", required = false) String operationKey) {
        service.deleteDraft(customerId, installationId, operationKey);
    }

    @GetMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/readiness")
    public AIWorkspaceReadinessSummary readiness(@PathVariable String customerId,
                                                 @PathVariable String installationId) {
        return service.readiness(customerId, installationId);
    }

    @PostMapping("/customers/{customerId}/ai-workspace-installations/{installationId}/domains/{hostname}/verify")
    public com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainSummary verifyDomain(
        @PathVariable String customerId,
        @PathVariable String installationId,
        @PathVariable String hostname,
        @RequestHeader(name = "Idempotency-Key", required = false) String operationKey
    ) {
        return service.verifyDomain(customerId, installationId, hostname, operationKey);
    }
}
