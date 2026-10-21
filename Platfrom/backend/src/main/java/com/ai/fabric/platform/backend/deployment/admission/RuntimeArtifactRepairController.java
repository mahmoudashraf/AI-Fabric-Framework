package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;

@RestController
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@RequestMapping("/api/deployments/{deploymentId}/releases/{releaseId}")
public class RuntimeArtifactRepairController {
    private final RuntimeArtifactRepairService service;
    public RuntimeArtifactRepairController(RuntimeArtifactRepairService service) { this.service = service; }
    @PostMapping("/runtime-artifact-repair-plan")
    public PlanSummary plan(@PathVariable String deploymentId, @PathVariable String releaseId, @RequestBody Request request) {
        return service.plan(deploymentId, releaseId, request);
    }
    @PostMapping("/runtime-artifact-repairs/{planId}/claim")
    public Summary claim(@PathVariable String deploymentId, @PathVariable String releaseId, @PathVariable String planId, @RequestBody Command command) {
        same(planId, command); return service.claim(deploymentId, releaseId, command);
    }
    @PostMapping("/runtime-artifact-repairs/{planId}/effects/{effect}")
    public Summary execute(@PathVariable String deploymentId, @PathVariable String releaseId, @PathVariable String planId,
        @PathVariable Effect effect, @RequestBody Command command) {
        same(planId, command); return service.execute(deploymentId, releaseId, command, effect);
    }
    @PostMapping("/runtime-artifact-repairs/{planId}/effects/{effect}/reconcile")
    public Summary reconcile(@PathVariable String deploymentId, @PathVariable String releaseId, @PathVariable String planId,
        @PathVariable Effect effect, @RequestBody Command command) {
        same(planId, command); return service.reconcile(deploymentId, releaseId, command, effect);
    }
    @PostMapping("/runtime-artifact-repairs/{planId}/verify")
    public Summary verify(@PathVariable String deploymentId, @PathVariable String releaseId, @PathVariable String planId, @RequestBody Command command) {
        same(planId, command); return service.verify(deploymentId, releaseId, command);
    }
    @PostMapping("/runtime-artifact-repairs/{planId}/renew-window")
    public ExecutionWindow renewWindow(@PathVariable String deploymentId, @PathVariable String releaseId,
        @PathVariable String planId, @RequestBody RenewWindow review) {
        if (review == null) throw new AdmissionFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_WINDOW_REVIEW_REQUIRED");
        same(planId, review.command()); return service.renewWindow(deploymentId, releaseId, review);
    }
    @GetMapping("/runtime-artifact-repairs/{planId}")
    public Summary read(@PathVariable String deploymentId, @PathVariable String releaseId, @PathVariable String planId, @RequestParam String planFingerprint) {
        return service.read(deploymentId, releaseId, new Command(planId, planFingerprint));
    }
    private static void same(String planId, Command command) {
        if (command == null || !planId.equals(command.planId()))
            throw new AdmissionFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_PLAN_MISMATCH");
    }
}
