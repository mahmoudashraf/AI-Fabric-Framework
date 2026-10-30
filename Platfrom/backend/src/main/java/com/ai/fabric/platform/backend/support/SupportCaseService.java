package com.ai.fabric.platform.backend.support;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@Service
public class SupportCaseService {
    private final SupportCaseStore store;
    private final SupportRequesterService requesters;
    private final SupportResourceService resources;
    private final SupportSettings settings;

    public SupportCaseService(SupportCaseStore store, SupportRequesterService requesters,
                               SupportResourceService resources, SupportSettings settings) {
        this.store = store; this.requesters = requesters; this.resources = resources; this.settings = settings;
    }

    @Transactional
    public CaseView create(CreateCase input, String submissionKey) {
        settings.requireEnabled();
        if (input == null) throw SupportPolicy.bad("SUPPORT_INTAKE_REQUIRED");
        var owner = requesters.current();
        String key = SupportPolicy.required(submissionKey, 128, "IDEMPOTENCY_KEY");
        var normalized = new CreateCase(SupportPolicy.required(input.summary(), 240, "SUMMARY"),
            SupportPolicy.optional(input.details(), 12000, "DETAILS"), SupportPolicy.optional(input.topic(), 80, "TOPIC"),
            normalizeResources(input.resources()), input.diagnosticConsent());
        // Serialize creates for this existing member, including duplicate submissions from different tabs.
        store.lockRequester(owner.platformUserId());
        requesters.requireActive(owner);
        String digest = digest(store.write(normalized));
        var replay = store.replay(owner, key).orElse(null);
        if (replay != null) {
            requesters.requireCurrent(replay.requester());
            if (!replay.submissionHash().equals(digest)) throw SupportPolicy.conflict("SUPPORT_SUBMISSION_KEY_REUSED");
            return view(replay);
        }
        resources.requireOwned(owner, normalized.resources());
        var binding = settings.binding();
        var now = Instant.now();
        String id = "sup-" + UUID.randomUUID();
        var c = new CaseRecord(id, "SUP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT), owner,
            "support-" + UUID.randomUUID(), key, digest, normalized.summary(), normalized.details(), normalized.topic(),
            normalized.resources(), initialMessage(normalized), "support-intake-" + UUID.randomUUID(), DeliveryStatus.PENDING,
            null, CaseStatus.OPEN, new Consent(normalized.diagnosticConsent(), 1, now, null), 0, now, now, null);
        store.insert(c);
        store.insertEpisode(newEpisode(c.id(), 1, binding, now));
        store.event(c, "CREATED", owner.platformUserId(), now);
        return view(store.find(c.id(), false).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<CaseView> list(int page, int size) {
        settings.requireEnabled();
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw SupportPolicy.bad("SUPPORT_INVALID_PAGE");
        var owner = requesters.current();
        return store.list(owner, size, page * size).stream().map(c -> {
            requesters.requireCurrent(c.requester()); return view(c);
        }).toList();
    }

    @Transactional(readOnly = true)
    public CaseView get(String caseId) { return view(own(caseId, false)); }

    @Transactional
    public CaseView update(String caseId, UpdateCase input) {
        var c = own(caseId, true);
        open(c);
        if (input == null) throw SupportPolicy.bad("SUPPORT_UPDATE_REQUIRED");
        revision(c, input.expectedRevision());
        var refs = normalizeResources(input.resources());
        resources.requireOwned(c.requester(), refs);
        var now = Instant.now();
        // New resources require fresh purpose-specific consent, never an inherited previous grant.
        if (!refs.equals(c.resources())) store.consent(c.id(), false, now);
        store.context(c.id(), SupportPolicy.optional(input.topic(), 80, "TOPIC"), refs, now);
        return changed(c.id(), "CONTEXT_UPDATED", c.requester().platformUserId(), now);
    }

    @Transactional
    public CaseView consent(String caseId, ConsentChange change) {
        var c = own(caseId, true);
        if (change == null) throw SupportPolicy.bad("SUPPORT_CONSENT_REQUIRED");
        if (change.expectedVersion() != c.consent().version()) throw SupportPolicy.conflict("SUPPORT_CONSENT_VERSION_CONFLICT");
        if (change.granted()) { open(c); resources.requireOwned(c.requester(), c.resources()); }
        var now = Instant.now();
        store.consent(c.id(), change.granted(), now);
        return changed(c.id(), change.granted() ? "CONSENT_GRANTED" : "CONSENT_REVOKED", c.requester().platformUserId(), now);
    }

    @Transactional
    public CaseView resolve(String caseId, long expectedRevision) { return close(caseId, expectedRevision, CaseStatus.RESOLVED); }
    @Transactional
    public CaseView cancel(String caseId, long expectedRevision) { return close(caseId, expectedRevision, CaseStatus.CANCELLED); }

    private CaseView close(String id, long expectedRevision, CaseStatus status) {
        var c = own(id, true);
        if (c.status() == status) return view(c);
        revision(c, expectedRevision); open(c);
        var now = Instant.now();
        store.consent(id, false, now);
        store.status(id, status, now);
        return changed(id, status.name(), c.requester().platformUserId(), now);
    }

    @Transactional
    public CaseView reopen(String id, long expectedRevision) {
        var c = own(id, true);
        var latest = latest(c);
        if (c.status() == CaseStatus.OPEN && !latest.terminal()) return view(c);
        revision(c, expectedRevision);
        var now = Instant.now();
        if (latest.terminal()) {
            store.insertEpisode(newEpisode(id, latest.number() + 1, settings.binding(), now));
        }
        // Reopening never restores diagnostic consent or sends the original form again.
        if (c.consent().granted()) store.consent(id, false, now);
        store.status(id, CaseStatus.OPEN, now);
        return changed(id, "REOPENED", c.requester().platformUserId(), now);
    }

    @Transactional(readOnly = true)
    public ConversationAccess requireConversationAccess(String caseId, String conversationId) {
        var c = own(caseId, false);
        var episode = conversationId == null ? latest(c) : store.episode(c.id(), conversationId).orElseThrow(SupportPolicy::missing);
        if (!episode.binding().equals(settings.binding())) throw SupportPolicy.conflict("SUPPORT_EPISODE_BINDING_CHANGED");
        return new ConversationAccess(c, episode);
    }

    @Transactional(readOnly = true)
    public OwnedCase requireOwnedCase(String caseId) {
        var access = requireConversationAccess(caseId, null);
        var c = access.supportCase();
        var e = access.episode();
        var b = e.binding();
        return new OwnedCase(c.id(), c.reference(), b.deploymentId(), b.targetProfileId(), b.customerId(), b.tenantId(),
            c.runtimePrincipal(), e.conversationId(), e.id(), e.number(), e.terminal(), c.initialMessageKey(),
            c.initialMessage(), c.deliveryStatus(), c.status(), b.issuer(), b.audience());
    }

    @Transactional(readOnly = true)
    public InitialMessage initialMessageForDelivery(String caseId) {
        var c = own(caseId, false); open(c);
        var episode = latest(c);
        if (episode.number() != 1 || episode.terminal()) throw SupportPolicy.conflict("SUPPORT_INTAKE_EPISODE_CLOSED");
        if (!episode.binding().equals(settings.binding())) throw SupportPolicy.conflict("SUPPORT_EPISODE_BINDING_CHANGED");
        return new InitialMessage(c.id(), episode.id(), episode.conversationId(), c.initialMessage(), c.initialMessageKey(), c.deliveryStatus());
    }

    /** Adapter hook after an actual runtime response/readback, never an HTTP customer mutation. */
    @Transactional
    public void observeInitialDelivery(String caseId, String episodeId, DeliveryStatus observation) {
        var c = own(caseId, true);
        var first = store.episodes(c.id()).getFirst();
        if (!first.id().equals(episodeId) || observation == null || observation == DeliveryStatus.PENDING) throw SupportPolicy.bad("SUPPORT_INVALID_DELIVERY_OBSERVATION");
        if (c.deliveryStatus() == DeliveryStatus.COMPLETED || c.deliveryStatus() == DeliveryStatus.FAILED || c.deliveryStatus() == observation) return;
        if (c.deliveryStatus() == DeliveryStatus.USER_MESSAGE_PERSISTED && observation == DeliveryStatus.UNKNOWN) return;
        var now = Instant.now();
        store.delivery(c.id(), observation, now);
        store.event(c, "INTAKE_" + observation.name(), c.requester().platformUserId(), now);
    }

    /** Called only after the adapter observes the runtime's authoritative terminal state. */
    @Transactional
    public void observeConversationTerminal(String caseId, String conversationId) {
        var c = own(caseId, true);
        var episode = store.episode(c.id(), conversationId).orElseThrow(SupportPolicy::missing);
        if (!episode.terminal()) {
            store.terminal(episode.id());
            store.event(c, "CONVERSATION_TERMINAL", c.requester().platformUserId(), Instant.now());
        }
    }

    private CaseRecord own(String id, boolean lock) {
        settings.requireEnabled();
        var c = store.find(id, lock).orElseThrow(SupportPolicy::missing);
        requesters.requireCurrent(c.requester());
        return c;
    }
    private Episode latest(CaseRecord c) { return store.episodes(c.id()).getLast(); }
    private CaseView changed(String id, String type, String actor, Instant now) {
        var c = store.find(id, false).orElseThrow(); store.event(c, type, actor, now); return view(c);
    }
    private CaseView view(CaseRecord c) {
        return new CaseView(c.id(), c.reference(), c.summary(), c.details(), c.topic(), c.resources(), c.status(), c.consent(),
            c.revision(), c.deliveryStatus(), c.deliveryObservedAt(), store.episodes(c.id()).stream()
                .map(e -> new EpisodeSummary(e.id(), e.number(), e.conversationId(), e.terminal(), e.createdAt())).toList(),
            c.createdAt(), c.updatedAt(), c.closedAt());
    }
    private static Episode newEpisode(String caseId, int number, RuntimeBinding binding, Instant now) {
        return new Episode("se-" + UUID.randomUUID(), caseId, number, binding, "chat-" + UUID.randomUUID(), false, now);
    }
    private static void open(CaseRecord c) { if (c.status() != CaseStatus.OPEN) throw SupportPolicy.conflict("SUPPORT_CASE_CLOSED"); }
    private static void revision(CaseRecord c, long expected) {
        if (expected != c.revision()) throw SupportPolicy.conflict("SUPPORT_CASE_REVISION_CONFLICT");
    }
    private static List<ResourceRef> normalizeResources(List<ResourceRef> refs) {
        if (refs == null) return List.of();
        if (refs.size() > 8) throw SupportPolicy.bad("SUPPORT_RESOURCE_LIMIT");
        return refs.stream().map(r -> {
            if (r == null || r.type() == null) throw SupportPolicy.bad("SUPPORT_INVALID_RESOURCE");
            return new ResourceRef(r.type(), SupportPolicy.required(r.id(), 128, "RESOURCE"));
        }).distinct().sorted(Comparator.comparing((ResourceRef r) -> r.type().name()).thenComparing(ResourceRef::id)).toList();
    }
    private static String initialMessage(CreateCase input) {
        return input.summary() + (input.details() == null ? "" : "\n\n" + input.details());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Cannot fingerprint support intake", e); }
    }
}
