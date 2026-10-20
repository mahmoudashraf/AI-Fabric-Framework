package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** An explicit continuation after native provisioning, never a runtime redeploy or allowance reset. */
public final class PostProvisionResumeContracts {
    private PostProvisionResumeContracts() { }
    public static final String BOUNDARY = "COOLIFY_DEPENDENCY_ADMISSION_FAILED_AFTER_PROVISIONING_V1";
    public record PlanRequest(String releaseId, String operationKey, String reason,
                              boolean acceptRemainingDependencyProvisioning) { }
    public record Command(@JsonProperty(value = "fence", required = true) @JsonSetter(nulls = Nulls.FAIL)
                          @JsonDeserialize(using = AdmissionContracts.CancelUnusedFenceDeserializer.class) AdmissionContracts.Fence fence,
                          String releaseId, String operationKey, String reason,
                          boolean acceptRemainingDependencyProvisioning, String proofFingerprint) { }
    public record Plan(Command command, String boundary, List<String> remainingPhases,
                       boolean runtimeRedeployRequired, List<String> retainedAllocationIds) { }
    record Candidate(AdmissionContracts.Reservation reservation, Map<String, String> inventory,
                     String failedReleaseJson, Instant releaseCreatedAt, Instant failedAt,
                     List<String> retainedAllocationIds) { }
    public record Summary(String receiptId, String reservationId, String generation, long fromRevision,
                          long revision, String releaseId, String status, boolean financialMutationPerformed,
                          boolean runtimeRedeployRequired, List<String> retainedAllocationIds, Instant authorizedAt) { }
    record Receipt(String actorId, String commandFingerprint, Command command, Candidate failedAttempt,
                   FailedApplyRetryContracts.NativeReadback providerReadback, Summary summary) { }
}
