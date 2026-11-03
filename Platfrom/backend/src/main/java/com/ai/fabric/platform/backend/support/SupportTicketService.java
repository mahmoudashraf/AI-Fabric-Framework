package com.ai.fabric.platform.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Console host adapter. Desk's configured provider owns every ticket lifecycle transition. */
@Service
public class SupportTicketService {
    private final SupportTicketClient client;
    private final SupportRequesterService requesters;
    private final SupportIntegrationContextStore contexts;
    private final SupportResourceService resources;
    private final SupportSettings settings;
    private final SupportConversationScopeService conversations;

    public SupportTicketService(SupportTicketClient client, SupportRequesterService requesters,
        SupportIntegrationContextStore contexts, SupportResourceService resources, SupportSettings settings,
        SupportConversationScopeService conversations) {
        this.client = client; this.requesters = requesters; this.contexts = contexts;
        this.resources = resources; this.settings = settings; this.conversations = conversations;
    }

    public record Create(@NotBlank @Size(max=240) String subject, @Size(max=12000) String details,
                         @Size(max=80) String topic, @Size(max=160) String existingConversationId) { }
    public record Version(@NotBlank @Size(max=2048) String expectedVersion) { }
    public record Seen(@NotBlank @Size(max=2048) String expectedVersion, Instant seenThrough) { }
    public record ContextInput(@NotNull @Size(max=8) List<@Valid ResourceRef> resources,
                               @PositiveOrZero long expectedRevision) { }
    public record ContextView(String ticketId, List<ResourceRef> resources, Consent consent, long revision) { }

    public JsonNode session() { settings.requireEnabled(); return client.request(requesters.current(), "GET", "/session", null, null); }

    public JsonNode list(String status, String cursor, int limit) {
        settings.requireEnabled();
        if (limit < 1 || limit > 50 || cursor != null && cursor.length() > 16384
            || status != null && !Set.of("OPEN", "RESOLVED", "CANCELLED").contains(status)) throw SupportPolicy.bad("SUPPORT_INVALID_PAGE");
        String query = "/requests?limit=" + limit + (status == null ? "" : "&status=" + status)
            + (cursor == null ? "" : "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        return client.request(requesters.current(), "GET", query, null, null);
    }

    @Transactional
    public JsonNode create(Create input, String key) {
        settings.requireEnabled();
        var owner = requesters.current();
        if (input.existingConversationId() != null) throw SupportPolicy.bad("SUPPORT_CONVERSATION_PROOF_REQUIRED");
        var ticket = client.request(owner, "POST", "/requests", input, key);
        ensureContext(owner, ticket);
        return ticket;
    }

    @Transactional
    public JsonNode createForConversation(Create input, String key, SupportConversationScopeService.Scope scope, Map<String,String> headers) {
        settings.requireEnabled(); requesters.requireActive(scope.requester());
        if (!scope.conversationId().equals(input.existingConversationId())) throw SupportPolicy.denied();
        var ticket = client.request(scope.requester(), "POST", "/requests", input, key, headers, scope.runtimePrincipal(), scope.conversationId());
        ensureContext(scope.requester(), ticket);
        return ticket;
    }

    public JsonNode listFor(Requester owner) {
        requesters.requireActive(owner); return client.request(owner, "GET", "/requests?limit=20", null, null);
    }

    public JsonNode getFor(Requester owner, String id) {
        requesters.requireActive(owner); return client.request(owner, "GET", path(id), null, null);
    }

    @Transactional
    public JsonNode get(String id) {
        var owner = requesters.current();
        var ticket = client.request(owner, "GET", path(id), null, null);
        ensureContext(owner, ticket);
        return ticket;
    }

    @Transactional
    public JsonNode change(String id, String operation, Version input, String key) {
        if (!Set.of("resolve", "cancel", "reopen").contains(operation)) throw SupportPolicy.bad("SUPPORT_UNKNOWN_OPERATION");
        var c = context(id, true);
        var result = client.request(c.requester(), "POST", path(id) + "/" + operation, input, key);
        if (c.consent().granted()) contexts.consent(c, false, Instant.now());
        return result;
    }

    public JsonNode start(String id, Version input, String key, Map<String, String> runtimeHeaders) {
        var c = context(id, false);
        return client.request(c.requester(), "POST", path(id) + "/start", input, key, runtimeHeaders);
    }

    public JsonNode conversationCommand(String id, String operation, Version input, String key, Map<String,String> headers) {
        if (!Set.of("episodes","activity").contains(operation)) throw SupportPolicy.bad("SUPPORT_UNKNOWN_OPERATION");
        var c = context(id, false);
        return client.request(c.requester(), "POST", path(id) + "/" + operation, input, key, headers);
    }

    public JsonNode seen(String id, Seen input, String key) {
        var c = context(id, false);
        return client.request(c.requester(), "PUT", path(id) + "/seen", input, key);
    }

    @Transactional
    public ContextView contextView(String id) { return view(context(id, false)); }

    @Transactional
    public ContextView updateContext(String id, ContextInput input) {
        var c = context(id, true);
        requireOpen(getFor(c));
        if (input.expectedRevision() != c.revision()) throw SupportPolicy.conflict("SUPPORT_CONTEXT_REVISION_CONFLICT");
        var refs = input.resources().stream().map(r -> new ResourceRef(Objects.requireNonNull(r.type()),
            SupportPolicy.required(r.id(), 128, "RESOURCE"))).distinct()
            .sorted(Comparator.comparing((ResourceRef r) -> r.type().name()).thenComparing(ResourceRef::id)).toList();
        resources.requireOwned(c.requester(), refs);
        if (!refs.equals(c.resources())) contexts.resources(c, refs, Instant.now());
        return view(context(id, false));
    }

    @Transactional
    public ContextView consent(String id, ConsentChange input) {
        var c = context(id, true);
        if (input.expectedVersion() != c.consent().version()) throw SupportPolicy.conflict("SUPPORT_CONSENT_VERSION_CONFLICT");
        if (input.granted()) { requireOpen(getFor(c)); resources.requireOwned(c.requester(), c.resources()); }
        if (input.granted() != c.consent().granted()) contexts.consent(c, input.granted(), Instant.now());
        return view(context(id, false));
    }

    public SupportIntegrationContextStore.Context context(String id, boolean lock) {
        settings.requireEnabled();
        var c = contexts.find(client.spaceId(), id, lock).orElseThrow(SupportPolicy::missing);
        requesters.requireCurrent(c.requester());
        return c;
    }

    public SupportIntegrationContextStore.Context contextByPrincipal(String principal, boolean lock) {
        return findContextByPrincipal(principal, lock).orElseThrow(SupportPolicy::denied);
    }

    public Optional<SupportIntegrationContextStore.Context> findContextByPrincipal(String principal, boolean lock) {
        settings.requireEnabled();
        var current = contexts.byPrincipal(client.spaceId(), principal, lock);
        current.ifPresent(c -> requesters.requireActive(c.requester()));
        return current;
    }

    public JsonNode getFor(SupportIntegrationContextStore.Context c) {
        requesters.requireActive(c.requester());
        if (!client.spaceId().equals(c.spaceId())) throw SupportPolicy.denied();
        return client.request(c.requester(), "GET", path(c.ticketId()), null, null);
    }

    public OwnedCase requireOwnedCase(String id) {
        return ownedConversation(id, null);
    }

    public OwnedCase requireOwnedConversation(String id, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) throw SupportPolicy.missing();
        return ownedConversation(id, conversationId);
    }

    private OwnedCase ownedConversation(String id, String requestedConversationId) {
        var c = context(id, false);
        var ticket = getFor(c);
        var b = settings.binding();
        var episode = ticket.path("episodes").get(ticket.path("episodes").size() - 1);
        if (requestedConversationId != null) {
            episode = null;
            for (var candidate : ticket.path("episodes")) {
                if (requestedConversationId.equals(candidate.path("conversationId").asText())) {
                    episode = candidate;
                    break;
                }
            }
            if (episode == null) throw SupportPolicy.missing();
        }
        if (episode == null || !episode.path("conversationId").isTextual()) throw SupportPolicy.unavailable();
        boolean historical = !episode.path("conversationId").asText().equals(ticket.path("conversation").path("conversationId").asText());
        return new OwnedCase(id, ticket.path("reference").asText(), b.deploymentId(), b.targetProfileId(), b.customerId(), b.tenantId(),
            c.runtimePrincipal(), episode.path("conversationId").asText(), episode.path("episodeId").asText(),
            episode.path("number").asInt(), false, null, null, DeliveryStatus.COMPLETED,
            historical ? CaseStatus.RESOLVED : CaseStatus.valueOf(ticket.path("status").asText()), b.issuer(), b.audience());
    }

    private void ensureContext(Requester owner, JsonNode ticket) {
        String id = ticket.path("id").asText();
        if (!id.matches("[A-Za-z0-9._:-]{1,128}")) throw SupportPolicy.unavailable();
        contexts.lockRequester(owner.platformUserId());
        requesters.requireActive(owner);
        var c = contexts.find(client.spaceId(), id, true).orElse(null);
        if (c == null) {
            String principal = ticket.path("runtimePrincipal").asText("support-ticket:" + id);
            if (!principal.equals("support-ticket:" + id)) {
                var scope = conversations.verifiedPrincipal(principal);
                if (!scope.requester().equals(owner) || !scope.conversationId().equals(ticket.path("conversation").path("conversationId").asText()))
                    throw SupportPolicy.denied();
            }
            contexts.insert(client.spaceId(), id, owner, principal, Instant.now());
        } else if (!c.requester().equals(owner)) throw SupportPolicy.missing();
    }

    public static void requireOpen(JsonNode ticket) {
        if (!"OPEN".equals(ticket.path("status").asText())) throw SupportPolicy.conflict("SUPPORT_REQUEST_CLOSED");
    }
    private static ContextView view(SupportIntegrationContextStore.Context c) {
        return new ContextView(c.ticketId(), c.resources(), c.consent(), c.revision());
    }
    private static String path(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._:-]{1,128}")) throw SupportPolicy.missing();
        return "/requests/" + id;
    }
}
