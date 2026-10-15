package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Revalidates a release's source access without renewing or allocating deployment capacity. */
@Service
public class CustomerSourcePrerequisiteRefreshService {
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferEntitlementService entitlements;
    private final DeploymentTargetContextService targets;
    private final DeploymentAdmissionCustomerPrerequisiteEvidenceProvider prerequisites;
    private final CustomerTargetPlacementService placement;
    private final CustomerSourceConnectorPrerequisiteProbe connector;
    private final CustomerSourcePrerequisiteEvidenceStore evidence;
    private final CustomerSelfServiceProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final EntityManager entityManager;

    @Autowired
    CustomerSourcePrerequisiteRefreshService(CustomerOfferDeploymentRepository associations,
        CustomerSelfServiceAuthorizationService authorization, CustomerOfferEntitlementService entitlements,
        DeploymentTargetContextService targets, DeploymentAdmissionCustomerPrerequisiteEvidenceProvider prerequisites,
        CustomerTargetPlacementService placement, CustomerSourceConnectorPrerequisiteProbe connector,
        CustomerSourcePrerequisiteEvidenceStore evidence, CustomerSelfServiceProperties properties,
        PlatformTransactionManager transactions, EntityManager entityManager) {
        this(associations, authorization, entitlements, targets, prerequisites, placement, connector, evidence, properties,
            transactions, entityManager, Clock.systemUTC());
    }

    CustomerSourcePrerequisiteRefreshService(CustomerOfferDeploymentRepository associations,
        CustomerSelfServiceAuthorizationService authorization, CustomerOfferEntitlementService entitlements,
        DeploymentTargetContextService targets, DeploymentAdmissionCustomerPrerequisiteEvidenceProvider prerequisites,
        CustomerTargetPlacementService placement, CustomerSourceConnectorPrerequisiteProbe connector,
        CustomerSourcePrerequisiteEvidenceStore evidence, CustomerSelfServiceProperties properties,
        PlatformTransactionManager transactions, EntityManager entityManager, Clock clock) {
        this.associations = associations;
        this.authorization = authorization;
        this.entitlements = entitlements;
        this.targets = targets;
        this.prerequisites = prerequisites;
        this.placement = placement;
        this.connector = connector;
        this.evidence = evidence;
        this.properties = properties;
        this.clock = clock;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Transactional(propagation = Propagation.NEVER)
    public void ensureFresh(DeploymentTargetReference target, Operation operation, String operationKey,
                            Set<DocumentAudience> audiences) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Source prerequisite refresh cannot run inside a transaction.");
        }
        Prepared prepared = transaction.execute(status -> {
            entityManager.clear();
            return prepare(target, operation, operationKey, audiences);
        });
        if (prepared == null || prepared.alreadyFresh()) return;
        var snapshot = prepared.snapshot();
        var placementEvidence = placement.probeAndRecord(snapshot.runtime().target(), snapshot.scope().intent());
        var proof = connector.probe(snapshot);
        transaction.executeWithoutResult(status -> {
            // Open-in-view can otherwise retain pre-probe entities across these separate transactions.
            entityManager.clear();
            Prepared current = prepare(target, operation, operationKey, audiences);
            if (current == null || !snapshot.scope().equals(current.snapshot().scope())) {
                throw unavailable("SOURCE_PREREQUISITE_SCOPE_CHANGED_DURING_PROBE");
            }
            placement.requireEvidenceById(current.snapshot().runtime().target(), snapshot.scope().intent(), placementEvidence.getId());
            Instant now = clock.instant();
            Instant expiry = minimum(proof.checkedAt().plusSeconds(Math.min(properties.evidenceMaxAgeSeconds(), snapshot.maxAgeSeconds())),
                placementEvidence.getExpiresAt(), current.expiresAt(), current.snapshot().expiresAt()).truncatedTo(ChronoUnit.MICROS);
            if (proof.checkedAt().isAfter(now) || !expiry.isAfter(now)) throw unavailable("SOURCE_PREREQUISITE_PROBE_EXPIRED");
            evidence.save(new CustomerSourcePrerequisiteEvidence("cse-" + UUID.randomUUID(), snapshot.scope(),
                placementEvidence.getId(), snapshot.passed(), proof.digest(), proof.checkedAt(), expiry));
        });
    }

    private Prepared prepare(DeploymentTargetReference target, Operation operation, String operationKey, Set<DocumentAudience> audiences) {
        if (target == null || operation == null || !isSource(operation) || audiences == null || audiences.isEmpty()) {
            throw unavailable("SOURCE_PREREQUISITE_CONTEXT_INVALID");
        }
        var runtime = targets.resolve(target.deploymentId(), target.targetProfileId(), target.releaseId(), false);
        var association = associations.findById(target.deploymentId());
        if (association.isEmpty()) return null;
        Authorization approved = null;
        for (var audience : EnumSet.copyOf(audiences)) {
            approved = authorization.prepareSourcePrerequisiteRefresh(runtime.deployment().getCustomerId(),
                new AuthorizationRequest(association.get().getOfferId(), target.targetProfileId(), target.deploymentId(),
                    runtime.deployment().getTenantId(), target.releaseId(), operation, operationKey,
                    operation == Operation.SOURCE_INDEX || operation == Operation.SOURCE_REFRESH
                        ? Set.of(Setting.DOCUMENT_AUDIENCE) : Set.of(), null, null, audience));
        }
        var context = approved.context();
        var snapshot = prerequisites.sourceSnapshot(context);
        var entitlement = entitlements.requireEntitlement(context.customerId(), context.offerId(), null, true);
        if (entitlement.getRevision() != context.entitlementRevision()) throw unavailable("SOURCE_PREREQUISITE_ENTITLEMENT_CHANGED");
        if (properties.evidenceMaxAgeSeconds() <= 0) throw unavailable("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        boolean fresh = evidence.latest(snapshot.scope()).filter(value -> fresh(value, snapshot)).isPresent();
        return new Prepared(snapshot, entitlement.getExpiresAt(), fresh);
    }

    private boolean fresh(CustomerSourcePrerequisiteEvidence value,
                          DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot) {
        Instant now = clock.instant();
        if (value.checkedAt() == null || value.checkedAt().isAfter(now)
            || value.checkedAt().isBefore(now.minusSeconds(Math.min(properties.evidenceMaxAgeSeconds(), snapshot.maxAgeSeconds())))
            || value.expiresAt() == null || !value.expiresAt().isAfter(now) || value.connectorProofDigest() == null
            || value.connectorProofDigest().isBlank() || !value.passed().containsAll(snapshot.passed())) return false;
        try {
            placement.requireEvidenceById(snapshot.runtime().target(), snapshot.scope().intent(), value.placementEvidenceId());
            return true;
        } catch (com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException ex) { return false; }
    }

    private boolean isSource(Operation operation) {
        return switch (operation) {
            case SOURCE_DISCOVER, SOURCE_PREVIEW, SOURCE_INDEX, SOURCE_REFRESH, SOURCE_REMOVE -> true;
            default -> false;
        };
    }

    private Instant minimum(Instant first, Instant... rest) {
        if (first == null) throw unavailable("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
        for (Instant value : rest) {
            if (value == null) throw unavailable("SOURCE_PREREQUISITE_REFRESH_REQUIRED");
            if (value.isBefore(first)) first = value;
        }
        return first;
    }

    private record Prepared(DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot,
                            Instant expiresAt, boolean alreadyFresh) { }
    private AdmissionFailure unavailable(String code) { return new AdmissionFailure(PRECONDITION_FAILED, code); }
}
