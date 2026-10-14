package com.ai.fabric.platform.backend.deployment.admission;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Trusted server adapter. Missing adapter denies admission; a health probe is not prerequisite proof. */
public interface AdmissionEvidenceSource {
    PrerequisiteReadback read(AdmissionContext context);
}
