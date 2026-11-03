package com.ai.fabric.platform.backend.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Console resource authority only; no ticket text, business status or transcript is stored here. */
@Repository
public class SupportIntegrationContextStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SupportIntegrationContextStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Context(String spaceId, String ticketId, Requester requester, String runtimePrincipal,
                          List<ResourceRef> resources, Consent consent, long revision,
                          Instant createdAt, Instant updatedAt) { }

    public Optional<Context> find(String spaceId, String ticketId, boolean lock) {
        return one(jdbc.query("select * from platform_support_integration_context where support_space_id=? and ticket_id=?"
            + (lock ? " for update" : ""), this::read, spaceId, ticketId));
    }

    public Optional<Context> byPrincipal(String spaceId, String principal, boolean lock) {
        return one(jdbc.query("select * from platform_support_integration_context where support_space_id=? and runtime_principal=?"
            + (lock ? " for update" : ""), this::read, spaceId, principal));
    }

    public void lockRequester(String userId) {
        if (jdbc.queryForList("select id from platform_users where id=? for update", String.class, userId).size() != 1)
            throw SupportPolicy.denied();
    }

    public void insert(String spaceId, String ticketId, Requester owner, String principal, Instant now) {
        jdbc.update("""
            insert into platform_support_integration_context
            (support_space_id,ticket_id,customer_id,requester_user_id,requester_issuer,requester_subject,
             runtime_principal,resources_json,consent_granted,consent_version,consent_changed_at,
             revision,created_at,updated_at) values (?,?,?,?,?,?,?,?,false,0,?,0,?,?)
            """, spaceId, ticketId, owner.customerId(), owner.platformUserId(), owner.issuer(), owner.subject(),
            principal, json(List.of()), ts(now), ts(now), ts(now));
    }

    public void resources(Context current, List<ResourceRef> resources, Instant now) {
        int changed = jdbc.update("""
            update platform_support_integration_context set resources_json=?,consent_granted=false,
              consent_version=consent_version+1,consent_changed_at=?,consent_revoked_at=?,
              revision=revision+1,updated_at=? where support_space_id=? and ticket_id=? and revision=?
            """, json(resources), ts(now), ts(now), ts(now), current.spaceId(), current.ticketId(), current.revision());
        if (changed != 1) throw SupportPolicy.conflict("SUPPORT_CONTEXT_REVISION_CONFLICT");
    }

    public void consent(Context current, boolean granted, Instant now) {
        int changed = jdbc.update("""
            update platform_support_integration_context set consent_granted=?,consent_version=consent_version+1,
              consent_changed_at=?,consent_revoked_at=?,revision=revision+1,updated_at=?
             where support_space_id=? and ticket_id=? and revision=?
            """, granted, ts(now), granted ? null : ts(now), ts(now), current.spaceId(), current.ticketId(), current.revision());
        if (changed != 1) throw SupportPolicy.conflict("SUPPORT_CONTEXT_REVISION_CONFLICT");
    }

    private Context read(ResultSet row, int index) throws SQLException {
        return new Context(row.getString("support_space_id"), row.getString("ticket_id"),
            new Requester(row.getString("customer_id"), row.getString("requester_user_id"),
                row.getString("requester_issuer"), row.getString("requester_subject")),
            row.getString("runtime_principal"), resources(row.getString("resources_json")),
            new Consent(row.getBoolean("consent_granted"), row.getLong("consent_version"),
                instant(row, "consent_changed_at"), instant(row, "consent_revoked_at")),
            row.getLong("revision"), instant(row, "created_at"), instant(row, "updated_at"));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Cannot encode support integration context", error); }
    }
    private List<ResourceRef> resources(String value) {
        try { return List.copyOf(mapper.readValue(value, new TypeReference<List<ResourceRef>>() { })); }
        catch (Exception error) { throw new IllegalStateException("Invalid stored support integration context", error); }
    }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static <T> Optional<T> one(List<T> values) {
        if (values.size() > 1) throw new IllegalStateException("Ambiguous support integration context");
        return values.stream().findFirst();
    }
}
