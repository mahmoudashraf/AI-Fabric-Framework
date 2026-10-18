package com.ai.fabric.platform.backend.customerselfservice.model;

import java.time.Instant;
import java.util.List;

/** Candidate-bound customer projection. No item can be manually marked complete. */
public final class CustomerGoLiveReadinessContracts {
    private CustomerGoLiveReadinessContracts() { }

    public enum Status { PASSED, BLOCKED, PENDING, NOT_APPLICABLE }
    public enum Phase { ACTIVATION, POST_ACTIVATION }

    public record Item(String code, String category, String label, Phase phase,
                       Status status, boolean mandatory, String owner,
                       String evidenceReference, Instant checkedAt, Instant expiresAt,
                       String blocker, String nextAction) { }

    public record Summary(String deploymentId, String offerId, String versionId,
                          String releaseId, String targetProfileId,
                          String candidateFingerprint, String consumerId,
                          long expectedBindingRevision, boolean activationReady,
                          boolean postActivationComplete, List<String> blockers,
                          List<Item> items, Instant projectedAt) {
        public Summary {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
