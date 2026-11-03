package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;

/** Optional read-only Console diagnostics beside Desk's authoritative ticket and transcript views. */
@Service
public class SupportDeskContextService {
    private final SupportDeskAuthorization authorization;
    private final SupportSettings settings;
    private final SupportIntegrationContextStore contexts;
    private final SupportTicketService tickets;
    private final SupportResourceService resources;
    private final PlatformCustomerRepository customers;
    public SupportDeskContextService(SupportDeskAuthorization authorization, SupportSettings settings,
        SupportIntegrationContextStore contexts, SupportTicketService tickets,
        SupportResourceService resources, PlatformCustomerRepository customers) {
        this.authorization = authorization; this.settings = settings; this.contexts = contexts;
        this.tickets = tickets; this.resources = resources; this.customers = customers;
    }
    @Transactional
    public Response handle(Request input, HttpHeaders headers) {
        if (input == null || input.schemaVersion() != 1 || input.operation() != Operation.LOOKUP || input.command() != null)
            throw SupportPolicy.bad("SUPPORT_DESK_READ_ONLY_CONTEXT");
        authorization.callback(headers, input);
        var c = contexts.byPrincipal(settings.deskBindingId(), input.scope().ownerRef(), true).orElse(null);
        if (c == null) return new Response(1, input.scope(), State.NOT_LINKED, null);
        var ticket = tickets.getFor(c);
        if (!ticket.path("conversation").path("conversationId").asText().equals(input.scope().conversationId()))
            return new Response(1, input.scope(), State.NOT_LINKED, null);
        resources.requireOwned(c.requester(), c.resources());
        String account = customers.findById(c.requester().customerId()).orElseThrow(SupportPolicy::denied).getName();
        var consent = new SupportDeskContracts.Consent(c.consent().granted() ? "GIVEN" : c.consent().revokedAt() == null ? "DECLINED" : "REVOKED",
            "Read-only status of this request's selected resources.", c.consent().changedAt());
        List<Observation> observations = !c.consent().granted() || !"OPEN".equals(ticket.path("status").asText()) ? List.of()
            : c.resources().stream().map(ref -> {
                var value = resources.read(c.requester(), ref);
                return new Observation(ref.type().name(), value.availability().name(),
                    "Recorded status only. Upstream services have not been probed.", Instant.now());
            }).toList();
        var context = new SupportDeskContracts.Context(ticket.path("reference").asText(), ticket.path("subject").asText(),
            ticket.path("status").asText(), c.revision(), account,
            c.resources().stream().map(r -> r.type().name() + ": " + r.id()).toList(), consent, observations, null);
        return new Response(1, input.scope(), State.AVAILABLE, context);
    }
}
