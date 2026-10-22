package com.ai.fabric.platform.backend.deployment.admission;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation;

/** Explicit consent to enter normal retirement, never a drain or capacity-release receipt. */
public final class CustomerStagingReturnContracts {
    private CustomerStagingReturnContracts() { }

    public static final String ACKNOWLEDGEMENT = "RETURN_STAGING_WITH_DOWNTIME_AND_DISPOSABLE_AI_STATE_V1";
    public static final String RECOVERY_WAIVER = "WAIVE_STAGING_RECOVERY_WINDOW_V1";

    public record Request(String allocationId, long expectedRevision, String operationKey, String reason, String acknowledgement,
                          String recoveryWindowAcknowledgement) { }

    record Receipt(String commandFingerprint, Request request, String actorId, String offerId,
                   long targetEpoch, Reservation original, Reservation claimed) {
        boolean waivesRecoveryWindow() { return RECOVERY_WAIVER.equals(request.recoveryWindowAcknowledgement()); }
    }
}
