package com.loomai.inbox.web;

import com.loomai.inbox.service.ManagedDependencyLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/loomai-managed-dependency/v1")
public class ManagedDependencyLifecycleController {
    private final ManagedDependencyLifecycleService lifecycle;
    public ManagedDependencyLifecycleController(ManagedDependencyLifecycleService lifecycle) { this.lifecycle = lifecycle; }
    @PostMapping("/lifecycle")
    public ManagedDependencyLifecycleService.Readback lifecycle(
        @RequestHeader(value = "X-LoomAI-Managed-Dependency-Key", required = false) String key,
        @RequestBody ManagedDependencyLifecycleService.Request request) {
        if (!lifecycle.authorized(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MANAGED_DEPENDENCY_AUTH_REQUIRED");
        return lifecycle.execute(request);
    }
}
