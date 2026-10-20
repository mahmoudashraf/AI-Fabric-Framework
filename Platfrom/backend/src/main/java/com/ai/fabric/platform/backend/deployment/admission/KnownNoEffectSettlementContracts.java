package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Explicit settlement of consumed, failed authorizations; never an unused cancellation or provider cleanup. */
public final class KnownNoEffectSettlementContracts {
    private KnownNoEffectSettlementContracts() { }

    public record FailedRelease(String releaseId, String authorizationOperationKey,
                                String authorizationRequestFingerprint) { }
    public record PlanRequest(String operationKey, String reason) { }
    public record Plan(Command command, String proofFingerprint) { }

    public record Command(
        @JsonProperty(value = "fence", required = true) @JsonSetter(nulls = Nulls.FAIL)
        @JsonDeserialize(using = CancelUnusedFenceDeserializer.class) Fence fence,
        String operationKey, String manifestFingerprint, String versionId, String versionConfigHash,
        String bindingFingerprint, List<FailedRelease> failedReleases, String reason,
        List<UnusedConsumerDetachment> consumerDetachments) {
        public Command {
            if (failedReleases != null) {
                if (failedReleases.stream().anyMatch(java.util.Objects::isNull))
                    throw new IllegalArgumentException("Failed releases must not contain null entries.");
                failedReleases = failedReleases.stream().sorted(Comparator.comparing(FailedRelease::releaseId,
                    Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            }
            consumerDetachments = consumerDetachments == null ? List.of() : List.copyOf(consumerDetachments).stream()
                .sorted(Comparator.comparing(UnusedConsumerDetachment::consumerEntityId,
                    Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        }
    }

    public record RecordProof(String id, String fingerprint) { }
    public record Proof(String executionBoundary, List<RecordProof> releases, List<RecordProof> authorizations,
                        List<RecordProof> retainedExternalBindings, Map<String, String> inventoryFingerprints) {
        public Proof {
            releases = List.copyOf(releases); authorizations = List.copyOf(authorizations);
            retainedExternalBindings = List.copyOf(retainedExternalBindings);
            inventoryFingerprints = Map.copyOf(inventoryFingerprints);
        }
    }

    public enum Outcome { SETTLED_KNOWN_NO_EFFECT }
    record Receipt(String id, String customerId, String tenantId, String deploymentId, String targetProfileId,
                   String actorId, String commandFingerprint, Command command, Proof proof,
                   Outcome outcome, boolean reclamationPerformed, Instant settledAt, Reservation reservation,
                   List<UnusedConsumerDetachmentReceipt> consumerDetachments) { }

    /** Customer projection deliberately excludes full authorization, resource metadata and admission payloads. */
    public record Summary(String id, String reservationId, String generation, long fromRevision, long revision,
                          String deploymentId, String targetProfileId, Outcome outcome,
                          boolean reclamationPerformed, Instant settledAt, List<String> failedReleaseIds,
                          List<UnusedConsumerDetachmentReceipt> consumerDetachments) { }
}
