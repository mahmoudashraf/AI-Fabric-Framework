package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter.NativeSnapshot;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter.Observation;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter.RuntimeLifecycleGateway;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseVerificationService;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Resolves server-owned adapters, never policy, placement selection or historical resource adoption. */
@Component
public class NativeLifecycleEffects implements LifecycleEffects {
    public record Frozen(NativeSnapshot resources, String runtimeBaseUrl) { }
    private final NativeLifecycleAdapter nativeAdapter;
    private final RuntimeLifecycleGateway runtime;
    private final LifecycleCommandStore store;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentVerificationRunRepository verificationRuns;
    private final DeploymentReleaseVerificationService verification;
    private final DeploymentReleaseEndpointService endpoints;
    private final PlatformConsumerRepository consumers;
    private final ObjectMapper mapper;

    public NativeLifecycleEffects(NativeLifecycleAdapter nativeAdapter, RuntimeLifecycleGateway runtime,
        LifecycleCommandStore store, DeploymentRepository deployments, DeploymentReleaseRepository releases,
        DeploymentVersionRepository versions, DeploymentVerificationRunRepository verificationRuns,
        DeploymentReleaseVerificationService verification, DeploymentReleaseEndpointService endpoints,
        PlatformConsumerRepository consumers, ObjectMapper mapper) {
        this.nativeAdapter = nativeAdapter; this.runtime = runtime; this.store = store; this.deployments = deployments;
        this.releases = releases; this.versions = versions; this.verificationRuns = verificationRuns;
        this.verification = verification; this.endpoints = endpoints; this.consumers = consumers; this.mapper = mapper;
    }

    @Override public JsonNode freeze(String deploymentId, String targetProfileId, String releaseId) {
        var resources = nativeAdapter.freeze(deploymentId, targetProfileId, releaseId);
        var release = releases.findById(releaseId).orElseThrow();
        return mapper.valueToTree(new Frozen(resources, endpoints.runtimeBaseUrl(release)));
    }

    @Override public void requireUnchanged(LifecycleCommandStore.Scope scope) {
        var job = scope.job();
        var deployment = deployments.findById(job.deploymentId()).orElseThrow(() -> conflict("LIFECYCLE_DEPLOYMENT_MISSING"));
        var release = releases.findById(job.releaseId()).orElseThrow(() -> conflict("LIFECYCLE_RELEASE_MISSING"));
        var version = versions.findById(scope.versionId()).orElseThrow(() -> conflict("LIFECYCLE_VERSION_MISSING"));
        var latest = releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(job.deploymentId(), job.targetProfileId());
        if (!Objects.equals(job.customerId(), deployment.getCustomerId() == null ? "platform" : deployment.getCustomerId())
            || !Objects.equals(job.tenantId(), deployment.getTenantId()) || latest.isEmpty()
            || !job.releaseId().equals(latest.getFirst().getId())
            || !job.deploymentId().equals(release.getDeploymentId()) || !job.targetProfileId().equals(release.getTargetProfileId())
            || !scope.versionId().equals(release.getDeploymentVersionId()) || !job.deploymentId().equals(version.getDeploymentId())
            || !"PUBLISHED".equals(version.getStatus()) || !Objects.equals(scope.versionConfigHash(), version.getConfigHash())
            || !Objects.equals(scope.authorityFingerprint(), release.getRuntimeAuthorityFingerprint())
            || !Objects.equals(frozen(scope).runtimeBaseUrl(), endpoints.runtimeBaseUrl(release)))
            throw conflict("LIFECYCLE_FROZEN_SCOPE_CHANGED");
    }

    @Override public Result execute(LifecycleCommandStore.Work work) {
        var scope = work.grant().scope(); var job = scope.job();
        var snapshot = frozen(scope).resources(); var checkpoint = checkpoint(work);
        store.guard(work);
        return switch (work.command().phase()) {
            case CLAIM -> observed(nativeAdapter.observe(snapshot), "EXACT_TARGET_CLAIMED");
            case OBSERVE -> observed(nativeAdapter.observe(snapshot), "EXACT_TARGET_OBSERVED");
            case BEGIN_DRAIN, OBSERVE_DRAIN, FENCE -> runtime(work);
            case STOP -> observed(nativeAdapter.stop(snapshot, checkpoint), "NATIVE_STOP_VERIFIED");
            case DELETE -> {
                requireUnassigned(scope);
                yield observed(nativeAdapter.delete(snapshot, scope.reclaimOwnedData(), checkpoint), "NATIVE_RECLAMATION_VERIFIED");
            }
            case RECOVER -> {
                String stopProof = "recovery-existing-resources-stopped";
                if (!store.effectComplete(work, stopProof)) {
                    var stopped = nativeAdapter.stop(snapshot, checkpoint);
                    if (!stopped.complete()) yield observed(stopped, "RECOVERY_STOP_PENDING");
                    store.beforeEffect(work, stopProof); store.completed(work, stopProof);
                }
                yield observed(nativeAdapter.restart(snapshot, checkpoint), "EXISTING_RESOURCES_RESTARTED");
            }
            case VERIFY -> switch (job.goal()) {
                case STOP -> {
                    var stopped = nativeAdapter.verifyStopped(snapshot);
                    if (stopped.complete()) updateReleaseProjection(work, "STOPPED", "STOPPED", null);
                    yield observed(stopped, "STOPPED_RESOURCES_VERIFIED_DATA_RETAINED");
                }
                case RETIRE -> {
                    var absent = nativeAdapter.verifyAbsent(snapshot);
                    if (!absent.absent() || !absent.dataReclaimed()) yield new Result(Status.PENDING,
                        "NATIVE_STORAGE_ABSENCE_UNPROVEN", List.of("NATIVE_STORAGE_ABSENCE_UNPROVEN"));
                    if (absent.complete()) updateReleaseProjection(work, "RETIRED", "DELETED", null);
                    yield observed(absent, "OWNED_RESOURCES_ABSENT_EXTERNAL_DATA_RETAINED");
                }
                case RECOVER -> verifyRecovery(work);
                case RECONCILE -> observed(nativeAdapter.observe(snapshot), "EXACT_TARGET_OBSERVED");
            };
            case CANCEL -> cancel(work);
            case RELEASE -> Result.succeeded("NATIVE_OPERATION_VERIFIED_AND_RELEASED");
        };
    }

    private Result runtime(LifecycleCommandStore.Work work) {
        var scope = work.grant().scope(); var job = scope.job();
        var target = new RuntimeLifecycleGateway.FrozenTarget(job.deploymentId(), job.tenantId(), job.targetProfileId(),
            job.releaseId(), scope.authorityFingerprint(), frozen(scope).runtimeBaseUrl());
        String remoteProof = null;
        if (work.command().phase() == Phase.FENCE) {
            String proofEffect = "authoritative-native-workers-fenced";
            remoteProof = store.providerOperation(work, proofEffect);
            if (remoteProof == null) {
                var drained = runtime.execute(Phase.OBSERVE_DRAIN, target, new RuntimeLifecycleGateway.Operation(job.operationId(),
                    job.scopeHash(), job.dueAt(), job.deadlineAt(), null));
                if (drained.status() != Status.SUCCEEDED) return new Result(drained.status(), drained.code(), drained.blockers());
                var snapshot = frozen(scope).resources();
                for (String dependency : drained.remoteDependencies()) {
                    if (!nativeAdapter.supportsRemoteDependency(snapshot, dependency))
                        return new Result(Status.BLOCKED, "EXTERNAL_WORKER_FENCE_UNSUPPORTED", List.of("EXTERNAL_WORKER_FENCE_UNSUPPORTED:" + dependency));
                }
            }
            // Stop/read back owned remote workers, then the Runtime independently rechecks its local attempts before fencing.
            var workers = nativeAdapter.quiesceWorkers(frozen(scope).resources(), checkpoint(work));
            if (!workers.complete()) return observed(workers, "NATIVE_WORKERS_FENCED");
            if (remoteProof == null) {
                store.beforeEffect(work, proofEffect);
                remoteProof = "native-fence:" + work.command().commandId();
                store.dispatched(work, proofEffect, remoteProof);
                store.completed(work, proofEffect);
            }
        }
        if (work.command().phase() != Phase.OBSERVE_DRAIN) store.beforeEffect(work, "runtime-" + work.command().phase());
        var result = runtime.execute(work.command().phase(), target, new RuntimeLifecycleGateway.Operation(job.operationId(),
            job.scopeHash(), job.dueAt(), job.deadlineAt(), remoteProof));
        if (result.status() == Status.SUCCEEDED && work.command().phase() != Phase.OBSERVE_DRAIN)
            store.completed(work, "runtime-" + work.command().phase());
        return new Result(result.status(), result.code(), result.blockers());
    }

    private Result cancel(LifecycleCommandStore.Work work) {
        if (store.anyEffect(work.grant().id())) return new Result(Status.BLOCKED, "NATIVE_EFFECT_ALREADY_STARTED",
            List.of("NATIVE_EFFECT_ALREADY_STARTED"));
        if (store.unsettledOtherCommand(work.grant().id(), work.command().commandId()))
            return new Result(Status.PENDING, "NATIVE_COMMAND_RECONCILIATION_REQUIRED", List.of("NATIVE_COMMAND_RECONCILIATION_REQUIRED"));
        if (store.succeeded(work.grant().id(), Phase.BEGIN_DRAIN)) {
            requireUnchanged(work.grant().scope());
            return runtime(work);
        }
        return Result.succeeded("UNSTARTED_OPERATION_CANCELLED");
    }

    private Result verifyRecovery(LifecycleCommandStore.Work work) {
        var scope = work.grant().scope(); var job = scope.job();
        var nativeReady = nativeAdapter.observe(frozen(scope).resources());
        if (!nativeReady.complete() || !nativeReady.ready()) return new Result(Status.PENDING,
            "RECOVERY_NATIVE_READINESS_PENDING", List.of("RECOVERY_NATIVE_READINESS_PENDING"));
        String effect = "actual-release-verification";
        String recordedId = store.providerOperation(work, effect);
        if (recordedId != null) {
            var run = verificationRuns.findById(recordedId).orElseThrow(() -> conflict("RECOVERY_VERIFICATION_RECORD_MISSING"));
            if (!job.releaseId().equals(run.getReleaseId()) || !scope.versionId().equals(run.getDeploymentVersionId()))
                throw conflict("RECOVERY_VERIFICATION_SCOPE_CHANGED");
            if (!store.effectComplete(work, effect)) store.completed(work, effect);
            return "PASSED".equals(run.getStatus()) ? Result.succeeded("RECOVERY_RELEASE_VERIFICATION_PASSED")
                : new Result(Status.BLOCKED, "RECOVERY_RELEASE_VERIFICATION_FAILED", List.of("RECOVERY_RELEASE_VERIFICATION_FAILED"));
        }
        if (!store.beforeEffect(work, effect)) return new Result(Status.UNKNOWN, "RECOVERY_VERIFICATION_OUTCOME_UNKNOWN",
            List.of("RECOVERY_VERIFICATION_OUTCOME_UNKNOWN"));
        // Existing resources are running. Restore only the exact release's provisioned posture before real probes.
        updateReleaseProjection(work, "APPLIED_VERIFICATION_FAILED", "ACTIVE", null);
        var deployment = deployments.findById(job.deploymentId()).orElseThrow();
        var release = releases.findById(job.releaseId()).orElseThrow();
        var version = versions.findById(scope.versionId()).orElseThrow();
        var run = verification.verify(deployment, version, release, "OPERATIONS_RECOVERY");
        store.transaction(() -> {
            store.guard(work); verificationRuns.saveAndFlush(run);
            store.dispatched(work, effect, run.getId());
            updateReleaseProjection(work, "PASSED".equals(run.getStatus()) ? "APPLIED_VERIFIED" : "APPLIED_VERIFICATION_FAILED", "ACTIVE", run.getId());
            store.completed(work, effect); return null;
        });
        return "PASSED".equals(run.getStatus()) ? Result.succeeded("RECOVERY_RELEASE_VERIFICATION_PASSED")
            : new Result(Status.BLOCKED, "RECOVERY_RELEASE_VERIFICATION_FAILED", List.of("RECOVERY_RELEASE_VERIFICATION_FAILED"));
    }

    private void updateReleaseProjection(LifecycleCommandStore.Work work, String status, String provisioning, String runId) {
        store.transaction(() -> {
            store.guard(work);
            var job = work.grant().scope().job();
            deployments.findByIdForUpdate(job.deploymentId()).orElseThrow();
            var release = releases.findById(job.releaseId()).orElseThrow();
            release.setStatus(status); release.setProvisioningStatus(provisioning); release.setUpdatedAt(Instant.now());
            if (runId != null) {
                var run = verificationRuns.findById(runId).orElseThrow();
                release.setVerificationRunId(runId); release.setVerificationStatus(run.getStatus());
            }
            releases.saveAndFlush(release);
            // A stopped staging target must not overwrite the production deployment's projection.
            if (releases.findTopByDeploymentIdOrderByCreatedAtDesc(job.deploymentId()).map(value -> job.releaseId().equals(value.getId())).orElse(false)) {
                var deployment = deployments.findById(job.deploymentId()).orElseThrow();
                if (Objects.equals(deployment.getActiveVersionId(), work.grant().scope().versionId())) {
                    deployment.setStatus("APPLIED_VERIFIED".equals(status) ? "ACTIVE" : status);
                    deployment.setUpdatedAt(Instant.now()); deployments.save(deployment);
                }
            }
            return null;
        });
    }

    private NativeLifecycleAdapter.Checkpoint checkpoint(LifecycleCommandStore.Work work) {
        return new NativeLifecycleAdapter.Checkpoint() {
            public boolean beforeEffect(String id) { requireUnchanged(work.grant().scope()); return store.beforeEffect(work, id); }
            public void completed(String id) { store.completed(work, id); }
            public boolean isComplete(String id) { return store.effectComplete(work, id); }
            public void dispatched(String id, String nativeId) { store.dispatched(work, id, nativeId); }
            public String operationId(String id) { return store.providerOperation(work, id); }
            public void guard() {
                store.guard(work); requireUnchanged(work.grant().scope());
                if (work.command().phase() == Phase.DELETE) requireUnassigned(work.grant().scope());
            }
        };
    }
    private void requireUnassigned(LifecycleCommandStore.Scope scope) {
        if (consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc(scope.job().deploymentId()).stream()
            .anyMatch(consumer -> (consumer.getBoundTargetProfileId() == null
                || scope.job().targetProfileId().equals(consumer.getBoundTargetProfileId()))
                && !"DISABLED".equals(consumer.getStatus()))) throw conflict("LIFECYCLE_ACTIVE_ASSIGNMENT_MUST_BE_MOVED");
    }
    private Result observed(Observation observation, String success) {
        return new Result(observation.complete() ? Status.SUCCEEDED : Status.PENDING,
            observation.complete() ? success : "NATIVE_READBACK_PENDING", observation.blockers().stream().limit(32).toList());
    }
    private Frozen frozen(LifecycleCommandStore.Scope scope) { return mapper.convertValue(scope.nativeSnapshot(), Frozen.class); }
    private static ResponseStatusException conflict(String code) { return new ResponseStatusException(CONFLICT, code); }
}
