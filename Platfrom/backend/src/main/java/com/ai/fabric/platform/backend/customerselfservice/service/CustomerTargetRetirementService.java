package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Command;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.PlanRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

/** Customer-scoped read-only archive. No planner, drain worker or provider mutation is registered here. */
@Service
@Transactional
public class CustomerTargetRetirementService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final JdbcTemplate jdbc;
    private final CustomerOfferCodec codec;

    public CustomerTargetRetirementService(CustomerOfferEntitlementService entitlements,
            CustomerSelfServiceAuthorizationService authorization, CustomerOfferDeploymentRepository associations,
            DeploymentRepository deployments, JdbcTemplate jdbc, CustomerOfferCodec codec) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.associations = associations;
        this.deployments = deployments;
        this.jdbc = jdbc;
        this.codec = codec;
    }

    public Summary plan(String offerId, String deploymentId, PlanRequest request) {
        if (request == null || blank(request.targetProfileId(), 64)) throw invalid();
        var deployment = ownedDeployment(offerId, deploymentId);
        authorizeTarget(offerId, deployment, request.targetProfileId());
        throw retired();
    }

    public List<Summary> list(String offerId, String deploymentId, String targetProfileId) {
        var deployment = ownedDeployment(offerId, deploymentId);
        authorizeTarget(offerId, deployment, targetProfileId);
        return jdbc.query("""
            SELECT * FROM platform_target_retirement_operations
            WHERE customer_id=? AND deployment_id=? AND target_profile_id=? ORDER BY created_at DESC LIMIT 200
            """, this::history, deployment.getCustomerId(), deploymentId, targetProfileId).stream()
            .map(row -> authorizeHistory(offerId, deployment, row)).toList();
    }

    public Summary status(String offerId, String deploymentId, String operationId) {
        if (blank(operationId, 64)) throw invalid();
        var deployment = ownedDeployment(offerId, deploymentId);
        var rows = jdbc.query("""
            SELECT * FROM platform_target_retirement_operations WHERE id=? AND customer_id=? AND deployment_id=?
            """, this::history, operationId, deployment.getCustomerId(), deploymentId);
        if (rows.size() != 1) throw deny(RESOURCE_SCOPE_MISMATCH);
        return authorizeHistory(offerId, deployment, rows.getFirst());
    }

    public Summary execute(String offerId, String deploymentId, String operationId, Command command) {
        status(offerId, deploymentId, operationId);
        throw retired();
    }

    public Summary retry(String offerId, String deploymentId, String operationId, Command command) {
        status(offerId, deploymentId, operationId);
        throw retired();
    }

    public Summary cancel(String offerId, String deploymentId, String operationId, Command command) {
        status(offerId, deploymentId, operationId);
        throw retired();
    }

    private DeploymentEntity ownedDeployment(String offerId, String deploymentId) {
        if (blank(offerId, 64) || blank(deploymentId, 64)) throw invalid();
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findById(deploymentId)
            .filter(value -> actor.customerId().equals(value.getCustomerId()) && value.getArchivedAt() == null
                && !"ARCHIVED".equals(value.getStatus()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var association = associations.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!offerId.equals(association.getOfferId()) || !actor.customerId().equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        return deployment;
    }

    private void authorizeTarget(String offerId, DeploymentEntity deployment, String targetProfileId) {
        if (blank(targetProfileId, 64)) throw invalid();
        authorization.authorizeOwnedTargetRead(deployment.getCustomerId(), readRequest(offerId, deployment, targetProfileId, null));
    }

    private Summary authorizeHistory(String offerId, DeploymentEntity deployment, History row) {
        var summary = row.summary();
        if (!deployment.getCustomerId().equals(row.customerId()) || !deployment.getId().equals(summary.deploymentId())
            || blank(summary.requestFingerprint(), 64) || blank(row.operationKey(), 128)
            || !row.operationKey().equals(row.request().path("operationKey").asText())) throw deny(RESOURCE_SCOPE_MISMATCH);
        authorizeTarget(offerId, deployment, summary.targetProfileId());
        JsonNode releaseIds = row.request().path("releaseIds");
        if (!releaseIds.isArray() || releaseIds.isEmpty() || releaseIds.size() > 128) throw deny(RESOURCE_SCOPE_MISMATCH);
        for (JsonNode releaseId : releaseIds) {
            if (!releaseId.isTextual() || blank(releaseId.asText(), 64)) throw deny(RESOURCE_SCOPE_MISMATCH);
            authorization.authorizeOperationalRead(deployment.getCustomerId(),
                readRequest(offerId, deployment, summary.targetProfileId(), releaseId.asText()));
        }
        return summary;
    }

    private AuthorizationRequest readRequest(String offerId, DeploymentEntity deployment, String targetProfileId, String releaseId) {
        return new AuthorizationRequest(offerId, targetProfileId, deployment.getId(), deployment.getTenantId(),
            releaseId, Operation.READ, null, Set.of(), null, null, null);
    }

    private History history(ResultSet rs, int index) throws SQLException {
        JsonNode request = codec.tree(rs.getString("request_json"));
        JsonNode result = codec.tree(rs.getString("result_json"));
        List<String> blockers = new ArrayList<>();
        for (JsonNode blocker : result.path("blockers")) {
            if (!blocker.isTextual()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy retirement history is unreadable.");
            blockers.add(blocker.asText());
        }
        var summary = new Summary(rs.getString("id"), rs.getString("deployment_id"), rs.getString("target_profile_id"),
            rs.getString("request_fingerprint"), rs.getString("state"), rs.getString("step"), instant(rs, "drain_deadline"),
            request.path("retention"), rs.getInt("attempt"), instant(rs, "next_retry_at"), rs.getString("error_code"),
            List.copyOf(blockers), !Set.of("COMPLETE", "RETIRED_WITH_RETAINED_DATA").contains(rs.getString("state")), false,
            instant(rs, "created_at"), instant(rs, "started_at"), instant(rs, "completed_at"), instant(rs, "updated_at"));
        return new History(rs.getString("customer_id"), rs.getString("operation_key"), request, summary);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private ResponseStatusException retired() {
        return new ResponseStatusException(HttpStatus.GONE,
            "LEGACY_TARGET_RETIREMENT_RETIRED: retirement history is read-only; no target was changed.");
    }

    private boolean blank(String value, int limit) {
        return value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim());
    }

    private record History(String customerId, String operationKey, JsonNode request, Summary summary) { }
}
