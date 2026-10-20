package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Retains local preparation and funding; never retries a provider operation or refunds capacity. */
public final class PreparedFailureRecoveryContracts {
    private PreparedFailureRecoveryContracts() { }

    public record PlanRequest(String releaseId, String operationKey, String reason) { }
    public record Command(@JsonProperty(value = "fence", required = true) @JsonSetter(nulls = Nulls.FAIL)
                          @JsonDeserialize(using = AdmissionContracts.CancelUnusedFenceDeserializer.class) AdmissionContracts.Fence fence,
                          String releaseId, String operationKey, String reason, String proofFingerprint) { }
    public record Plan(Command command, String boundary) { }
    public record Proof(String boundary, Map<String, String> inventory, List<String> retainedAllocationIds) { }
    public record Summary(String receiptId, String reservationId, String generation, long fromRevision, long revision,
                          String releaseId, AdmissionContracts.State state, List<String> retainedAllocationIds,
                          boolean providerRetryPerformed, boolean financialMutationPerformed, Instant recoveredAt) { }
    record Receipt(String actorId, String commandFingerprint, Command command, Proof proof,
                   AdmissionContracts.Reservation reservation, Summary summary) { }
}
