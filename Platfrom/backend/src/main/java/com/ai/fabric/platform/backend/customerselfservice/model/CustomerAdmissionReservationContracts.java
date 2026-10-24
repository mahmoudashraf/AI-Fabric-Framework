package com.ai.fabric.platform.backend.customerselfservice.model;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Legacy request shapes and read-only history. These records cannot authorize capacity operations. */
public final class CustomerAdmissionReservationContracts {
    private CustomerAdmissionReservationContracts() { }

    public record Request(@NotBlank String targetProfileId,
                          @NotBlank String versionId,
                          String releaseId,
                          @NotNull Operation operation,
                          @NotBlank String operationKey,
                          String allocationId) {
        public Request(String targetProfileId, String versionId, String releaseId,
                       Operation operation, String operationKey) {
            this(targetProfileId, versionId, releaseId, operation, operationKey, null);
        }
    }

    public record ExtensionRequest(@NotBlank String targetProfileId, @NotBlank String allocationId,
                                   long expectedRevision, @NotBlank String operationKey,
                                   @NotBlank String reason, boolean keepAfterPromotion) { }

    /** Refreshes checks for the original, unused apply; it does not extend capacity or funding. */
    public record RefreshRequest(String targetProfileId, String versionId, Operation operation,
                                 String operationKey, long expectedRevision) { }

    public record Summary(String id,
                          String deploymentId,
                          String targetProfileId,
                          TargetIntent intent,
                          Operation operation,
                          String state,
                          long revision,
                          Instant expiresAt,
                          String expiryState,
                          Instant keepUntil,
                          boolean occupiesActiveSlot,
                          JsonNode chargedDemand) {
        public Summary(String id, String deploymentId, String targetProfileId, TargetIntent intent, Operation operation,
                       String state, long revision, Instant expiresAt, String expiryState, Instant keepUntil) {
            this(id, deploymentId, targetProfileId, intent, operation, state, revision, expiresAt, expiryState, keepUntil,
                !"RELEASED".equals(state) && !"RETAINED_EXTERNAL".equals(state), null);
        }
    }
}
