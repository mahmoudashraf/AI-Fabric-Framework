package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

@RestController
@RequestMapping("/api/platform/deployments/{deploymentId}/targets/{targetProfileId}/admission-reservations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class AdmissionUnusedReservationCancellationController {
    private final DeploymentAdmissionService admissions;

    public AdmissionUnusedReservationCancellationController(DeploymentAdmissionService admissions) {
        this.admissions = admissions;
    }

    @PostMapping("/cancel-unused")
    public UnusedReservationCancellationReceipt cancel(@PathVariable String deploymentId,
                                                        @PathVariable String targetProfileId,
                                                        @RequestBody CancelUnusedReservation command) {
        return admissions.cancelUnusedReservation(deploymentId, targetProfileId, command);
    }
}
