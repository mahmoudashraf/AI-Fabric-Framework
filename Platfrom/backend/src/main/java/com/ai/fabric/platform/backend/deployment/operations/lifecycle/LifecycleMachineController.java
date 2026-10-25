package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;

@RestController
@RequestMapping("/internal/v1/deployment-lifecycle")
public class LifecycleMachineController {
    private final LifecycleCommandExecutor executor;
    public LifecycleMachineController(LifecycleCommandExecutor executor) { this.executor = executor; }
    @PostMapping("/commands")
    public Receipt command(@Valid @RequestBody Command command) { return executor.submit(command); }
    @GetMapping("/commands/{commandId}")
    public Receipt command(@PathVariable String commandId) { return executor.poll(commandId); }
}
