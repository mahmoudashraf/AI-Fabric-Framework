package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Exact, caller-authorized metadata detachments; the owning service performs every binding/history write. */
@Component
public class AdmissionUnusedConsumerDetachment {
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final PlatformCustomerConsumerService consumers;

    public AdmissionUnusedConsumerDetachment(JdbcTemplate jdbc, AdmissionJson json, PlatformCustomerConsumerService consumers) {
        this.jdbc = jdbc; this.json = json; this.consumers = consumers;
    }

    List<UnusedConsumerDetachmentReceipt> detach(Reservation reservation, List<UnusedConsumerDetachment> requests,
                                                String cancellationFingerprint) {
        Map<String, UnusedConsumerDetachment> expected = requests.stream()
            .collect(Collectors.toMap(UnusedConsumerDetachment::consumerEntityId, Function.identity()));
        var found = jdbc.query("""
            SELECT id,consumer_id,customer_id,status,bound_release_id,bound_target_profile_id,binding_revision
              FROM platform_consumers WHERE bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL)
             ORDER BY id FOR UPDATE NOWAIT
            """, (rs, row) -> {
            var request = expected.get(rs.getString("id"));
            if (request == null || !reservation.customerId().equals(rs.getString("customer_id"))
                || !"ACTIVE".equals(rs.getString("status")) || rs.getString("bound_release_id") != null
                || !reservation.targetProfileId().equals(rs.getString("bound_target_profile_id"))
                || rs.getLong("binding_revision") != request.expectedBindingRevision() || request.expectedBindingRevision() != 1)
                throw denied("UNUSED_CONSUMER_ASSIGNMENT_CHANGED");
            var initial = jdbc.query("""
                SELECT id,customer_id,from_deployment_id,to_deployment_id,from_release_id,to_release_id,
                       from_target_profile_id,to_target_profile_id,from_binding_revision,to_binding_revision,operation_key
                  FROM platform_consumer_binding_history WHERE consumer_entity_id=? ORDER BY id FOR UPDATE NOWAIT
                """, (history, index) -> {
                if (!reservation.customerId().equals(history.getString("customer_id"))
                    || history.getString("from_deployment_id") != null || history.getString("from_release_id") != null
                    || history.getString("to_release_id") != null || history.getString("from_target_profile_id") != null
                    || !reservation.deploymentId().equals(history.getString("to_deployment_id"))
                    || !reservation.targetProfileId().equals(history.getString("to_target_profile_id"))
                    || !Long.valueOf(0).equals(history.getObject("from_binding_revision", Long.class))
                    || !Long.valueOf(1).equals(history.getObject("to_binding_revision", Long.class)))
                    throw denied("UNUSED_CONSUMER_HISTORY_UNPROVEN");
                return history.getString("operation_key");
            }, request.consumerEntityId());
            if (initial.size() != 1 || initial.getFirst() == null) throw denied("UNUSED_CONSUMER_HISTORY_UNPROVEN");
            String consumerId = rs.getString("consumer_id");
            var creation = jdbc.query("""
                SELECT c.result_json FROM platform_customer_entitlement_commands c
                 JOIN platform_customer_offer_entitlements e ON e.id=c.entitlement_id
                 JOIN platform_customer_offer_deployments d ON d.offer_id=e.offer_id AND d.customer_id=e.customer_id
                 WHERE d.deployment_id=? AND d.tenant_id=? AND e.customer_id=? AND c.operation_key=?
                   AND c.command_type='CREATE_STAGING_CONSUMER'
                """, (command, index) -> {
                JsonNode result = json.read(command.getString("result_json"), JsonNode.class);
                return consumerId.equals(result.path("consumerId").textValue())
                    && reservation.customerId().equals(result.path("customerId").textValue())
                    && reservation.deploymentId().equals(result.path("boundDeploymentId").textValue())
                    && reservation.targetProfileId().equals(result.path("boundTargetProfileId").textValue())
                    && result.path("boundReleaseId").isNull() && result.path("bindingRevision").isIntegralNumber()
                    && result.path("bindingRevision").longValue() == 1;
            }, reservation.deploymentId(), reservation.tenantId(), reservation.customerId(), initial.getFirst());
            if (creation.size() != 1 || !creation.getFirst()) throw denied("UNUSED_CONSUMER_CREATION_UNPROVEN");
            var apps = jdbc.query("""
                SELECT id,customer_id,status,activated_at,disabled_at,row_version FROM ai_workspace_installations
                 WHERE consumer_entity_id=? ORDER BY id FOR UPDATE NOWAIT
                """, (app, index) -> {
                if (!reservation.customerId().equals(app.getString("customer_id")) || !"DRAFT".equals(app.getString("status"))
                    || app.getTimestamp("activated_at") != null || app.getTimestamp("disabled_at") != null)
                    throw denied("UNUSED_CONSUMER_WORKSPACE_CHANGED");
                return new DraftWorkspaceExpectation(app.getString("id"), app.getLong("row_version"));
            }, request.consumerEntityId());
            if (!apps.equals(request.draftWorkspaces())) throw denied("UNUSED_CONSUMER_WORKSPACE_CHANGED");
            return request;
        }, reservation.deploymentId(), reservation.targetProfileId());
        if (!found.equals(requests)) throw denied("UNUSED_CONSUMER_SET_CHANGED");
        var receipts = new ArrayList<UnusedConsumerDetachmentReceipt>();
        for (var request : found) {
            String key = "unused-cancel:" + json.fingerprint(Map.of("cancellation", cancellationFingerprint,
                "reservation", reservation.id(), "generation", reservation.generation(), "consumer", request.consumerEntityId()));
            var result = consumers.detachNeverActivatedForUnusedCancellation(reservation.customerId(), request.consumerEntityId(),
                reservation.deploymentId(), reservation.targetProfileId(), request.expectedBindingRevision(), key,
                request.draftWorkspaces().stream().collect(Collectors.toMap(DraftWorkspaceExpectation::installationEntityId,
                    DraftWorkspaceExpectation::expectedRowVersion)));
            if (!request.consumerEntityId().equals(result.consumerEntityId()) || result.fromBindingRevision() != request.expectedBindingRevision()
                || result.toBindingRevision() != request.expectedBindingRevision() + 1 || !key.equals(result.operationKey())
                || result.historyId() == null || result.historyId().isBlank()) throw denied("UNUSED_CONSUMER_DETACHMENT_UNPROVEN");
            receipts.add(new UnusedConsumerDetachmentReceipt(result.consumerEntityId(), result.fromBindingRevision(),
                result.toBindingRevision(), result.historyId(), key, request.draftWorkspaces()));
        }
        return List.copyOf(receipts);
    }

    private AdmissionFailure denied(String code) { return new AdmissionFailure(CONFLICT, code); }
}
