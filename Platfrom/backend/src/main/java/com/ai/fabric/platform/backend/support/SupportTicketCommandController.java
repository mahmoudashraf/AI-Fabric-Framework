package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

/** Connector endpoint. Model fields never select requester identity or runtime authority. */
@RestController
@RequestMapping("/api/integrations/support/ticket-commands")
public class SupportTicketCommandController {
    private final SupportDiagnosticAuthentication authentication;
    private final SupportRequesterAssertionService assertions;
    private final SupportConversationScopeService conversations;
    private final SupportTicketService tickets;
    private final SupportRuntimeAccessService runtime;
    public SupportTicketCommandController(SupportDiagnosticAuthentication authentication, SupportRequesterAssertionService assertions,
        SupportConversationScopeService conversations, SupportTicketService tickets, SupportRuntimeAccessService runtime) {
        this.authentication = authentication; this.assertions = assertions; this.conversations = conversations;
        this.tickets = tickets; this.runtime = runtime;
    }
    public record Command(@NotBlank String operation, @Size(max=128) String requestId,
        @Size(max=240) String subject, @Size(max=12000) String details, @Size(max=80) String topic) { }
    @PostMapping public ResponseEntity<JsonNode> command(@Valid @RequestBody Command input,
        @RequestHeader HttpHeaders headers, @RequestHeader("Idempotency-Key") String key) {
        if (!Set.of("CREATE","GET","LIST").contains(input.operation())) throw SupportPolicy.bad("SUPPORT_UNKNOWN_OPERATION");
        var caller = authentication.verify(headers, "X-LoomAI-Ticket-Connector-Key", assertions.connectorKey(),
            input.operation().equals("CREATE") ? "support:tickets:create" : "support:tickets:read");
        SupportContracts.Requester owner;
        SupportConversationScopeService.Scope scope;
        var linked = tickets.findContextByPrincipal(caller.principal(), false);
        if (linked.isPresent()) {
            var c = linked.orElseThrow(); owner = c.requester();
            var ticket = tickets.getFor(c);
            scope = new SupportConversationScopeService.Scope(ticket.path("conversation").path("conversationId").asText(), c.runtimePrincipal(), owner);
        } else {
            try { scope = conversations.verifiedPrincipal(caller.principal()); }
            catch (org.springframework.web.server.ResponseStatusException unknown) {
                if (unknown.getStatusCode().value() == 404) throw SupportPolicy.denied();
                throw unknown;
            }
            owner = scope.requester();
        }
        if (caller.conversationId() == null || !caller.conversationId().equals(scope.conversationId())) throw SupportPolicy.denied();
        JsonNode result = switch (input.operation()) {
            case "GET" -> tickets.getFor(owner, input.requestId());
            case "LIST" -> tickets.listFor(owner);
            default -> tickets.createForConversation(new SupportTicketService.Create(
                SupportPolicy.required(input.subject(),240,"SUBJECT"), input.details(), input.topic(), scope.conversationId()),
                key, scope, runtime.conversationAccess(scope).headers());
        };
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }
}
