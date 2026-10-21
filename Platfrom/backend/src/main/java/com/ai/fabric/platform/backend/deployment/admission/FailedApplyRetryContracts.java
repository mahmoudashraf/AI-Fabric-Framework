package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Explicitly authorizes new provider jobs on existing owned applications, not an effect-free recovery. */
public final class FailedApplyRetryContracts {
    private FailedApplyRetryContracts() { }
    public static final String BOUNDARY = "INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1";
    public static final String REAPPLY_BOUNDARY = "COOLIFY_REAPPLY_BEFORE_RUNTIME_STOP_V1";
    public static final String COLD_MAINTENANCE_BOUNDARY = "COOLIFY_REAPPLY_AFTER_VERIFIED_COLD_MAINTENANCE_V1";

    record ReapplyScope(String priorReleaseId, Instant priorReleaseCreatedAt, String priorRuntimeBaseUrl) { }
    record Submission(Summary summary, boolean queued) { }

    public record PlanRequest(String releaseId, String operationKey, String reason, boolean acceptOwnedServiceRedeploy) { }
    public record Command(@JsonProperty(value = "fence", required = true) @JsonSetter(nulls = Nulls.FAIL)
                          @JsonDeserialize(using = AdmissionContracts.CancelUnusedFenceDeserializer.class) AdmissionContracts.Fence fence,
                          String releaseId, String operationKey, String reason,
                          boolean acceptOwnedServiceRedeploy, String proofFingerprint) { }
    public record Plan(Command command, String boundary, List<String> redeployedServiceRoles) { }
    public record NativeReadback(Instant observedAt, Map<String, String> inventory) { }
    record Candidate(AdmissionContracts.Reservation reservation, Map<String, String> inventory,
                     String failedReleaseJson, Instant releaseCreatedAt, Instant failedAt, List<String> retainedAllocationIds) { }
    public record Summary(String receiptId, String reservationId, String generation, long fromRevision,
                          long revision, String releaseId, String status, boolean financialMutationPerformed,
                          List<String> retainedAllocationIds, Instant authorizedAt) { }
    record Receipt(String actorId, String commandFingerprint, Command command, Candidate failedAttempt,
                   NativeReadback providerReadback, Summary summary) { }
}
