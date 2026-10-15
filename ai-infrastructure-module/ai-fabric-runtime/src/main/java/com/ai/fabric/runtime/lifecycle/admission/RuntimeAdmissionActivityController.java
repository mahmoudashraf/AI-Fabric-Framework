package com.ai.fabric.runtime.lifecycle.admission;

import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.Target;
import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@RestController
@RequestMapping("/api/private/runtime-admission-activity")
public class RuntimeAdmissionActivityController {
    public static final String READ_SCOPE = "runtime:admission-activity:read";
    private final RuntimeRequestAuthResolver auth;
    private final RuntimeTargetRetirementIdentity identity;
    private final RuntimeAdmissionActivityService activity;

    public RuntimeAdmissionActivityController(RuntimeRequestAuthResolver auth, RuntimeTargetRetirementIdentity identity,
                                              RuntimeAdmissionActivityService activity) {
        this.auth = auth; this.identity = identity; this.activity = activity;
    }

    @PostMapping("/readback")
    public ResponseEntity<RuntimeAdmissionActivityService.Readback> readback(@RequestBody Target target, HttpServletRequest request) {
        RuntimeResolvedIdentity caller = auth.resolveVerifiedPrivateContext(request, "admission activity");
        auth.requireScope(caller, READ_SCOPE, "admission activity");
        RuntimeAuthContext context = caller == null ? null : caller.getAuthContext();
        if (context == null || context.getAuthMode() != RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED
            || context.getCallerType() != RuntimeAuthCallerType.TRUSTED_BACKEND
            || context.getSubjectType() != RuntimeAuthSubjectType.SYSTEM_PROCESS
            || !Objects.equals(identity.target().deploymentId(), context.getDeploymentId())
            || !Objects.equals(identity.tenantId(), context.getTenantId())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "PRIVATE_ACTIVITY_SERVER_SCOPE_REQUIRED");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(activity.read(target));
    }
}
