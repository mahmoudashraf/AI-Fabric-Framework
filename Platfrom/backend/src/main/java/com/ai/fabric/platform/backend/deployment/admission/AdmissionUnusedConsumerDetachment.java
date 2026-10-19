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
        return detach(new Scope(reservation.customerId(), reservation.tenantId(), reservation.deploymentId(),
            reservation.targetProfileId()), requests, Map.of("cancellation", cancellationFingerprint,
                "reservation", reservation.id(), "generation", reservation.generation()), false);
    }

    /** Only the unused vector release service supplies this scope after locking and proving no target effects. */
    public List<UnusedConsumerDetachmentReceipt> detachForUnusedVectorRelease(String customerId, String tenantId,
            String deploymentId, String targetProfileId, List<UnusedConsumerDetachment> requests,
            String releaseCommandFingerprint) {
        return detach(new Scope(customerId, tenantId, deploymentId, targetProfileId), requests,
            Map.of("releaseCommand", releaseCommandFingerprint), true);
    }

    private record Scope(String customerId, String tenantId, String deploymentId, String targetProfileId) { }

    private List<UnusedConsumerDetachmentReceipt> detach(Scope scope, List<UnusedConsumerDetachment> requests,
                                                        Map<String, String> commandIdentity, boolean vectorRelease) {
        if (requests == null || requests.size() > 32 || requests.stream().anyMatch(java.util.Objects::isNull)
            || requests.stream().map(UnusedConsumerDetachment::consumerEntityId).distinct().count() != requests.size())
            throw denied("UNUSED_CONSUMER_SET_CHANGED");
        var found = configurationOnly(jdbc, json, scope, requests, null, new ArrayList<>());
        if (!found.equals(requests)) throw denied("UNUSED_CONSUMER_SET_CHANGED");
        var receipts = new ArrayList<UnusedConsumerDetachmentReceipt>();
        for (var request : found) {
            var fingerprint = new java.util.TreeMap<String, String>(commandIdentity);
            fingerprint.put("consumer", request.consumerEntityId());
            String key = (vectorRelease ? "unused-vector-release:" : "unused-cancel:") + json.fingerprint(fingerprint);
            var drafts = request.draftWorkspaces().stream().collect(Collectors.toMap(DraftWorkspaceExpectation::installationEntityId,
                DraftWorkspaceExpectation::expectedRowVersion));
            var result = vectorRelease
                ? consumers.detachNeverActivatedForUnusedVectorRelease(scope.customerId(), request.consumerEntityId(),
                    scope.deploymentId(), scope.targetProfileId(), request.expectedBindingRevision(), key, drafts)
                : consumers.detachNeverActivatedForUnusedCancellation(scope.customerId(), request.consumerEntityId(),
                    scope.deploymentId(), scope.targetProfileId(), request.expectedBindingRevision(), key, drafts);
            if (!request.consumerEntityId().equals(result.consumerEntityId()) || result.fromBindingRevision() != request.expectedBindingRevision()
                || result.toBindingRevision() != request.expectedBindingRevision() + 1 || !key.equals(result.operationKey())
                || result.historyId() == null || result.historyId().isBlank()) throw denied("UNUSED_CONSUMER_DETACHMENT_UNPROVEN");
            receipts.add(new UnusedConsumerDetachmentReceipt(result.consumerEntityId(), result.fromBindingRevision(),
                result.toBindingRevision(), result.historyId(), key, request.draftWorkspaces()));
        }
        return List.copyOf(receipts);
    }

    record ConfigurationOnlyConsumers(List<UnusedConsumerDetachment> assignments, String fingerprint) {
        ConfigurationOnlyConsumers { assignments = List.copyOf(assignments); }
        List<String> consumerIds() { return assignments.stream().map(UnusedConsumerDetachment::consumerEntityId).toList(); }
    }

    static ConfigurationOnlyConsumers readForInitialEvidenceRenewal(JdbcTemplate jdbc, AdmissionJson json,
        Reservation reservation, com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context) {
        var fingerprints = new ArrayList<String>();
        var assignments = configurationOnly(jdbc, json, new Scope(reservation.customerId(), reservation.tenantId(),
            reservation.deploymentId(), reservation.targetProfileId()), null, context.offerId(), fingerprints);
        return new ConfigurationOnlyConsumers(assignments, json.fingerprint(fingerprints));
    }

    private static List<UnusedConsumerDetachment> configurationOnly(JdbcTemplate jdbc, AdmissionJson json, Scope scope,
        List<UnusedConsumerDetachment> requests, String offerId, List<String> fingerprints) {
        Map<String, UnusedConsumerDetachment> expected = requests == null ? null : requests.stream()
            .collect(Collectors.toMap(UnusedConsumerDetachment::consumerEntityId, Function.identity()));
        return jdbc.query("""
            SELECT *
              FROM platform_consumers WHERE bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL)
             ORDER BY id FOR UPDATE NOWAIT
            """, (rs, row) -> {
            var request = expected == null ? null : expected.get(rs.getString("id"));
            fingerprints.add(rowFingerprint(rs, json));
            if ((expected != null && request == null) || !scope.customerId().equals(rs.getString("customer_id"))
                || !"ACTIVE".equals(rs.getString("status")) || rs.getString("bound_release_id") != null
                || !scope.targetProfileId().equals(rs.getString("bound_target_profile_id"))
                || rs.getLong("binding_revision") != 1
                || (request != null && request.expectedBindingRevision() != 1))
                throw denied("UNUSED_CONSUMER_ASSIGNMENT_CHANGED");
            var initial = jdbc.query("""
                SELECT *
                  FROM platform_consumer_binding_history WHERE consumer_entity_id=? ORDER BY id FOR UPDATE NOWAIT
                """, (history, index) -> {
                fingerprints.add(rowFingerprint(history, json));
                if (!scope.customerId().equals(history.getString("customer_id"))
                    || history.getString("from_deployment_id") != null || history.getString("from_release_id") != null
                    || history.getString("to_release_id") != null || history.getString("from_target_profile_id") != null
                    || !scope.deploymentId().equals(history.getString("to_deployment_id"))
                    || !scope.targetProfileId().equals(history.getString("to_target_profile_id"))
                    || !Long.valueOf(0).equals(history.getObject("from_binding_revision", Long.class))
                    || !Long.valueOf(1).equals(history.getObject("to_binding_revision", Long.class)))
                    throw denied("UNUSED_CONSUMER_HISTORY_UNPROVEN");
                return history.getString("operation_key");
            }, rs.getString("id"));
            if (initial.size() != 1 || initial.getFirst() == null) throw denied("UNUSED_CONSUMER_HISTORY_UNPROVEN");
            String consumerId = rs.getString("consumer_id");
            var creation = jdbc.query("""
                SELECT c.*,e.offer_id AS evidence_offer_id FROM platform_customer_entitlement_commands c
                 JOIN platform_customer_offer_entitlements e ON e.id=c.entitlement_id
                 JOIN platform_customer_offer_deployments d ON d.offer_id=e.offer_id AND d.customer_id=e.customer_id
                 WHERE d.deployment_id=? AND d.tenant_id=? AND e.customer_id=? AND c.operation_key=?
                   AND c.command_type='CREATE_STAGING_CONSUMER'
                """, (command, index) -> {
                fingerprints.add(rowFingerprint(command, json));
                JsonNode result = json.read(command.getString("result_json"), JsonNode.class);
                return (offerId == null || offerId.equals(command.getString("evidence_offer_id")))
                    && consumerId.equals(result.path("consumerId").textValue())
                    && scope.customerId().equals(result.path("customerId").textValue())
                    && scope.deploymentId().equals(result.path("boundDeploymentId").textValue())
                    && scope.targetProfileId().equals(result.path("boundTargetProfileId").textValue())
                    && result.path("boundReleaseId").isNull() && result.path("bindingRevision").isIntegralNumber()
                    && result.path("bindingRevision").longValue() == 1;
            }, scope.deploymentId(), scope.tenantId(), scope.customerId(), initial.getFirst());
            if (creation.size() != 1 || !creation.getFirst()) throw denied("UNUSED_CONSUMER_CREATION_UNPROVEN");
            var apps = jdbc.query("""
                SELECT * FROM ai_workspace_installations
                 WHERE consumer_entity_id=? ORDER BY id FOR UPDATE NOWAIT
                """, (app, index) -> {
                fingerprints.add(rowFingerprint(app, json));
                if (!scope.customerId().equals(app.getString("customer_id")) || !"DRAFT".equals(app.getString("status"))
                    || app.getTimestamp("activated_at") != null || app.getTimestamp("disabled_at") != null)
                    throw denied("UNUSED_CONSUMER_WORKSPACE_CHANGED");
                return new DraftWorkspaceExpectation(app.getString("id"), app.getLong("row_version"));
            }, rs.getString("id"));
            if (request != null && !apps.equals(request.draftWorkspaces())) throw denied("UNUSED_CONSUMER_WORKSPACE_CHANGED");
            return request == null ? new UnusedConsumerDetachment(rs.getString("id"), 1, apps) : request;
        }, scope.deploymentId(), scope.targetProfileId());
    }

    private static String rowFingerprint(java.sql.ResultSet row, AdmissionJson json) throws java.sql.SQLException {
        var fields = new java.util.TreeMap<String, String>();
        var columns = row.getMetaData();
        for (int i = 1; i <= columns.getColumnCount(); i++) fields.put(columns.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), row.getString(i));
        return json.fingerprint(fields);
    }

    private static AdmissionFailure denied(String code) { return new AdmissionFailure(CONFLICT, code); }
}
