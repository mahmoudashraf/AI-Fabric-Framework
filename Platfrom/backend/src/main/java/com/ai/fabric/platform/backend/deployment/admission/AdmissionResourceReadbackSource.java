package com.ai.fabric.platform.backend.deployment.admission;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Must read actual provider inventory and assignment/session/confirmation/inbox work with generation fencing. */
public interface AdmissionResourceReadbackSource {
    ResourceReadback read(ReadbackContext context);

    /** Read-only eligibility evidence for replacing an unassigned, fully provisioned but unverified release. */
    default ResourceReadback readForVerificationReapply(ReadbackContext context) {
        throw new AdmissionFailure(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
            "VERIFICATION_REAPPLY_READBACK_UNAVAILABLE");
    }
}
