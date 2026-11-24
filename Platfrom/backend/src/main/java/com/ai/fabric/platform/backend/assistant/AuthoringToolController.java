package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/** Exact internal actions authenticate themselves before reading a work scope or draft. */
@RestController
@RequestMapping("/api/internal/configuration-authoring/{sessionId}/{turnId}")
public class AuthoringToolController {
    private final AuthoringToolAuthentication authentication;
    private final AuthoringDraftOperations operations;
    public AuthoringToolController(AuthoringToolAuthentication authentication, AuthoringDraftOperations operations) {
        this.authentication = authentication; this.operations = operations;
    }
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @PostMapping("/authoring_get_context")
    public JsonNode context(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers) {
        return operations.context(authentication.verify(headers, sessionId, turnId, "authoring_get_context"));
    }
    @PostMapping("/authoring_read_selected_sources")
    public JsonNode sources(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers) {
        return operations.selectedSources(authentication.verify(headers, sessionId, turnId, "authoring_read_selected_sources"));
    }
    @PostMapping("/authoring_check_candidate")
    public JsonNode check(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        return operations.check(authentication.verify(headers, sessionId, turnId, "authoring_check_candidate"), body.path("changes"));
    }
    @PostMapping("/authoring_get_work_status")
    public JsonNode status(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers) {
        return operations.status(authentication.verify(headers, sessionId, turnId, "authoring_get_work_status"));
    }
    @PostMapping("/authoring_update_draft")
    public JsonNode save(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        return operations.save(authentication.verify(headers, sessionId, turnId, "authoring_update_draft"), body.path("operationId").asText(), body.path("changes"));
    }
    @PostMapping("/authoring_undo_request")
    public JsonNode undo(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        return operations.undo(authentication.verify(headers, sessionId, turnId, "authoring_undo_request"), body.path("operationId").asText(), body.path("savedOperationId").asText());
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> rejected(org.springframework.web.server.ResponseStatusException failure) {
        if (failure.getStatusCode().value() != 400) throw failure;
        // A structural/validation rejection rolls back the short transaction. Never
        // expose raw provider exceptions or credential-bearing values as AI feedback.
        return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of(
            "code", "AUTHORING_VALIDATION_REJECTED", "committed", false,
            "findings", java.util.List.of(java.util.Map.of("code", "CANDIDATE_CONTRACT_INVALID",
                "message", "Read the selected resource contracts and correct the proposed fields."))));
    }
}
