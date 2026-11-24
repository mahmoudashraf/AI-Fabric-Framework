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
    private AuthoringGoalService goals;
    public AuthoringToolController(AuthoringToolAuthentication authentication, AuthoringDraftOperations operations) {
        this.authentication = authentication; this.operations = operations;
    }
    @org.springframework.beans.factory.annotation.Autowired public void setGoals(AuthoringGoalService goals) { this.goals = goals; }
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @PostMapping("/authoring_get_context")
    public JsonNode context(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers,
        @RequestBody(required = false) AuthoringDraftOperations.ContextRead request) {
        return operations.context(authentication.verify(headers, sessionId, turnId, "authoring_get_context"), request);
    }
    @PostMapping("/authoring_read_selected_sources")
    public JsonNode sources(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers,
        @RequestBody(required = false) AuthoringDraftOperations.SourceRead request) {
        var caller = authentication.verify(headers, sessionId, turnId, "authoring_read_selected_sources");
        return request == null || caller.goalId() == null && request.equals(new AuthoringDraftOperations.SourceRead(null, null, null, null, null, null, false))
            ? operations.selectedSources(caller) : operations.selectedSources(caller, request);
    }
    @PostMapping("/authority") public JsonNode authority(@PathVariable String sessionId, @PathVariable String turnId,
        @RequestHeader HttpHeaders headers, @RequestBody AuthoringGoalService.Renewal request) {
        return goals.renew(authentication.verifyAuthority(headers, sessionId, turnId), request);
    }
    @PostMapping("/authoring_check_candidate")
    public JsonNode check(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        return operations.check(authentication.verify(headers, sessionId, turnId, "authoring_check_candidate"), body.path("changes"));
    }
    @PostMapping("/authoring_get_work_status")
    public JsonNode status(@PathVariable String sessionId, @PathVariable String turnId, @RequestHeader HttpHeaders headers,
        @RequestBody(required = false) AuthoringDraftOperations.ReceiptRead request) {
        var caller = authentication.verify(headers, sessionId, turnId, "authoring_get_work_status");
        return request == null || request.equals(new AuthoringDraftOperations.ReceiptRead(null, null, null))
            ? operations.status(caller) : operations.status(caller, request);
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
            "findings", java.util.List.of(java.util.Map.of("code", failure instanceof AuthoringContractException contract
                ? contract.code() : "CANDIDATE_CONTRACT_INVALID", "message", failure instanceof AuthoringContractException
                ? failure.getReason() : "Read the selected resource contracts and correct the proposed fields."))));
    }
}
