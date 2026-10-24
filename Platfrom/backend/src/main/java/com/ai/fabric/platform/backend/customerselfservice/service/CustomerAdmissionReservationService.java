package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.RefreshRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

/** Preserves customer-scoped history without loading or executing the retired capacity engine. */
@Service
@Transactional
public class CustomerAdmissionReservationService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final DeploymentRepository deployments;
    private final CustomerOfferDeploymentRepository associations;
    private final JdbcTemplate jdbc;
    private final CustomerOfferCodec codec;

    public CustomerAdmissionReservationService(CustomerOfferEntitlementService entitlements,
            CustomerSelfServiceAuthorizationService authorization, DeploymentRepository deployments,
            CustomerOfferDeploymentRepository associations, JdbcTemplate jdbc, CustomerOfferCodec codec) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.deployments = deployments;
        this.associations = associations;
        this.jdbc = jdbc;
        this.codec = codec;
    }

    public Summary reserve(String offerId, String deploymentId, Request request) {
        if (request == null || blank(request.targetProfileId(), 64)) throw invalid();
        var deployment = ownedDeployment(offerId, deploymentId);
        authorizeRead(offerId, deployment, request.targetProfileId());
        throw retired();
    }

    public Summary refreshEvidence(String offerId, String deploymentId, String reservationId, RefreshRequest request) {
        status(offerId, deploymentId, reservationId);
        throw retired();
    }

    public Summary status(String offerId, String deploymentId, String reservationId) {
        if (blank(reservationId, 64)) throw invalid();
        var deployment = ownedDeployment(offerId, deploymentId);
        var rows = jdbc.query("""
            SELECT target_profile_id, payload_json FROM deployment_capacity_reservations
            WHERE id=? AND customer_id=? AND deployment_id=?
            """, (rs, index) -> new History(rs.getString("target_profile_id"), rs.getString("payload_json")),
            reservationId, deployment.getCustomerId(), deploymentId);
        if (rows.size() != 1) throw deny(RESOURCE_SCOPE_MISMATCH);
        var row = rows.getFirst();
        authorizeRead(offerId, deployment, row.targetProfileId());
        return summary(deployment, reservationId, row);
    }

    public List<Summary> list(String offerId, String deploymentId, String targetProfileId) {
        var deployment = ownedDeployment(offerId, deploymentId);
        authorizeRead(offerId, deployment, targetProfileId);
        return jdbc.query("""
            SELECT id, target_profile_id, payload_json FROM deployment_capacity_reservations
            WHERE customer_id=? AND deployment_id=? AND target_profile_id=? ORDER BY updated_at DESC LIMIT 200
            """, (rs, index) -> summary(deployment, rs.getString("id"),
                new History(rs.getString("target_profile_id"), rs.getString("payload_json"))),
            deployment.getCustomerId(), deploymentId, targetProfileId);
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

    private void authorizeRead(String offerId, DeploymentEntity deployment, String targetProfileId) {
        if (blank(targetProfileId, 64)) throw invalid();
        authorization.authorizeOwnedTargetRead(deployment.getCustomerId(), new AuthorizationRequest(offerId,
            targetProfileId, deployment.getId(), deployment.getTenantId(), null, Operation.READ, null,
            Set.of(), null, null, null));
    }

    private Summary summary(DeploymentEntity deployment, String id, History row) {
        JsonNode payload = codec.tree(row.payload());
        if (!id.equals(payload.path("id").asText())
            || !deployment.getId().equals(payload.path("deploymentId").asText())
            || !deployment.getCustomerId().equals(payload.path("customerId").asText())
            || !deployment.getTenantId().equals(payload.path("tenantId").asText())
            || !row.targetProfileId().equals(payload.path("targetProfileId").asText())) throw deny(RESOURCE_SCOPE_MISMATCH);
        String state = payload.path("state").asText();
        try {
            return new Summary(id, deployment.getId(), row.targetProfileId(),
                TargetIntent.valueOf(payload.path("intent").asText()),
                Operation.valueOf(payload.path("customerOperation").asText()), state,
                payload.path("revision").longValue(), instant(payload, "expiresAt"), payload.path("expiryState").asText(),
                instant(payload, "keepUntil"), !Set.of("RELEASED", "RETAINED_EXTERNAL").contains(state), null);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy reservation history is unreadable.");
        }
    }

    private Instant instant(JsonNode value, String field) {
        return value.path(field).isNull() || value.path(field).isMissingNode() ? null
            : codec.read(value.path(field).toString(), Instant.class);
    }

    private ResponseStatusException retired() {
        return new ResponseStatusException(HttpStatus.GONE,
            "LEGACY_CAPACITY_OPERATIONS_RETIRED: capacity history is read-only; no reservation was changed.");
    }

    private boolean blank(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.trim());
    }

    private record History(String targetProfileId, String payload) { }
}
