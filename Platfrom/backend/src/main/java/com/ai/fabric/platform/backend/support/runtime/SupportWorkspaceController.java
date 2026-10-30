package com.ai.fabric.platform.backend.support.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/support/cases/{caseId}")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class SupportWorkspaceController {
    private final SupportRuntimeAccessService access;
    private final SupportWorkspaceService workspace;
    public SupportWorkspaceController(SupportRuntimeAccessService access, SupportWorkspaceService workspace) {
        this.access = access; this.workspace = workspace;
    }
    @GetMapping("/workspace")
    public ResponseEntity<SupportRuntimeAccessService.Bootstrap> bootstrap(@PathVariable String caseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.bootstrap(caseId));
    }
    @PostMapping("/start")
    public ResponseEntity<SupportWorkspaceService.StartReceipt> start(@PathVariable String caseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workspace.start(caseId));
    }
    @GetMapping(value = "/runtime/api/chat/me/conversations/{conversationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> events(@PathVariable String caseId, @PathVariable String conversationId,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
            .body(workspace.events(caseId, conversationId, lastEventId));
    }
    @RequestMapping(value = "/runtime/api/{*route}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<JsonNode> exchange(@PathVariable String caseId, @PathVariable String route,
        @RequestHeader(value = "Idempotency-Key", required = false) String key,
        @RequestParam(defaultValue = "0") long afterMessageSequence,
        @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        String normalized = route.startsWith("/") ? route.substring(1) : route;
        var result = workspace.exchange(caseId, request.getMethod(), normalized, body, key, afterMessageSequence);
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(result.body());
    }
}
