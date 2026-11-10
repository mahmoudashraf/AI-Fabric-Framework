package com.ai.fabric.runtime.tasks;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ManagedTaskController.PATH)
public final class ManagedTaskController {
    public static final String PATH = "/api/tasks/v1/tasks";
    private final RuntimeRequestAuthResolver auth;
    private final ManagedTaskService tasks;

    public ManagedTaskController(RuntimeRequestAuthResolver auth, ManagedTaskService tasks) {
        this.auth = auth; this.tasks = tasks;
    }

    @GetMapping
    public ResponseEntity<ManagedTaskService.TaskCatalogue> catalogue(HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(tasks.catalogue(auth.resolveVerifiedPrivateContext(request, "managed tasks")));
    }

    @PostMapping("/{taskCode}/execute")
    public ResponseEntity<ManagedTaskResponse> execute(@PathVariable String taskCode,
        @RequestBody ManagedTaskRequest body, HttpServletRequest request) {
        return tasks.execute(taskCode, body, auth.resolveVerifiedPrivateContext(request, "managed tasks"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Error> malformed() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
            .body(new Error("TASK_REQUEST_INVALID", "Provide input and an optional correlationId only."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> rejected(ResponseStatusException failure) {
        // Never forward decoder/assertion exception messages or submitted values.
        String code = switch (failure.getStatusCode().value()) {
            case 401 -> "TASK_AUTHENTICATION_REQUIRED";
            case 403 -> "TASK_ACCESS_DENIED";
            case 404 -> "TASK_NOT_FOUND";
            case 413 -> "TASK_INPUT_LIMIT";
            case 503 -> "TASKS_NOT_READY";
            default -> "TASK_INPUT_INVALID";
        };
        return ResponseEntity.status(failure.getStatusCode()).header("Cache-Control", "no-store")
            .body(new Error(code, "The managed task request could not be accepted."));
    }

    public record Error(String code, String message) {}
}
