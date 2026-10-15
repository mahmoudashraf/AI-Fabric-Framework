package com.loomai.verification.vehicleprovider.config;

import com.loomai.verification.vehicleprovider.service.ManagedDependencyLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
