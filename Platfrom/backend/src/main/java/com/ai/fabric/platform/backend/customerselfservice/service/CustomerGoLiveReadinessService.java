package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceReadinessService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Item;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Phase;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Status;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingKind;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.admission.ReviewedManagedDependencyEvidenceService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPromotionCandidateService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Derives go-live status from immutable candidate evidence. It has no mutable checklist state. */
@Service
public class CustomerGoLiveReadinessService {
    private static final List<String> DOCUMENT_CHECKS = List.of(
        "document_source_connector_ready",
        "document_active_sources_ready",
        "document_retrieval_evidence_ready"
    );
    private static final String DATASET_CHECK = "marketplace_dataset_sync_matches_expected";

    private final CustomerOfferEntitlementService entitlements;
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentVerificationRunRepository verifications;
    private final PlatformConsumerRepository consumers;
    private final AIWorkspaceInstallationRepository installations;
    private final AIWorkspaceReadinessService workspaceReadiness;
    private final DeploymentPromotionCandidateService candidates;
    private final DeploymentAdmissionService admissions;
    private final TargetRetirementOperationRepository retirements;
    private final CustomerReviewedCompositionService composition;
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence;
    private final ObjectMapper mapper;

    public CustomerGoLiveReadinessService(CustomerOfferEntitlementService entitlements,
                                          CustomerOfferDeploymentRepository associations,
                                          DeploymentRepository deployments,
                                          DeploymentReleaseRepository releases,
                                          DeploymentVersionRepository versions,
                                          DeploymentTargetProfileRepository targets,
                                          DeploymentVerificationRunRepository verifications,
                                          PlatformConsumerRepository consumers,
                                          AIWorkspaceInstallationRepository installations,
                                          AIWorkspaceReadinessService workspaceReadiness,
                                          DeploymentPromotionCandidateService candidates,
                                          DeploymentAdmissionService admissions,
                                          TargetRetirementOperationRepository retirements,
                                          CustomerReviewedCompositionService composition,
                                          ReviewedManagedDependencyEvidenceService dependencyEvidence,
                                          ObjectMapper mapper) {
        this.entitlements = entitlements;
        this.associations = associations;
        this.deployments = deployments;
        this.releases = releases;
        this.versions = versions;
        this.targets = targets;
        this.verifications = verifications;
        this.consumers = consumers;
        this.installations = installations;
        this.workspaceReadiness = workspaceReadiness;
        this.candidates = candidates;
        this.admissions = admissions;
        this.retirements = retirements;
        this.composition = composition;
        this.dependencyEvidence = dependencyEvidence;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Summary project(String offerId, String deploymentId, String releaseId, String consumerId) {
        required(offerId, "offerId", 64);
        required(deploymentId, "deploymentId", 64);
        required(releaseId, "releaseId", 64);
        required(consumerId, "consumerId", 128);
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findById(deploymentId)
            .filter(value -> actor.customerId().equals(value.getCustomerId()))
            .orElseThrow(this::notFound);
        var association = requireAssociation(offerId, deployment, actor.customerId());
        var entitlement = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), false);
        if (entitlement.getRevision() < association.getAuthorizationEntitlementRevision()) throw notFound();
        var offer = entitlements.requireOffer(offerId);
        if (!offer.getFingerprint().equals(association.getOfferFingerprint())) throw notFound();
        var definition = entitlements.definition(offer);
        var release = releases.findById(releaseId)
            .filter(value -> deploymentId.equals(value.getDeploymentId()))
            .orElseThrow(this::notFound);
        var version = versions.findById(release.getDeploymentVersionId())
            .filter(value -> deploymentId.equals(value.getDeploymentId()))
            .orElseThrow(this::notFound);
        var consumer = consumers.findByCustomerIdAndConsumerIdIgnoreCase(actor.customerId(), consumerId)
            .orElseThrow(this::notFound);
        var target = requireProductionTarget(definition, release);
        return build(association, definition, deployment, version, release, target, consumer, Instant.now());
    }

    /** Staff and customer activation share this fail-closed guard for reviewed self-service offers. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActivationReady(DeploymentEntity deployment, DeploymentReleaseEntity release,
                                       PlatformConsumerEntity consumer) {
        CustomerOfferDeploymentEntity association = associations.findById(deployment.getId()).orElse(null);
        if (association == null) return;
        var offer = entitlements.requireOffer(association.getOfferId());
        if (!deployment.getCustomerId().equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())
            || !offer.getFingerprint().equals(association.getOfferFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviewed customer deployment association changed.");
        }
        entitlements.requireEntitlement(deployment.getCustomerId(), association.getOfferId(), null, false);
        Definition definition = entitlements.definition(offer);
        DeploymentVersionEntity version = versions.findById(release.getDeploymentVersionId())
            .filter(value -> deployment.getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Production candidate version is unavailable."));
        DeploymentTargetProfileEntity target = requireProductionTarget(definition, release);
        Summary summary = build(association, definition, deployment, version, release, target, consumer, Instant.now());
        if (!summary.activationReady()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Production activation is blocked by candidate readiness: " + String.join("; ", summary.blockers()));
        }
    }

    private Summary build(CustomerOfferDeploymentEntity association, Definition definition,
                          DeploymentEntity deployment, DeploymentVersionEntity version,
                          DeploymentReleaseEntity release, DeploymentTargetProfileEntity target,
                          PlatformConsumerEntity consumer, Instant now) {
        List<Item> items = new ArrayList<>();
        CandidateEvidence candidate = candidateEvidence(definition, deployment, version, release, target, consumer, now);
        items.add(candidate.composition());

        Reservation reservation;
        try {
            reservation = admissions.activeCustomerReleaseReservation(deployment.getId(), release.getId());
        } catch (RuntimeException error) {
            reservation = null;
        }
        items.add(connectionItem(reservation, now));
        items.add(knowledgeItem(definition, candidate.run(), now));
        items.add(workspaceItem(definition, deployment, release, consumer, now));
        items.add(securityItem(candidate, now));
        items.add(operationsItem(release, reservation, now));
        items.add(integrationItem(definition, release, now));
        items.add(postActivationItem(deployment, release, consumer, candidate.node(), now));

        List<String> blockers = items.stream()
            .filter(Item::mandatory)
            .filter(item -> item.phase() == Phase.ACTIVATION)
            .filter(item -> item.status() != Status.PASSED && item.status() != Status.NOT_APPLICABLE)
            .map(item -> item.label() + ": " + nonBlank(item.blocker(), "Evidence is not ready."))
            .toList();
        boolean postComplete = items.stream()
            .filter(Item::mandatory)
            .filter(item -> item.phase() == Phase.POST_ACTIVATION)
            .allMatch(item -> item.status() == Status.PASSED || item.status() == Status.NOT_APPLICABLE);
        return new Summary(deployment.getId(), association.getOfferId(), version.getId(), release.getId(),
            target.getId(), release.getPromotionCandidateFingerprint(), consumer.getConsumerId(),
            candidate.expectedBindingRevision(), blockers.isEmpty(), postComplete, blockers, items, now);
    }

    private CandidateEvidence candidateEvidence(Definition definition, DeploymentEntity deployment,
                                                DeploymentVersionEntity version, DeploymentReleaseEntity release,
                                                DeploymentTargetProfileEntity target, PlatformConsumerEntity consumer,
                                                Instant now) {
        List<String> failures = new ArrayList<>();
        ObjectNode node = null;
        long expectedRevision = -1;
        if (!"APPLIED_VERIFIED".equalsIgnoreCase(release.getStatus())
            || !"PASSED".equalsIgnoreCase(release.getVerificationStatus())) {
            failures.add("Production release has not completed verified apply.");
        }
        try { composition.requireVersion(definition, version); }
        catch (RuntimeException error) { failures.add("Published version no longer matches the reviewed composition."); }
        try { dependencyEvidence.requireProductionPersistence(definition); }
        catch (RuntimeException error) { failures.add("Managed production dependency persistence is not supported or proven."); }
        try { composition.requireProfile(target, definition.targets().stream()
            .filter(value -> value.targetProfileId().equals(target.getId()))
            .findFirst().orElseThrow()); }
        catch (RuntimeException error) { failures.add("Production target no longer matches its reviewed profile."); }
        if (definition.artifacts().stream().noneMatch(pin -> pin.artifactId().equals(release.getSourceArtifactId()))) {
            failures.add("Production image is not pinned by the reviewed offer.");
        }
        try {
            JsonNode parsed = mapper.readTree(release.getPromotionCandidateJson());
            if (!parsed.isObject()) throw new IllegalArgumentException("candidate");
            node = (ObjectNode) parsed;
            expectedRevision = node.path("expectedBindingRevision").asLong(-1);
            if (!release.getPromotionCandidateFingerprint().equals(candidates.fingerprint(node))) {
                failures.add("Frozen promotion candidate fingerprint changed.");
            }
            exact(node, "deploymentId", deployment.getId(), failures);
            exact(node, "customerId", deployment.getCustomerId(), failures);
            exact(node, "consumerId", consumer.getConsumerId(), failures);
            exact(node, "versionId", version.getId(), failures);
            exact(node, "productionTargetProfileId", target.getId(), failures);
            if (expectedRevision < 0) failures.add("Frozen consumer binding revision is missing.");
            boolean beforeActivation = expectedRevision == consumer.getBindingRevision()
                && node.path("stagingReleaseId").asText().equals(consumer.getBoundReleaseId());
            boolean afterActivation = release.getId().equals(consumer.getBoundReleaseId())
                && consumer.getBindingRevision() > expectedRevision;
            if (!beforeActivation && !afterActivation) failures.add("Consumer binding changed after the candidate was reviewed.");
        } catch (Exception error) {
            failures.add("Frozen promotion candidate is missing or invalid.");
        }

        DeploymentVerificationRunEntity run = null;
        try {
            candidates.requireFreshVerification(deployment, version, release, target);
            run = verifications.findById(release.getVerificationRunId()).orElse(null);
            if (run == null) failures.add("Exact verification run is unavailable.");
        } catch (RuntimeException error) {
            failures.add("Exact verification evidence is missing, failed, or stale.");
        }
        Status status = failures.isEmpty() ? Status.PASSED
            : release.getPromotionCandidateJson() == null ? Status.PENDING : Status.BLOCKED;
        Item item = item("reviewed_composition", "Composition", "Reviewed composition and candidate",
            Phase.ACTIVATION, status, true, "Platform release owner",
            reference("candidate", release.getPromotionCandidateFingerprint()),
            release.getUpdatedAt(), runExpiry(run, target), failures, "Regenerate and verify the exact production candidate.");
        return new CandidateEvidence(node, expectedRevision, run, item);
    }

    private Item connectionItem(Reservation reservation, Instant now) {
        List<String> failures = new ArrayList<>();
        if (reservation == null) failures.add("A current production admission reservation is missing.");
        else if (reservation.prerequisiteReadback() == null
            || reservation.prerequisiteReadback().validUntil() == null
            || !reservation.prerequisiteReadback().validUntil().isAfter(now)
            || !StringUtils.hasText(reservation.prerequisiteReadback().evidenceReference())
            || reservation.prerequisiteReadback().placement() == null) {
            failures.add("Production connection and prerequisite readback is missing or stale.");
        }
        return item("production_connections", "Connections", "Production connections and bindings",
            Phase.ACTIVATION, failures.isEmpty() ? Status.PASSED : Status.BLOCKED, true,
            "Customer and Platform operator", reservationReference(reservation),
            reservation == null || reservation.prerequisiteReadback() == null ? null
                : reservation.prerequisiteReadback().observedAt(),
            reservation == null || reservation.prerequisiteReadback() == null ? null
                : reservation.prerequisiteReadback().validUntil(), failures,
            "Refresh customer-owned storage, identity, provider, and target prerequisite evidence.");
    }

    private Item knowledgeItem(Definition definition, DeploymentVerificationRunEntity run, Instant now) {
        boolean required = !definition.documentAudiences().isEmpty() || definition.bindingSlots().stream()
            .anyMatch(slot -> slot.kind() == BindingKind.CUSTOMER_S3);
        Map<String, JsonNode> checks = checks(run);
        List<String> failures = new ArrayList<>();
        int passed = 0;
        for (String name : DOCUMENT_CHECKS) {
            JsonNode check = checks.get(name);
            if (check != null && "PASSED".equalsIgnoreCase(check.path("status").asText())) passed++;
            else if (required) failures.add(name + " is not PASSED.");
        }
        JsonNode dataset = checks.get(DATASET_CHECK);
        if (dataset != null && ("FAILED".equalsIgnoreCase(dataset.path("status").asText())
            || "WARNING".equalsIgnoreCase(dataset.path("status").asText()))) {
            failures.add(DATASET_CHECK + " is not ready.");
        }
        Status status = !failures.isEmpty() ? Status.BLOCKED
            : required || passed > 0 ? Status.PASSED : Status.NOT_APPLICABLE;
        return item("knowledge_evidence", "Knowledge", "Indexed knowledge and retrieval evidence",
            Phase.ACTIVATION, status, required, "Customer knowledge owner",
            run == null ? null : "verification:" + run.getId() + "#knowledge",
            run == null ? null : run.getCompletedAt(), null, failures,
            "Synchronize, index, and prove retrieval against the exact production release.");
    }

    private Item workspaceItem(Definition definition, DeploymentEntity deployment, DeploymentReleaseEntity release,
                               PlatformConsumerEntity consumer, Instant now) {
        boolean required = definition.operations().contains(Operation.APP_CONFIGURE);
        if (!required) {
            return item("experience_delivery", "Experience", "AI Workspace delivery",
                Phase.ACTIVATION, Status.NOT_APPLICABLE, false, "Customer app owner", null,
                now, null, List.of(), null);
        }
        var active = installations.findByConsumerEntityIdAndStatusIn(consumer.getId(),
            List.of(AIWorkspaceInstallationStatus.ACTIVE));
        var evaluated = active.stream().map(installation -> new WorkspaceEvidence(installation,
                workspaceReadiness.evaluateAgainst(installation, consumer, deployment.getId(), release.getId())))
            .toList();
        var ready = evaluated.stream()
            .filter(value -> value.summary().ready()).findFirst().orElse(null);
        List<String> failures = new ArrayList<>();
        if (ready == null) {
            if (active.isEmpty()) failures.add("No active AI Workspace installation is assigned to this consumer.");
            else evaluated.stream().flatMap(value -> value.summary().checks().stream())
                .filter(check -> "BLOCKED".equals(check.status()))
                .limit(3).forEach(check -> failures.add(check.code() + ": " + check.message()));
        }
        return item("experience_delivery", "Experience", "AI Workspace delivery",
            Phase.ACTIVATION, ready == null ? Status.BLOCKED : Status.PASSED, true,
            "Customer app owner", ready == null ? null : "ai-workspace:"
                + ready.installation().getInstallationId() + ":" + ready.summary().assignmentRevision(),
            ready == null ? null : ready.installation().getUpdatedAt(), null, failures,
            "Activate and verify an installation against this exact production release.");
    }

    private Item securityItem(CandidateEvidence candidate, Instant now) {
        List<String> failures = new ArrayList<>();
        if (candidate.run() == null || !"PASSED".equalsIgnoreCase(candidate.run().getStatus())) {
            failures.add("The exact post-apply verification run is not PASSED.");
        }
        return item("security_behavior", "Security", "Security and behavior verification",
            Phase.ACTIVATION, failures.isEmpty() ? Status.PASSED : Status.BLOCKED, true,
            "Platform verification owner", candidate.run() == null ? null : "verification:" + candidate.run().getId(),
            candidate.run() == null ? null : candidate.run().getCompletedAt(), null, failures,
            "Run exact-release security, behavior, and runtime verification again.");
    }

    private Item operationsItem(DeploymentReleaseEntity release, Reservation reservation, Instant now) {
        List<String> failures = new ArrayList<>();
        if (!"APPLIED_VERIFIED".equalsIgnoreCase(release.getStatus())
            || !"PASSED".equalsIgnoreCase(release.getVerificationStatus())) {
            failures.add("Production release is not in verified operating state.");
        }
        if (reservation == null) failures.add("Production capacity and lifecycle reservation is missing.");
        return item("operations_ready", "Operations", "Capacity, lifecycle, and recovery controls",
            Phase.ACTIVATION, failures.isEmpty() ? Status.PASSED : Status.BLOCKED, true,
            "Platform operator", reservationReference(reservation),
            reservation == null ? release.getUpdatedAt() : reservation.updatedAt(),
            reservation == null ? null : reservation.expiresAt(), failures,
            "Restore a current production reservation and repeat operational verification.");
    }

    private Item integrationItem(Definition definition, DeploymentReleaseEntity release, Instant now) {
        List<String> failures = new ArrayList<>();
        if (!StringUtils.hasText(definition.approvedPolicyRef())
            || !StringUtils.hasText(definition.operatingPolicyRef())) failures.add("Reviewed integration policy is incomplete.");
        if (definition.artifacts().stream().noneMatch(pin -> pin.artifactId().equals(release.getSourceArtifactId()))) {
            failures.add("Runtime artifact is not an approved integration artifact.");
        }
        return item("integration_truth", "Integration", "Reviewed integration contract",
            Phase.ACTIVATION, failures.isEmpty() ? Status.PASSED : Status.BLOCKED, true,
            "Offer reviewer", failures.isEmpty() ? definition.approvedPolicyRef() + " | "
                + definition.operatingPolicyRef() : null, release.getUpdatedAt(), null, failures,
            "Review and republish the integration and operating policy contract.");
    }

    private Item postActivationItem(DeploymentEntity deployment, DeploymentReleaseEntity release,
                                    PlatformConsumerEntity consumer, ObjectNode candidate, Instant now) {
        boolean activated = release.getId().equals(consumer.getBoundReleaseId())
            && release.getTargetProfileId().equals(consumer.getBoundTargetProfileId());
        if (!activated) {
            return item("staging_disposition", "Lifecycle", "Post-activation staging disposition",
                Phase.POST_ACTIVATION, Status.PENDING, true, "Customer and Platform operator", null,
                now, null, List.of("Production is not active yet."),
                "After activation, retire staging or retain it through a bounded entitlement.");
        }
        String stagingTarget = candidate == null ? null : candidate.path("stagingTargetProfileId").asText(null);
        if (!StringUtils.hasText(stagingTarget)) {
            return item("staging_disposition", "Lifecycle", "Post-activation staging disposition",
                Phase.POST_ACTIVATION, Status.BLOCKED, true, "Platform operator", null,
                now, null, List.of("Frozen staging target identity is unavailable."),
                "Recover the exact promotion candidate before staging disposition.");
        }
        var complete = retirements.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(
                deployment.getId(), stagingTarget).stream()
            .filter(value -> value.getState() == TargetRetirementContracts.State.COMPLETE)
            .findFirst().orElse(null);
        if (complete != null) {
            return item("staging_disposition", "Lifecycle", "Post-activation staging disposition",
                Phase.POST_ACTIVATION, Status.PASSED, true, "Platform operator",
                "retirement:" + complete.getId(), complete.getCompletedAt(), null, List.of(), null);
        }
        try {
            var retained = admissions.activeCustomerReservations(deployment.getId(), stagingTarget).stream()
                .filter(value -> value.keepUntil() != null && value.keepUntil().isAfter(now))
                .filter(value -> StringUtils.hasText(value.keepEntitlementReference()))
                .findFirst().orElse(null);
            if (retained != null) {
                return item("staging_disposition", "Lifecycle", "Post-activation staging disposition",
                    Phase.POST_ACTIVATION, Status.PASSED, true, "Customer and Platform operator",
                    reservationReference(retained), retained.updatedAt(), retained.keepUntil(), List.of(), null);
            }
        } catch (RuntimeException ignored) {
            // The durable retirement ledger remains the primary evidence path.
        }
        return item("staging_disposition", "Lifecycle", "Post-activation staging disposition",
            Phase.POST_ACTIVATION, Status.BLOCKED, true, "Customer and Platform operator", null,
            now, null, List.of("Staging is neither retired nor covered by a current retention entitlement."),
            "Retire staging or record a bounded keep-until entitlement.");
    }

    private CustomerOfferDeploymentEntity requireAssociation(String offerId, DeploymentEntity deployment, String customerId) {
        var association = associations.findById(deployment.getId()).orElseThrow(this::notFound);
        if (!customerId.equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())
            || !offerId.equals(association.getOfferId())) throw notFound();
        return association;
    }

    private DeploymentTargetProfileEntity requireProductionTarget(Definition definition, DeploymentReleaseEntity release) {
        var permission = definition.targets().stream()
            .filter(value -> value.targetProfileId().equals(release.getTargetProfileId()))
            .filter(value -> value.intent() == TargetIntent.CUSTOMER_PRODUCTION)
            .findFirst().orElseThrow(this::notFound);
        return targets.findById(permission.targetProfileId())
            .filter(value -> value.isActive() && "production".equalsIgnoreCase(value.getEnvironmentName()))
            .orElseThrow(this::notFound);
    }

    private Map<String, JsonNode> checks(DeploymentVerificationRunEntity run) {
        if (run == null) return Map.of();
        try {
            JsonNode parsed = mapper.readTree(run.getChecksJson());
            if (!parsed.isArray()) return Map.of();
            Map<String, JsonNode> values = new LinkedHashMap<>();
            parsed.forEach(check -> {
                String name = check.path("name").asText();
                if (StringUtils.hasText(name)) values.put(name, check);
            });
            return values;
        } catch (Exception error) {
            return Map.of();
        }
    }

    private Instant runExpiry(DeploymentVerificationRunEntity run, DeploymentTargetProfileEntity target) {
        if (run == null || run.getCompletedAt() == null) return null;
        try {
            long seconds = mapper.readTree(target.getProviderConfigJson())
                .path("promotionEvidenceMaxAgeSeconds").asLong(0);
            return seconds > 0 ? run.getCompletedAt().plusSeconds(seconds) : null;
        } catch (Exception error) {
            return null;
        }
    }

    private Item item(String code, String category, String label, Phase phase, Status status,
                      boolean mandatory, String owner, String evidenceReference,
                      Instant checkedAt, Instant expiresAt, List<String> failures, String nextAction) {
        return new Item(code, category, label, phase, status, mandatory, owner, evidenceReference,
            checkedAt, expiresAt, failures == null || failures.isEmpty() ? null : String.join(" ", failures),
            failures == null || failures.isEmpty() ? null : nextAction);
    }

    private void exact(ObjectNode node, String field, String expected, List<String> failures) {
        if (!expected.equals(node.path(field).asText())) failures.add("Frozen candidate " + field + " changed.");
    }

    private String reservationReference(Reservation reservation) {
        return reservation == null ? null : "admission:" + reservation.id() + ":"
            + reservation.generation() + ":" + reservation.revision();
    }

    private String reference(String kind, String value) {
        return StringUtils.hasText(value) ? kind + ":" + value : null;
    }

    private String nonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value) || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Go-live candidate not found.");
    }

    private record CandidateEvidence(ObjectNode node, long expectedBindingRevision,
                                     DeploymentVerificationRunEntity run, Item composition) { }
    private record WorkspaceEvidence(com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity installation,
                                     com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary summary) { }
}
