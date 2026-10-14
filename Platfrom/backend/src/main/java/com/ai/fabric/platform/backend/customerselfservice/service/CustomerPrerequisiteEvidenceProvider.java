package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.PrerequisiteEvidence;

/** Read durable server-side evidence only: no provider/canary I/O under command locks.
 * Capacity must already be durably reserved for this operation. */
public interface CustomerPrerequisiteEvidenceProvider {
    PrerequisiteEvidence resolve(AdmissionContext context);
}
