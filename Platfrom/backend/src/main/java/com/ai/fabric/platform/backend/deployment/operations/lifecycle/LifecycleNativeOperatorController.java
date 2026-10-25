package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Existing native authority remains operable by its owner during an operations-service outage. */
@RestController
@RequestMapping("/api/deployment-operations/jobs/{operationId}/native")
public class LifecycleNativeOperatorController {
    private final LifecycleCommandStore store;
    private final LifecycleCommandExecutor executor;
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    public LifecycleNativeOperatorController(LifecycleCommandStore store, LifecycleCommandExecutor executor,
        DeploymentRepository deployments, DeploymentAccessService access) {
        this.store = store; this.executor = executor; this.deployments = deployments; this.access = access;
    }
    @GetMapping
    public Map<String, Object> receipts(@PathVariable String operationId) {
        var grant = authorized(operationId, false);
        return Map.of("authorization", grant.scope().job(), "state", grant.state(), "claimed", grant.claimed(),
            "commands", store.receipts(grant.id()));
    }
    @PostMapping("/commands")
    public LifecycleContracts.Receipt command(@PathVariable String operationId,
        @Valid @RequestBody LifecycleContracts.Command command) {
        var grant = authorized(operationId, true);
        if (!operationId.equals(command.operationId()) || !grant.id().equals(command.grantId()))
            throw new ResponseStatusException(CONFLICT, "LIFECYCLE_SCOPE_MISMATCH");
        return executor.submit(command);
    }
    private LifecycleCommandStore.Grant authorized(String id, boolean mutate) {
        var grant = store.byOperation(id);
        if (grant == null) throw new ResponseStatusException(NOT_FOUND);
        var deployment = deployments.findById(grant.scope().job().deploymentId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (mutate) access.requireDeploymentAdminAccess(deployment); else access.requireDeploymentAccess(deployment);
        return grant;
    }
}
