package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    /** Resolve parser failures here without an unauthenticated servlet ERROR redispatch. */
    @RestControllerAdvice(assignableTypes = AdmissionUnusedReservationCancellationController.class)
    public static class InvalidCancellationRequestHandler {
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> invalidJson() {
            // Body conversion precedes method security; preserve the same staff-only boundary on this path.
            if (!PlatformSecurityContext.isAuthenticated()) return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized.");
            var role = PlatformSecurityContext.currentPrincipal().role();
            if (role != PlatformRole.PLATFORM_ADMIN && role != PlatformRole.PLATFORM_OPERATOR)
                return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden.");
            return error(HttpStatus.BAD_REQUEST, "UNUSED_CANCELLATION_REQUEST_INVALID",
                "Invalid unused-reservation cancellation request.");
        }

        private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
            return ResponseEntity.status(status).body(Map.of("success", false, "errorCode", code, "message", message));
        }
    }
}
