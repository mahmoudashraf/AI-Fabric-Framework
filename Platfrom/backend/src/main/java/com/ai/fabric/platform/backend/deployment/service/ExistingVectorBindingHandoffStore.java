package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts.Receipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Append-only receipt in the same transaction as the new binding and its credential references. */
@Component
public class ExistingVectorBindingHandoffStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ExistingVectorBindingHandoffStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public Optional<Receipt> find(String deploymentId, String targetId, String operationKey) {
        var rows = jdbc.query("""
            SELECT * FROM deployment_unused_vector_handoffs WHERE deployment_id=? AND target_profile_id=? AND operation_key=?
            """, (rs, row) -> {
            Receipt receipt;
            try { receipt = mapper.readValue(rs.getString("payload_json"), Receipt.class); }
            catch (Exception error) { throw changed(); }
            if (!rs.getString("id").equals(receipt.id()) || !deploymentId.equals(receipt.deploymentId())
                || !targetId.equals(receipt.targetProfileId()) || !operationKey.equals(receipt.operationKey())
                || !rs.getString("command_fingerprint").equals(receipt.commandFingerprint())
                || !rs.getString("binding_id").equals(receipt.binding().bindingId())
                || !rs.getString("actor_id").equals(receipt.actorId())
                || !rs.getString("approval_reference").equals(receipt.approvalReference())
                || !rs.getTimestamp("handed_off_at").toInstant().equals(receipt.handedOffAt())
                || !rs.getString("fingerprint").equals(receipt.fingerprint())
                || !fingerprint(receipt).equals(receipt.fingerprint())) throw changed();
            return receipt;
        }, deploymentId, targetId, operationKey);
        return rows.stream().findFirst();
    }

    public String fingerprint(Receipt receipt) {
        ObjectNode value = mapper.valueToTree(receipt);
        value.remove("fingerprint");
        return DeploymentEvidenceFingerprint.hash(mapper, value);
    }

    public void append(Receipt receipt) {
        if (!fingerprint(receipt).equals(receipt.fingerprint())) throw changed();
        try {
            jdbc.update("""
                INSERT INTO deployment_unused_vector_handoffs
                 (id,deployment_id,target_profile_id,operation_key,command_fingerprint,binding_id,actor_id,
                  approval_reference,handed_off_at,fingerprint,payload_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, receipt.id(), receipt.deploymentId(), receipt.targetProfileId(), receipt.operationKey(),
                receipt.commandFingerprint(), receipt.binding().bindingId(), receipt.actorId(), receipt.approvalReference(),
                Timestamp.from(receipt.handedOffAt()), receipt.fingerprint(), mapper.writeValueAsString(receipt));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw changed(); }
    }

    private ResponseStatusException changed() { return new ResponseStatusException(CONFLICT, "UNUSED_VECTOR_HANDOFF_RECEIPT_CHANGED"); }
}
