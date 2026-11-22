package com.ai.fabric.platform.backend.support;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@Service
public class SupportDiagnosticService {
    public static final String CASE_SUMMARY = "support_get_case_summary";
    public static final Map<String, ResourceType> RESOURCE_ACTIONS = Map.of(
        "support_get_deployment_status", ResourceType.DEPLOYMENT,
        "support_get_engage_readiness", ResourceType.ENGAGE_APP,
        "support_get_connection_sync_status", ResourceType.CONNECTION);

    private final SupportDiagnosticAuthentication authentication;
    private final SupportTicketService tickets;
    private final SupportRequesterService requesters;
    private final SupportResourceService resources;
    private final SupportConversationScopeService conversations;

    public SupportDiagnosticService(SupportDiagnosticAuthentication authentication, SupportTicketService tickets,
                                     SupportRequesterService requesters, SupportResourceService resources, SupportConversationScopeService conversations) {
        this.authentication = authentication; this.tickets = tickets; this.requesters = requesters; this.resources = resources;
        this.conversations = conversations;
    }

    @Transactional
    public DiagnosticResult read(String action, HttpHeaders headers) {
        var caller = authentication.verify(headers);
        if (!CASE_SUMMARY.equals(action) && !RESOURCE_ACTIONS.containsKey(action)) throw SupportPolicy.bad("SUPPORT_UNKNOWN_ACTION");
        // A per-conversation principal proves scope even when a specialist trace omits conversationId.
        // The lock serializes accepted resource reads against local consent revocation.
        var found = tickets.findContextByPrincipal(caller.principal(), true);
        if (found.isEmpty()) return conversationRead(action, caller);
        return ticketRead(action, caller, found.orElseThrow());
    }
    private DiagnosticResult ticketRead(String action, SupportDiagnosticAuthentication.VerifiedCaller caller, SupportIntegrationContextStore.Context c) {
        var ticket = tickets.getFor(c);
        var conversation = ticket.path("conversation").path("conversationId").asText();
        if (caller.conversationId() != null && !caller.conversationId().equals(conversation)
            || !"OPEN".equals(ticket.path("status").asText()) || !c.consent().granted()) {
            throw SupportPolicy.denied();
        }
        requesters.requireActive(c.requester());
        resources.requireOwned(c.requester(), c.resources());
        if (CASE_SUMMARY.equals(action)) {
            return new DiagnosticResult(action, Instant.now(), Availability.CURRENT,
                new CaseDiagnostic(ticket.path("reference").asText(), ticket.path("subject").asText(),
                    ticket.path("topic").asText(null), CaseStatus.OPEN, c.consent(), c.resources()), List.of());
        }
        var selected = c.resources().stream().filter(r -> r.type() == RESOURCE_ACTIONS.get(action)).toList();
        var results = selected.stream().map(r -> resources.read(c.requester(), r, c.resources(), c.runtimePrincipal())).toList();
        var availability = results.isEmpty() ? Availability.NOT_APPLICABLE
            : results.stream().allMatch(r -> r.availability() == Availability.CURRENT) ? Availability.CURRENT : Availability.INCOMPLETE;
        return new DiagnosticResult(action, Instant.now(), availability, null, results);
    }
    private DiagnosticResult conversationRead(String action, SupportDiagnosticAuthentication.VerifiedCaller caller) {
        var scope = conversations.findPrincipal(caller.principal()).orElseThrow(SupportPolicy::denied);
        if (caller.conversationId() != null && !scope.conversationId().equals(caller.conversationId())) throw SupportPolicy.denied();
        var context = conversations.context(scope, true);
        // Adoption takes the same requester lock; ticket consent/lifecycle wins over casual context.
        var adopted = tickets.findContextByPrincipal(caller.principal(), true);
        if (adopted.isPresent()) return ticketRead(action, caller, adopted.orElseThrow());
        var selected = context.resources().stream().filter(r -> r.type() == RESOURCE_ACTIONS.get(action)).toList();
        if (CASE_SUMMARY.equals(action)) return required(action, "NO_LINKED_REQUEST", "This is a general support conversation, not a ticket. Documentation help and consented resource checks are available without creating a request.");
        if (selected.isEmpty()) return required(action, "SELECT_RESOURCE", "Choose the relevant resource in Configure diagnostics before checking its status. A support ticket is not required.");
        if (!context.consent().granted()) return required(action, "DIAGNOSTIC_CONSENT_REQUIRED", "Grant status-check permission in Configure diagnostics for the selected resources, then request the check again.");
        resources.requireOwned(scope.requester(), context.resources());
        var results = selected.stream().map(r -> resources.read(scope.requester(), r, context.resources(), scope.runtimePrincipal())).toList();
        return new DiagnosticResult(action, Instant.now(), results.stream().allMatch(r -> r.availability() == Availability.CURRENT)
            ? Availability.CURRENT : Availability.INCOMPLETE, null, results);
    }
    private static DiagnosticResult required(String action, String code, String message) {
        return new DiagnosticResult(action, Instant.now(), Availability.NOT_APPLICABLE, null, List.of(), new DiagnosticRequirement(code, message));
    }
}
