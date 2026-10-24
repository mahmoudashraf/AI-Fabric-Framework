package com.ai.fabric.platform.backend.deployment.admission;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Trusted server adapter. Missing adapter denies admission; a health probe is not prerequisite proof. */
public interface AdmissionEvidenceSource {
    PrerequisiteReadback read(AdmissionContext context);

    /** Execution may obtain new provider evidence; callers must not hold admission locks. */
    default PrerequisiteReadback readForExecution(AdmissionContext context) { return read(context); }

    /** Only the ledger's exact active-production configuration path may use these proofs. */
    default PrerequisiteReadback readExistingConfiguration(AdmissionContext context) {
        throw new AdmissionFailure(org.springframework.http.HttpStatus.PRECONDITION_FAILED, "DEPENDENCY_CONFIGURATION_EVIDENCE_REQUIRED");
    }
    default PrerequisiteReadback readExistingConfigurationForExecution(AdmissionContext context) {
        return readExistingConfiguration(context);
    }
}
