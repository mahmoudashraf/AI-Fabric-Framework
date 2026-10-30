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
import java.util.UUID;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@Repository
public class SupportCaseStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public SupportCaseStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    void lockRequester(String userId) {
        if (jdbc.queryForList("select id from platform_users where id=? for update", String.class, userId).size() != 1) {
            throw SupportPolicy.denied();
        }
    }
    Optional<CaseRecord> replay(Requester owner, String key) {
        return one(jdbc.query("select * from platform_support_cases where customer_id=? and requester_user_id=? and submission_key=?",
            this::readCase, owner.customerId(), owner.platformUserId(), key));
    }
    Optional<CaseRecord> find(String id, boolean lock) {
        return one(jdbc.query("select * from platform_support_cases where id=?" + (lock ? " for update" : ""), this::readCase, id));
    }
    Optional<CaseRecord> byPrincipal(String principal) {
        return one(jdbc.query("select * from platform_support_cases where runtime_principal=? for update", this::readCase, principal));
    }
    List<CaseRecord> list(Requester owner, int limit, int offset) {
        return jdbc.query("select * from platform_support_cases where customer_id=? and requester_user_id=? order by created_at desc,id desc limit ? offset ?",
            this::readCase, owner.customerId(), owner.platformUserId(), limit, offset);
    }
    List<CaseRecord> staffIntake(RuntimeBinding binding, int limit, int offset) {
        return jdbc.query("""
            select c.* from platform_support_cases c
              join platform_support_case_episodes e on e.case_id=c.id
             where c.status='OPEN' and c.delivery_status<>'COMPLETED'
               and e.episode_number=(select max(latest.episode_number) from platform_support_case_episodes latest where latest.case_id=c.id)
               and e.deployment_id=? and e.target_profile_id=?
             order by c.created_at,c.id limit ? offset ?
            """, this::readCase, binding.deploymentId(), binding.targetProfileId(), limit, offset);
    }
    void insert(CaseRecord c) {
        jdbc.update("""
            insert into platform_support_cases
            (id,reference,customer_id,requester_user_id,requester_issuer,requester_subject,runtime_principal,
             submission_key,submission_hash,summary,details,topic,resources_json,initial_message,initial_message_key,
             delivery_status,status,consent_granted,consent_version,consent_changed_at,revision,created_at,updated_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, c.id(), c.reference(), c.requester().customerId(), c.requester().platformUserId(), c.requester().issuer(),
            c.requester().subject(), c.runtimePrincipal(), c.submissionKey(), c.submissionHash(), c.summary(), c.details(), c.topic(),
            write(c.resources()), c.initialMessage(), c.initialMessageKey(), c.deliveryStatus().name(), c.status().name(),
            c.consent().granted(), c.consent().version(), ts(c.consent().changedAt()), c.revision(), ts(c.createdAt()), ts(c.updatedAt()));
    }
    void context(String id, String topic, List<ResourceRef> resources, Instant now) {
        jdbc.update("update platform_support_cases set topic=?,resources_json=?,revision=revision+1,updated_at=? where id=?",
            topic, write(resources), ts(now), id);
    }
    void consent(String id, boolean granted, Instant now) {
        jdbc.update("""
            update platform_support_cases set consent_granted=?,consent_version=consent_version+1,
              consent_changed_at=?,consent_revoked_at=?,revision=revision+1,updated_at=? where id=?
            """, granted, ts(now), granted ? null : ts(now), ts(now), id);
    }
    void status(String id, CaseStatus status, Instant now) {
        jdbc.update("update platform_support_cases set status=?,closed_at=?,revision=revision+1,updated_at=? where id=?",
            status.name(), status == CaseStatus.OPEN ? null : ts(now), ts(now), id);
    }
    void delivery(String id, DeliveryStatus status, Instant now) {
        jdbc.update("update platform_support_cases set delivery_status=?,delivery_observed_at=?,updated_at=? where id=?",
            status.name(), ts(now), ts(now), id);
    }
    List<Episode> episodes(String caseId) {
        return jdbc.query("select * from platform_support_case_episodes where case_id=? order by episode_number", this::readEpisode, caseId);
    }
    Optional<Episode> episode(String caseId, String conversationId) {
        return one(jdbc.query("select * from platform_support_case_episodes where case_id=? and conversation_id=?",
            this::readEpisode, caseId, conversationId));
    }
    void insertEpisode(Episode e) {
        jdbc.update("""
            insert into platform_support_case_episodes
              (id,case_id,episode_number,binding_json,deployment_id,target_profile_id,conversation_id,terminal,created_at)
            values (?,?,?,?,?,?,?,?,?)
            """, e.id(), e.caseId(), e.number(), write(e.binding()), e.binding().deploymentId(), e.binding().targetProfileId(),
            e.conversationId(), e.terminal(), ts(e.createdAt()));
    }
    void terminal(String episodeId) {
        jdbc.update("update platform_support_case_episodes set terminal=true where id=?", episodeId);
    }
    void event(CaseRecord c, String type, String actor, Instant now) {
        jdbc.update("insert into platform_support_case_events(id,case_id,event_type,actor_user_id,consent_version,created_at) values (?,?,?,?,?,?)",
            "sce-" + UUID.randomUUID(), c.id(), type, actor, c.consent().version(), ts(now));
    }
    private CaseRecord readCase(ResultSet rs, int row) throws SQLException {
        return new CaseRecord(rs.getString("id"), rs.getString("reference"),
            new Requester(rs.getString("customer_id"), rs.getString("requester_user_id"), rs.getString("requester_issuer"), rs.getString("requester_subject")),
            rs.getString("runtime_principal"), rs.getString("submission_key"), rs.getString("submission_hash"),
            rs.getString("summary"), rs.getString("details"), rs.getString("topic"), resources(rs.getString("resources_json")),
            rs.getString("initial_message"), rs.getString("initial_message_key"), DeliveryStatus.valueOf(rs.getString("delivery_status")),
            instant(rs, "delivery_observed_at"), CaseStatus.valueOf(rs.getString("status")),
            new Consent(rs.getBoolean("consent_granted"), rs.getLong("consent_version"), instant(rs, "consent_changed_at"), instant(rs, "consent_revoked_at")),
            rs.getLong("revision"), instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "closed_at"));
    }
    private Episode readEpisode(ResultSet rs, int row) throws SQLException {
        return new Episode(rs.getString("id"), rs.getString("case_id"), rs.getInt("episode_number"),
            read(rs.getString("binding_json"), RuntimeBinding.class), rs.getString("conversation_id"), rs.getBoolean("terminal"), instant(rs, "created_at"));
    }
    String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize support registry data", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("Invalid support registry data", e); }
    }
    private List<ResourceRef> resources(String value) {
        try { return List.copyOf(json.readValue(value, new TypeReference<List<ResourceRef>>() { })); }
        catch (Exception e) { throw new IllegalStateException("Invalid support resource references", e); }
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) throw new IllegalStateException("Ambiguous support registry identity");
        return rows.stream().findFirst();
    }
}
