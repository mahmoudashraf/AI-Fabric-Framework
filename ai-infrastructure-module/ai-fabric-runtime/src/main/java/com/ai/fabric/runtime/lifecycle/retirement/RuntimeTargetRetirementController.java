package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/private/runtime-target-retirement")
public class RuntimeTargetRetirementController {
    public static final String READ_SCOPE = "runtime:target-retirement:read";
    public static final String WRITE_SCOPE = "runtime:target-retirement:write";
    public static final String COLD_MAINTENANCE_WRITE_SCOPE = "runtime:target-retirement:cold-maintenance:write";
    private final RuntimeRequestAuthResolver auth;
    private final RuntimeTargetRetirementIdentity identity;
    private final RuntimeTargetRetirementService service;
    private final RuntimeTargetIndexRetirementService indexes;

    public RuntimeTargetRetirementController(RuntimeRequestAuthResolver auth,
        RuntimeTargetRetirementIdentity identity, RuntimeTargetRetirementService service,
        RuntimeTargetIndexRetirementService indexes) {
        this.auth = auth;
        this.identity = identity;
        this.service = service;
        this.indexes = indexes;
    }

    @PostMapping("/commands")
    public ResponseEntity<CommandResult> command(@RequestBody Command command, HttpServletRequest request) {
        authorize(request, WRITE_SCOPE);
        return response(service.command(command));
    }
    @PostMapping("/generation-transitions")
    public ResponseEntity<GenerationTransitionResult> transition(@RequestBody GenerationTransition command,
        HttpServletRequest request) {
        authorize(request, WRITE_SCOPE);
        return response(service.transition(command));
    }
    @PostMapping("/cold-maintenance-generation-transitions")
    public ResponseEntity<ColdMaintenanceTransitionResult> coldMaintenanceTransition(
        @RequestBody ColdMaintenanceTransition command, HttpServletRequest request) {
        RuntimeResolvedIdentity caller;
        try {
            caller = authorize(request, COLD_MAINTENANCE_WRITE_SCOPE);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PRIVATE_COLD_MAINTENANCE_SYSTEM_SCOPE_REQUIRED");
            }
            throw exception;
        }
        if (caller.getAuthContext().getSubjectType() != RuntimeAuthSubjectType.SYSTEM_PROCESS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PRIVATE_COLD_MAINTENANCE_SYSTEM_SCOPE_REQUIRED");
        }
        return response(service.coldMaintenanceTransition(command));
    }
    @PostMapping("/status")
    public ResponseEntity<Readback> status(@RequestBody Target target, HttpServletRequest request) {
        authorize(request, READ_SCOPE);
        return response(service.status(target));
    }
    @PostMapping("/snapshots")
    public ResponseEntity<Snapshot> snapshot(@RequestBody Binding binding, HttpServletRequest request) {
        authorize(request, READ_SCOPE);
        return response(service.snapshot(binding));
    }
    @PostMapping("/snapshots/{snapshotId}/readback")
    public ResponseEntity<Snapshot> readback(@PathVariable String snapshotId,
        @RequestBody Binding binding, HttpServletRequest request) {
        authorize(request, READ_SCOPE);
        return response(service.readSnapshot(snapshotId, binding));
    }
    @PostMapping("/index-retirements")
    public ResponseEntity<IndexRetirementReadback> retireIndex(
        @RequestBody IndexRetirementCommand command, HttpServletRequest request) {
        authorize(request, WRITE_SCOPE);
        return response(indexes.retire(command));
    }
    @PostMapping("/index-retirements/readback")
    public ResponseEntity<IndexRetirementReadback> indexReadback(
        @RequestBody IndexRetirementCommand command, HttpServletRequest request) {
        authorize(request, READ_SCOPE);
        return response(indexes.readback(command));
    }

    private RuntimeResolvedIdentity authorize(HttpServletRequest request, String scope) {
        RuntimeResolvedIdentity caller = auth.resolveVerifiedPrivateContext(request, "runtime target retirement");
        auth.requireScope(caller, scope, "runtime target retirement");
        if (caller == null || caller.getAuthContext() == null
            || caller.getAuthContext().getAuthMode() != RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED
            || (caller.getAuthContext().getSubjectType() != RuntimeAuthSubjectType.TRUSTED_BACKEND
                && caller.getAuthContext().getSubjectType() != RuntimeAuthSubjectType.SYSTEM_PROCESS)
            || caller.getAuthContext().getCallerType() != RuntimeAuthCallerType.TRUSTED_BACKEND
            || !java.util.Objects.equals(identity.target().deploymentId(), caller.getAuthContext().getDeploymentId())
            || !java.util.Objects.equals(identity.tenantId(), caller.getAuthContext().getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PRIVATE_RETIREMENT_SERVER_SCOPE_REQUIRED");
        }
        return caller;
    }
    private <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().header("Cache-Control", "no-store").body(value); }
}
