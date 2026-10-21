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
    public enum Mode { FULL_DRAIN, IDLE_CHECKPOINT_COLD_RESTART }
    public enum Effect { QUIESCE_CONNECTOR, QUIESCE_INBOX, BEGIN_DRAIN, STOP_RUNTIME, PATCH_IMAGE, PATCH_PROVENANCE,
        DEPLOY_RUNTIME, CANCEL_DRAIN, RESUME_INBOX, RESUME_CONNECTOR }
    public enum Outcome { INTENT, CONFIRMED, UNKNOWN }
    public record Request(AdmissionContracts.Fence fence, String replacementArtifactId,
                          String expectedOfferFingerprint, long expectedEntitlementRevision,
                          String operationKey, String reason, boolean acceptRuntimeRestart, Mode mode) { }
    public record Command(String planId, String planFingerprint) { }
    public record StepCommand(String planId, String planFingerprint, Effect effect) { }
    public record Scope(String customerId, String tenantId, String deploymentId, String targetProfileId,
                        String releaseId, String versionId, String versionConfigHash, String authorityFingerprint,
                        String reservationId, String generation, String runtimeBaseUrl,
                        String runtimeApplicationUuid, String connectorApplicationUuid, String databaseUuid) { }
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
