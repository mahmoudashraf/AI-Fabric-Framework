package com.ai.fabric.platform.backend.deployment.admission;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Staff-attended compute stop, not graceful drain, resource deletion, or a resumable recovery engine. */
public final class AttendedStagingStopContracts {
    private AttendedStagingStopContracts() { }
    public record Request(@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = CancelUnusedFenceDeserializer.class)
                          Fence fence, String reason,
                          boolean acceptDowntimeVolatileStateLossAndWaiveRecoveryWindow) { }
    public record NativeResource(String kind, String uuid, String name, String marker, String serverUuid,
                                 String destinationUuid, String environmentId) { }
    public record Plan(String id, String actorId, AdmissionColdTerminationReview.Payload review,
                       String dependencyGraphId, long dependencyRevision, String dependencyFingerprint,
                       List<NativeResource> resources, Instant expiresAt) {
        public Plan { resources = List.copyOf(resources); }
    }
    public record Command(String fingerprint) { }
    public record Claim(String planId, String planFingerprint, String actorId, Fence fence, long targetEpoch) { }
    public record StoppedReadback(String planId, String planFingerprint, long targetEpoch,
                                  List<NativeResource> resources, Instant observedAt, String hostEvidenceReference) {
        public StoppedReadback { resources = List.copyOf(resources); }
    }
    public record Receipt(String id, String actorId, Claim claim, StoppedReadback readback,
                          Reservation reservation, Instant recordedAt) { }
    public record Summary(String planId, String fingerprint, String status, List<NativeResource> resources,
                          Claim claim, Receipt receipt, Instant expiresAt) { }
}
