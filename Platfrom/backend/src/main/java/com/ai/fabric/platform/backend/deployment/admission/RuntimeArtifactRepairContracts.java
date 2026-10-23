package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One reviewed, same-release runtime-image repair. No allowance or original offer mutation. */
public final class RuntimeArtifactRepairContracts {
    private RuntimeArtifactRepairContracts() { }
    public static final String BOUNDARY = "SAME_RELEASE_REVIEWED_RUNTIME_ARTIFACT_REPAIR_V1";
    public enum Mode { FULL_DRAIN, IDLE_CHECKPOINT_COLD_RESTART, COLD_MAINTENANCE_REAPPLY }
    public enum Effect { QUIESCE_CONNECTOR, QUIESCE_INBOX, BEGIN_DRAIN, STOP_RUNTIME, PATCH_IMAGE, PATCH_PROVENANCE,
        DEPLOY_RUNTIME, CANCEL_DRAIN, RESUME_INBOX, RESUME_CONNECTOR, ADOPT_COLD_GENERATION }
    public enum Outcome { INTENT, CONFIRMED, UNKNOWN }
    public record Request(AdmissionContracts.Fence fence, String replacementArtifactId,
                          String expectedOfferFingerprint, long expectedEntitlementRevision,
                          String operationKey, String reason, boolean acceptRuntimeRestart, Mode mode,
                          @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                          ColdMaintenanceAcknowledgement coldMaintenance) {
        public Request(AdmissionContracts.Fence fence, String replacementArtifactId, String expectedOfferFingerprint,
                       long expectedEntitlementRevision, String operationKey, String reason,
                       boolean acceptRuntimeRestart, Mode mode) {
            this(fence, replacementArtifactId, expectedOfferFingerprint, expectedEntitlementRevision,
                operationKey, reason, acceptRuntimeRestart, mode, null);
        }
    }
    public record ColdMaintenanceAcknowledgement(String previousReleaseId, String approvalReference,
                                                  boolean acceptInMemoryStateLoss) { }
    public record ColdPriorRuntime(String releaseId, String versionId, String authorityFingerprint,
                                   boolean unassignedVerificationFailure) {
        public ColdPriorRuntime(String releaseId, String versionId, String authorityFingerprint) {
            this(releaseId, versionId, authorityFingerprint, false);
        }
    }

    public static List<Effect> effects(Mode mode) {
        if (mode == Mode.COLD_MAINTENANCE_REAPPLY) {
            return List.of(Effect.QUIESCE_CONNECTOR, Effect.QUIESCE_INBOX, Effect.STOP_RUNTIME,
                Effect.PATCH_IMAGE, Effect.PATCH_PROVENANCE, Effect.DEPLOY_RUNTIME,
                Effect.ADOPT_COLD_GENERATION, Effect.RESUME_INBOX, Effect.RESUME_CONNECTOR);
        }
        return List.of(Effect.QUIESCE_CONNECTOR, Effect.QUIESCE_INBOX, Effect.BEGIN_DRAIN,
            Effect.STOP_RUNTIME, Effect.PATCH_IMAGE, Effect.PATCH_PROVENANCE, Effect.DEPLOY_RUNTIME,
            Effect.CANCEL_DRAIN, Effect.RESUME_INBOX, Effect.RESUME_CONNECTOR);
    }
    public record Command(String planId, String planFingerprint) { }
    public record RenewWindow(Command command, Instant expectedExecutionDeadline, Instant executionDeadline,
                              String reason, boolean acceptEmptyJdbcColdRestart) { }
    public record ExecutionWindow(RenewWindow review, String actorId, Instant reviewedAt) { }
    public record StepCommand(String planId, String planFingerprint, Effect effect) { }
    public record Scope(String customerId, String tenantId, String deploymentId, String targetProfileId,
                        String releaseId, String versionId, String versionConfigHash, String authorityFingerprint,
                        String reservationId, String generation, String runtimeBaseUrl,
                        String runtimeApplicationUuid, String connectorApplicationUuid, String databaseUuid,
                        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                        String priorRuntimeReleaseId) {
        public Scope(String customerId, String tenantId, String deploymentId, String targetProfileId,
                     String releaseId, String versionId, String versionConfigHash, String authorityFingerprint,
                     String reservationId, String generation, String runtimeBaseUrl,
                     String runtimeApplicationUuid, String connectorApplicationUuid, String databaseUuid) {
            this(customerId, tenantId, deploymentId, targetProfileId, releaseId, versionId, versionConfigHash,
                authorityFingerprint, reservationId, generation, runtimeBaseUrl, runtimeApplicationUuid,
                connectorApplicationUuid, databaseUuid, null);
        }
    }
    public record ArtifactPin(String artifactId, String fingerprint, String repository, String digest,
                              String gitCommit, String buildRunId, String promotionChannel, String capabilityManifestHash) { }
    public record LocalEvidence(Scope scope, Request request, ArtifactPin original, ArtifactPin replacement,
                                Map<String, String> fingerprints, Instant validUntil, Instant executionDeadline) {
        public LocalEvidence { fingerprints = Map.copyOf(fingerprints); }
    }
    public record NativeEvidence(Map<String, String> identities, ProviderUsageRuntimeClient.Readback wallet,
                                 RuntimeGenerationTransitionContracts.Readback runtime, Instant observedAt) {
        public NativeEvidence { identities = Map.copyOf(identities); }
    }
    public record NativeJob(String uuid, String applicationUuid, String status, Instant createdAt, Instant finishedAt) { }
    public record NativeInventory(Map<String, String> fingerprints, String runtimeStatus, String connectorStatus,
                                  String runtimeRepository, String runtimeTag, List<NativeJob> runtimeJobs,
                                  List<NativeJob> connectorJobs, Instant observedAt) {
        public NativeInventory {
            fingerprints = Map.copyOf(fingerprints); runtimeJobs = List.copyOf(runtimeJobs); connectorJobs = List.copyOf(connectorJobs);
        }
    }
    public record Plan(String id, String actorId, LocalEvidence local, NativeEvidence nativeEvidence,
                       Instant createdAt, Instant expiresAt) { }
    public record PlanSummary(Command command, Scope scope, ArtifactPin original, ArtifactPin replacement,
                              Instant expiresAt, Instant executionDeadline, boolean financialMutationRequired) { }
    public record Claim(String planId, String planFingerprint, String actorId, String workerToken,
                        AdmissionContracts.Fence reservationFence, Instant claimedAt, Instant executionDeadline) { }
    public record EffectReceipt(String planId, Effect effect, Outcome outcome, String requestFingerprint,
                                Map<String, String> evidence, Instant recordedAt) {
        public EffectReceipt { evidence = Map.copyOf(evidence); }
    }
    public record VerifiedRepair(String planId, String planFingerprint, Scope scope, ArtifactPin original,
                                 ArtifactPin replacement, String applicationUuid, String deploymentJobUuid,
                                 Instant jobCreatedAt, Instant jobFinishedAt,
                                 NativeJob connectorJob, ProviderUsageRuntimeClient.Readback wallet, Map<String, String> finalLocalFingerprints,
                                 Map<String, String> finalNativeInventory, Instant verifiedAt) {
        public VerifiedRepair {
            finalLocalFingerprints = Map.copyOf(finalLocalFingerprints); finalNativeInventory = Map.copyOf(finalNativeInventory);
        }
    }
    public record Summary(String planId, String releaseId, String status, Effect pendingEffect,
                          List<Effect> confirmedEffects, String verifiedReceiptFingerprint,
                          boolean financialMutationPerformed) { }
}
