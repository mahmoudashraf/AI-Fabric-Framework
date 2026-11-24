package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Available installed tools, independent of which ones a workflow currently enables. */
@RestController
public class WorkflowConfigurationController {
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    private final DeploymentService service;
    private final SpecialistAssistanceAuthorityService authority;
    private final AuthoringProperties properties;
    public WorkflowConfigurationController(DeploymentRepository deployments, DeploymentAccessService access,
        DeploymentService service, SpecialistAssistanceAuthorityService authority, AuthoringProperties properties) {
        this.deployments = deployments; this.access = access; this.service = service;
        this.authority = authority; this.properties = properties;
    }
    public record Catalogue(String deploymentId, List<String> readActions, List<String> writeActions) { }
    @GetMapping("/api/deployments/{id}/workflow-actions")
    @PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public Catalogue catalogue(@PathVariable String id, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var deployment = deployments.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        access.requireDeploymentEditorAccess(deployment);
        var draft = service.getActiveDraftForDeployment(id);
        var available = AuthoringNativeCapabilities.add(deployment, properties,
            authority.resolveTasks(id, draft.actionsConfig(), draft.knowledgeSourceConfig()));
        return new Catalogue(id, available.readActionScopes().keySet().stream().sorted().toList(),
            available.writeActionScopes().keySet().stream().sorted().toList());
    }
}
