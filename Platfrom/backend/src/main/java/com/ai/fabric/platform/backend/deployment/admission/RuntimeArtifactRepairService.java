package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Explicit one-effect commands. Lost responses cannot trigger another dispatch of the same intent. */
@Service
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RuntimeArtifactRepairService {
    private final RuntimeArtifactRepairJournal journal;
    private final RuntimeArtifactRepairEvidence evidence;
    private final RuntimeArtifactRepairOperations operations;
    private final AdmissionStore store;
    private final AdmissionJson json;
    private final CustomerOfferEntitlementService entitlements;
    public RuntimeArtifactRepairService(RuntimeArtifactRepairJournal journal, RuntimeArtifactRepairEvidence evidence,
        RuntimeArtifactRepairOperations operations, AdmissionStore store, AdmissionJson json, CustomerOfferEntitlementService entitlements) {
        this.journal = journal; this.evidence = evidence; this.operations = operations; this.store = store;
        this.json = json; this.entitlements = entitlements;
    }
    public PlanSummary plan(String deploymentId, String releaseId, Request request) {
        String actor = actor();
        var local = journal.locked(() -> evidence.read(deploymentId, releaseId, request, null));
        String id = journal.preview(local, actor);
        var nativeEvidence = operations.observe(id, local);
        journal.locked(() -> { evidence.unchanged(local, evidence.read(deploymentId, releaseId, request, null)); return null; });
        var plan = journal.savePlan(id, actor, nativeEvidence);
        return new PlanSummary(new Command(plan.id(), journal.fingerprint(plan)), local.scope(), local.original(), local.replacement(),
            plan.expiresAt(), local.executionDeadline(), false);
    }
    public Summary claim(String deploymentId, String releaseId, Command command) {
        var plan = authorized(deploymentId, releaseId, command); String actor = actor();
        journal.claim(plan, actor, () -> {
            evidence.unchanged(plan.local(), evidence.read(deploymentId, releaseId, plan.local().request(), null));
            return store.claimRuntimeArtifactRepair(plan.local().request().fence(), releaseId, actor, journal.fingerprint(plan));
        });
        return journal.summary(command);
    }
    public Summary execute(String deploymentId, String releaseId, Command command, Effect effect) {
        var plan = authorized(deploymentId, releaseId, command); String actor = actor();
        if (effect == null) throw new AdmissionFailure(BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_EFFECT_REQUIRED");
        var claim = journal.requireClaim(plan);
        var intent = journal.intent(plan, effect, actor, () -> revalidate(plan, claim));
        try {
            var response = operations.perform(plan, effect, actor);
            journal.locked(() -> { revalidate(plan, claim); return null; });
            journal.outcome(plan, intent, Outcome.CONFIRMED, response, actor);
        } catch (RuntimeException failure) {
            // Even a rejected provider response is not evidence that no effect happened.
            journal.outcome(plan, intent, Outcome.UNKNOWN, Map.of("failureType", failure.getClass().getName()), actor);
            throw failure;
        }
        return journal.summary(command);
    }
    public Summary reconcile(String deploymentId, String releaseId, Command command, Effect effect) {
        var plan = authorized(deploymentId, releaseId, command); String actor = actor(); var claim = journal.requireClaim(plan);
        if (effect == null) throw new AdmissionFailure(BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_EFFECT_REQUIRED");
        journal.locked(() -> { revalidate(plan, claim); return null; });
        var observation = operations.reconcile(plan, effect);
        journal.locked(() -> { revalidate(plan, claim); return null; });
        journal.reconciled(plan, effect, observation, actor);
        return journal.summary(command);
    }
    public Summary verify(String deploymentId, String releaseId, Command command) {
        var plan = authorized(deploymentId, releaseId, command); String actor = actor();
        if (journal.verifiedRepair(releaseId) != null) return journal.summary(command);
        var claim = journal.requireClaim(plan);
        var local = journal.locked(() -> revalidate(plan, claim));
        var proof = operations.finalProof(plan, local);
        journal.finish(plan, proof, actor, () -> {
            revalidate(plan, claim);
            store.finishRuntimeArtifactRepair(claim, actor, json.fingerprint(proof));
        });
        return journal.summary(command);
    }
    public Summary read(String deploymentId, String releaseId, Command command) {
        authorized(deploymentId, releaseId, command); return journal.summary(command);
    }
    public ExecutionWindow renewWindow(String deploymentId, String releaseId, RenewWindow review) {
        if (review == null) throw new AdmissionFailure(BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_WINDOW_REVIEW_REQUIRED");
        var plan = authorized(deploymentId, releaseId, review.command()); var claim = journal.requireClaim(plan);
        return journal.renewWindow(plan, review, actor(), () -> {
            revalidate(plan, claim);
            return store.require(plan.local().scope().reservationId()).expiresAt();
        });
    }
    private LocalEvidence revalidate(Plan plan, Claim claim) {
        var scope = plan.local().scope();
        var current = evidence.read(scope.deploymentId(), scope.releaseId(), plan.local().request(), claim);
        evidence.unchanged(plan.local(), current); return current;
    }
    private Plan authorized(String deploymentId, String releaseId, Command command) {
        String actor = actor(); var plan = journal.requirePlan(command); var scope = plan.local().scope();
        if (!plan.actorId().equals(actor) || !scope.deploymentId().equals(deploymentId) || !scope.releaseId().equals(releaseId))
            throw new AdmissionFailure(FORBIDDEN, "RUNTIME_ARTIFACT_REPAIR_SCOPE_DENIED");
        return plan;
    }
    private String actor() { entitlements.requireStaffAdmin(); return PlatformSecurityContext.currentPrincipal().actorId(); }
}
