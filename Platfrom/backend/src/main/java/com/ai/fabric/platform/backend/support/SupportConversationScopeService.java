package com.ai.fabric.platform.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Private host identity linkage only, independent of ticketing and transcript storage. */
@Service
public class SupportConversationScopeService {
    public record Scope(String conversationId, String runtimePrincipal, Requester requester) { }
    private final JdbcTemplate jdbc;
    private final SupportSettings settings;
    private final SupportRequesterService requesters;
    private final ObjectMapper mapper;
    private final SupportResourceService resources;
    public record ContextView(String conversationId, List<ResourceRef> resources, Consent consent, long revision) { }
    public record Summary(String conversationId, Instant createdAt) { }
    public record History(List<Summary> items, String nextCursor) { }
    public SupportConversationScopeService(JdbcTemplate jdbc, SupportSettings settings, SupportRequesterService requesters,
        ObjectMapper mapper, SupportResourceService resources) {
        this.jdbc = jdbc; this.settings = settings; this.requesters = requesters;
        this.mapper = mapper; this.resources = resources;
    }
    @Transactional
    public Scope create() {
        var owner = requesters.current(); settings.requireEnabled();
        lock(owner);
        String id = "chat-" + UUID.randomUUID();
        String principal = "support-conversation:" + UUID.randomUUID();
        jdbc.update("""
            insert into platform_support_conversation_scope
            (support_space_id,conversation_id,runtime_principal,customer_id,requester_user_id,requester_issuer,requester_subject,created_at,last_selected_at,consent_changed_at)
            values(?,?,?,?,?,?,?,?,?,?)
            """, settings.deskBindingId(), id, principal, owner.customerId(), owner.platformUserId(), owner.issuer(), owner.subject(),
            ts(Instant.now()), ts(Instant.now()), ts(Instant.now()));
        return new Scope(id, principal, owner);
    }
    @Transactional
    public Scope resume(String id) {
        var owner = requesters.current(); settings.requireEnabled(); lock(owner);
        if (id != null) {
            var selected = current(id);
            if (linked(selected)) throw SupportPolicy.conflict("SUPPORT_USE_REQUEST_CONVERSATION");
            jdbc.update("update platform_support_conversation_scope set last_selected_at=? where support_space_id=? and conversation_id=?",
                ts(Instant.now()), settings.deskBindingId(), id);
            return selected;
        }
        var ids = jdbc.queryForList("select s.conversation_id from platform_support_conversation_scope s where " + ownerWhere()
            + unlinked() + " order by s.last_selected_at desc, s.created_at desc, s.conversation_id desc limit 1",
            String.class, ownerArgs(owner));
        return ids.isEmpty() ? create() : current(ids.getFirst());
    }
    public History history(String cursor, int limit) {
        var owner = requesters.current(); settings.requireEnabled();
        if (limit < 1 || limit > 50 || cursor != null && cursor.length() > 128) throw SupportPolicy.bad("SUPPORT_INVALID_PAGE");
        var args = new java.util.ArrayList<Object>(List.of(settings.deskBindingId(), owner.customerId(), owner.platformUserId()));
        args.add(owner.issuer()); args.add(owner.subject());
        String boundary = "";
        if (cursor != null) {
            var previous = current(cursor);
            if (linked(previous)) throw SupportPolicy.missing();
            boundary = " and (s.created_at < (select created_at from platform_support_conversation_scope where support_space_id=? and conversation_id=?)"
                + " or (s.created_at = (select created_at from platform_support_conversation_scope where support_space_id=? and conversation_id=?) and s.conversation_id < ?))";
            args.addAll(List.of(settings.deskBindingId(), cursor, settings.deskBindingId(), cursor, cursor));
        }
        args.add(limit + 1);
        var rows = jdbc.query("select s.conversation_id,s.created_at from platform_support_conversation_scope s where " + ownerWhere()
            + unlinked() + boundary + " order by s.created_at desc,s.conversation_id desc limit ?",
            (row,n) -> new Summary(row.getString(1), row.getTimestamp(2).toInstant()), args.toArray());
        return new History(List.copyOf(rows.subList(0, Math.min(limit, rows.size()))), rows.size() > limit ? rows.get(limit-1).conversationId() : null);
    }
    @Transactional
    public ContextView context(String id) { return context(current(id), false); }
    public ContextView context(Scope scope, boolean lock) {
        if (lock) lock(scope.requester());
        return jdbc.query("select * from platform_support_conversation_scope where support_space_id=? and conversation_id=?" + (lock ? " for update" : ""),
            (row,n) -> new ContextView(scope.conversationId(), decode(row.getString("resources_json")),
                new Consent(row.getBoolean("consent_granted"), row.getLong("consent_version"), row.getTimestamp("consent_changed_at").toInstant(),
                    row.getTimestamp("consent_revoked_at") == null ? null : row.getTimestamp("consent_revoked_at").toInstant()), row.getLong("revision")),
            settings.deskBindingId(), scope.conversationId()).stream().findFirst().orElseThrow(SupportPolicy::missing);
    }
    @Transactional
    public ContextView update(String id, SupportTicketService.ContextInput input) {
        var scope = current(id);
        var c = context(scope, true);
        if (linked(scope)) throw SupportPolicy.conflict("SUPPORT_USE_REQUEST_CONVERSATION");
        if (input.expectedRevision() != c.revision()) throw SupportPolicy.conflict("SUPPORT_CONTEXT_REVISION_CONFLICT");
        if (input.resources().size() > 8) throw SupportPolicy.bad("SUPPORT_RESOURCE_LIMIT");
        var refs = input.resources().stream().distinct().sorted(Comparator.comparing((ResourceRef r) -> r.type().name()).thenComparing(ResourceRef::id)).toList();
        resources.requireOwned(scope.requester(), refs);
        if (!refs.equals(c.resources())) jdbc.update("""
            update platform_support_conversation_scope set resources_json=?,consent_granted=false,
            consent_version=consent_version+1,consent_changed_at=?,consent_revoked_at=?,revision=revision+1
            where support_space_id=? and conversation_id=?
            """, encode(refs), ts(Instant.now()), ts(Instant.now()), settings.deskBindingId(), id);
        return context(scope, false);
    }
    @Transactional
    public ContextView consent(String id, ConsentChange input) {
        var scope = current(id);
        var c = context(scope, true);
        if (linked(scope)) throw SupportPolicy.conflict("SUPPORT_USE_REQUEST_CONVERSATION");
        if (input.expectedVersion() != c.consent().version()) throw SupportPolicy.conflict("SUPPORT_CONSENT_VERSION_CONFLICT");
        if (input.granted()) resources.requireOwned(scope.requester(), c.resources());
        if (input.granted() != c.consent().granted()) jdbc.update("""
            update platform_support_conversation_scope set consent_granted=?,consent_version=consent_version+1,
            consent_changed_at=?,consent_revoked_at=?,revision=revision+1 where support_space_id=? and conversation_id=?
            """, input.granted(), ts(Instant.now()), input.granted() ? null : ts(Instant.now()), settings.deskBindingId(), id);
        return context(scope, false);
    }
    private boolean linked(Scope scope) {
        return jdbc.queryForObject("select count(*) from platform_support_integration_context where support_space_id=? and runtime_principal=?",
            Integer.class, settings.deskBindingId(), scope.runtimePrincipal()) > 0;
    }
    private static String ownerWhere() {
        return "s.support_space_id=? and s.customer_id=? and s.requester_user_id=? and coalesce(s.requester_issuer,'')=coalesce(?,'') and coalesce(s.requester_subject,'')=coalesce(?,'')";
    }
    private static String unlinked() {
        return " and not exists (select 1 from platform_support_integration_context c where c.support_space_id=s.support_space_id and c.runtime_principal=s.runtime_principal)";
    }
    private Object[] ownerArgs(Requester owner) { return new Object[]{settings.deskBindingId(), owner.customerId(), owner.platformUserId(), owner.issuer(), owner.subject()}; }
    private void lock(Requester owner) {
        if (jdbc.queryForList("select id from platform_users where id=? for update", String.class, owner.platformUserId()).size() != 1) throw SupportPolicy.denied();
    }
    private static java.sql.Timestamp ts(Instant instant) { return instant == null ? null : java.sql.Timestamp.from(instant); }
    private String encode(List<ResourceRef> refs) {
        try { return mapper.writeValueAsString(refs); } catch (Exception e) { throw new IllegalStateException("Invalid support resources", e); }
    }
    private List<ResourceRef> decode(String json) {
        try { return List.copyOf(mapper.readValue(json, new TypeReference<List<ResourceRef>>() { })); } catch (Exception e) { throw new IllegalStateException("Invalid support resources", e); }
    }
    public Scope current(String conversationId) {
        var scope = find("conversation_id", conversationId);
        requesters.requireCurrent(scope.requester()); return scope;
    }
    public Scope verifiedPrincipal(String principal) {
        return findPrincipal(principal).orElseThrow(SupportPolicy::missing);
    }
    public Optional<Scope> findPrincipal(String principal) {
        var scope = lookup("runtime_principal", principal);
        scope.ifPresent(value -> requesters.requireActive(value.requester()));
        return scope;
    }
    private Scope find(String column, String value) {
        return lookup(column, value).orElseThrow(SupportPolicy::missing);
    }
    private Optional<Scope> lookup(String column, String value) {
        settings.requireEnabled();
        return jdbc.query("select * from platform_support_conversation_scope where support_space_id=? and " + column + "=?",
            (row,n) -> new Scope(row.getString("conversation_id"), row.getString("runtime_principal"),
                new Requester(row.getString("customer_id"), row.getString("requester_user_id"), row.getString("requester_issuer"), row.getString("requester_subject"))),
            settings.deskBindingId(), value).stream().findFirst();
    }
}
