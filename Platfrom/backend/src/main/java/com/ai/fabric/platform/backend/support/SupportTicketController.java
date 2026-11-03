package com.ai.fabric.platform.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class SupportTicketController {
    private final SupportTicketService tickets;
    private final SupportConversationScopeService conversations;
    private final com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService runtime;
    public SupportTicketController(SupportTicketService tickets, SupportConversationScopeService conversations,
        com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService runtime) {
        this.tickets = tickets; this.conversations = conversations; this.runtime = runtime;
    }
    @ModelAttribute void headers(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
    @GetMapping("/session") public JsonNode session() { return tickets.session(); }
    @GetMapping("/requests") public JsonNode list(@RequestParam(required=false) String status,
        @RequestParam(required=false) String cursor, @RequestParam(defaultValue="20") int limit) { return tickets.list(status, cursor, limit); }
    @PostMapping("/requests") public JsonNode create(@Valid @RequestBody SupportTicketService.Create input,
        @RequestHeader("Idempotency-Key") String key) {
        if (input.existingConversationId() == null) return tickets.create(input, key);
        var scope = conversations.current(input.existingConversationId());
        return tickets.createForConversation(input, key, scope, runtime.resolveConversation(scope.conversationId()).headers());
    }
    @GetMapping("/requests/{id}") public JsonNode get(@PathVariable String id) { return tickets.get(id); }
    @PostMapping("/requests/{id}/{operation:resolve|cancel|reopen}")
    public JsonNode change(@PathVariable String id, @PathVariable String operation,
        @Valid @RequestBody SupportTicketService.Version input, @RequestHeader("Idempotency-Key") String key) {
        return tickets.change(id, operation, input, key);
    }
    @PutMapping("/requests/{id}/seen") public JsonNode seen(@PathVariable String id,
        @Valid @RequestBody SupportTicketService.Seen input, @RequestHeader("Idempotency-Key") String key) { return tickets.seen(id, input, key); }
    @GetMapping("/requests/{id}/context") public SupportTicketService.ContextView context(@PathVariable String id) {
        return tickets.contextView(id);
    }
    @PutMapping("/requests/{id}/context") public SupportTicketService.ContextView update(@PathVariable String id,
        @Valid @RequestBody SupportTicketService.ContextInput input) { return tickets.updateContext(id, input); }
    @PutMapping("/requests/{id}/context/consent") public SupportTicketService.ContextView consent(@PathVariable String id,
        @Valid @RequestBody SupportContracts.ConsentChange input) { return tickets.consent(id, input); }
}
