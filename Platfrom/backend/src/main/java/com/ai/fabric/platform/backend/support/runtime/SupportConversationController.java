package com.ai.fabric.platform.backend.support.runtime;

import com.ai.fabric.platform.backend.support.SupportConversationScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/support")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class SupportConversationController {
    private final SupportRuntimeAccessService access;
    private final SupportWorkspaceService workspace;
    private final SupportConversationScopeService scopes;
    public SupportConversationController(SupportRuntimeAccessService access, SupportWorkspaceService workspace,
        SupportConversationScopeService scopes) { this.access = access; this.workspace = workspace; this.scopes = scopes; }
    @ModelAttribute void headers(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "private, no-store"); }
    @GetMapping("/workspace") public ResponseEntity<SupportRuntimeAccessService.Bootstrap> bootstrap(@RequestParam(required=false) String conversationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.bootstrapConversation(conversationId));
    }
    @PostMapping("/conversations") public ResponseEntity<SupportRuntimeAccessService.Bootstrap> create() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(access.newConversation());
    }
    @GetMapping("/conversations") public ResponseEntity<SupportConversationScopeService.History> history(
        @RequestParam(required=false) String cursor, @RequestParam(defaultValue="20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(scopes.history(cursor, limit));
    }
    @GetMapping("/conversations/{id}/context") public SupportConversationScopeService.ContextView context(@PathVariable String id) { return scopes.context(id); }
    @PutMapping("/conversations/{id}/context") public SupportConversationScopeService.ContextView update(@PathVariable String id,
        @jakarta.validation.Valid @RequestBody com.ai.fabric.platform.backend.support.SupportTicketService.ContextInput input) { return scopes.update(id, input); }
    @PutMapping("/conversations/{id}/context/consent") public SupportConversationScopeService.ContextView consent(@PathVariable String id,
        @jakarta.validation.Valid @RequestBody com.ai.fabric.platform.backend.support.SupportContracts.ConsentChange input) { return scopes.consent(id, input); }
    @GetMapping(value="/conversations/{scopeId}/runtime/api/chat/me/conversations/{conversationId}/events", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> events(@PathVariable String scopeId, @PathVariable String conversationId,
        @RequestHeader(value="Last-Event-ID",required=false) String lastEventId) {
        var owner = scopes.current(scopeId).requester();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
            .body(workspace.events(access.resolveConversation(scopeId), owner, conversationId, lastEventId));
    }
    @RequestMapping(value="/conversations/{scopeId}/runtime/api/{*route}", method={RequestMethod.GET,RequestMethod.POST})
    public ResponseEntity<JsonNode> exchange(@PathVariable String scopeId, @PathVariable String route,
        @RequestHeader(value="Idempotency-Key",required=false) String key, @RequestParam(defaultValue="0") long afterMessageSequence,
        @RequestBody(required=false) JsonNode body, HttpServletRequest request) {
        String normalized = route.startsWith("/") ? route.substring(1) : route;
        var result = workspace.exchange(access.resolveConversation(scopeId), request.getMethod(), normalized, body, key, afterMessageSequence);
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(result.body());
    }
}
