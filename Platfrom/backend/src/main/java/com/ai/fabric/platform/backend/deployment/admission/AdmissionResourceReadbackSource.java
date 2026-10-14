package com.ai.fabric.platform.backend.deployment.admission;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Must read actual provider inventory and assignment/session/confirmation/inbox work with generation fencing. */
public interface AdmissionResourceReadbackSource {
    ResourceReadback read(ReadbackContext context);
}
