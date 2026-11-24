package com.ai.fabric.platform.backend.assistant;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configuration-assistant/managed-brain")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ManagedAssistantController {
    private final ManagedAssistantRuntime runtime;
    public ManagedAssistantController(ManagedAssistantRuntime runtime) { this.runtime = runtime; }
    @PostMapping("/install")
    public Map<String, Object> install(@RequestBody ManagedAssistantRuntime.Install request) { return runtime.install(request); }
}
