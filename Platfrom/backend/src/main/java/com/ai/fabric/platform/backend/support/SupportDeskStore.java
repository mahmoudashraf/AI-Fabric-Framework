package com.ai.fabric.platform.backend.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;

@Repository
public class SupportDeskStore {
    public record Receipt(String caseId, String requestHash, List<ResourceRef> resources, Response response) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public SupportDeskStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    Optional<Receipt> receipt(String key) {
        return jdbc.query("select case_id,request_hash,resource_refs_json,response_json from platform_support_desk_operations where receipt_key=?",
            (rs, n) -> new Receipt(rs.getString(1), rs.getString(2), resources(rs.getString(3)), response(rs.getString(4))), key).stream().findFirst();
    }

    void record(CaseRecord c, Request input, VerifiedAgent actor, String receiptKey, String requestHash, Response response) {
        var s = input.scope();
        jdbc.update("""
            insert into platform_support_desk_operations
            (id,case_id,binding_id,conversation_id,handover_id,external_conversation_ref,
             actor_issuer,actor_subject,actor_agent_id,operation,receipt_key,request_hash,resource_refs_json,response_json,created_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, "sdo-" + UUID.randomUUID(), c.id(), s.bindingId(), s.conversationId(), s.handoverId(), s.externalConversationRef(),
            actor.issuer(), actor.subject(), actor.agentId(), input.operation().name(), receiptKey, requestHash,
            json(c.resources()), response == null ? null : json(response), Timestamp.from(Instant.now()));
    }
    String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Cannot serialize support Desk operation", failure); }
    }
    private Response response(String value) {
        try { return mapper.readValue(value, Response.class); }
        catch (Exception failure) { throw new IllegalStateException("Invalid support Desk receipt", failure); }
    }
    private List<ResourceRef> resources(String value) {
        try { return mapper.readValue(value, new TypeReference<List<ResourceRef>>() { }); }
        catch (Exception failure) { throw new IllegalStateException("Invalid support Desk receipt scope", failure); }
    }
}
