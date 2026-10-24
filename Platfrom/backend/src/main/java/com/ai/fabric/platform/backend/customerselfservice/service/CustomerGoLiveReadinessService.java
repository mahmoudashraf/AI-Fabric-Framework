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
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecRepository;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
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
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
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
    private final CustomerReviewedCompositionService composition;
    private final ManagedDependencySpecRepository dependencySpecs;
    private final ManagedDependencySpecValidator dependencyValidator;
    private final ManagedDependencyArtifactPolicy dependencyOwnership;
    private final CoreReleaseAuthorizationStore releaseAuthorizations;
    private final DeploymentOperationsService deploymentOperations;
    private final DeploymentDocumentBindingResolver documentBindings;
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
                                          CustomerReviewedCompositionService composition,
                                          ManagedDependencySpecRepository dependencySpecs,
                                          ManagedDependencySpecValidator dependencyValidator,
                                          ManagedDependencyArtifactPolicy dependencyOwnership,
                                          CoreReleaseAuthorizationStore releaseAuthorizations,
                                          DeploymentOperationsService deploymentOperations,
                                          DeploymentDocumentBindingResolver documentBindings,
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
        this.composition = composition;
        this.dependencySpecs = dependencySpecs;
        this.dependencyValidator = dependencyValidator;
        this.dependencyOwnership = dependencyOwnership;
        this.releaseAuthorizations = releaseAuthorizations;
        this.deploymentOperations = deploymentOperations;
        this.documentBindings = documentBindings;
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

        items.add(coreConnectionItem(deployment, version, release, candidate, now));
        items.add(knowledgeItem(definition, candidate.run(), now));
        items.add(workspaceItem(definition, deployment, release, consumer, now));
        items.add(securityItem(candidate, now));
        items.add(externalOperationsItem(release, now));
        items.add(integrationItem(definition, release, now));
        items.add(postActivationItem(release, now));

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
        try { requireProductionDependencies(definition); }
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
            if (node.path("promotionEvidenceMaxAgeSeconds").asLong(-1)
                != candidates.promotionEvidenceMaxAgeSeconds(target)) {
                failures.add("Approved promotion evidence policy changed after candidate review.");
            }
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
            run = verifications.findById(release.getVerificationRunId())
                .filter(value -> deployment.getId().equals(value.getDeploymentId())
                    && release.getId().equals(value.getReleaseId())
                    && version.getId().equals(value.getDeploymentVersionId())
                    && ("POST_APPLY".equals(value.getVerificationType())
                        || "MANUAL_RERUN".equals(value.getVerificationType())))
                .orElse(null);
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

    private void requireProductionDependencies(Definition definition) {
        var matches = dependencySpecs.findByTemplateVersionIdAndActiveTrue(definition.template().versionId());
        if (definition.topology().dependencies() == 0) {
            if (!matches.isEmpty()) throw new IllegalStateException("UNEXPECTED_REVIEWED_DEPENDENCY_SPEC");
            return;
        }
        if (matches.size() != 1) throw new IllegalStateException("REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        var reviewed = matches.getFirst();
        try {
            var spec = mapper.readValue(reviewed.getSpecJson(), ManagedDependencySpec.class);
            dependencyValidator.validate(spec);
            if (!definition.template().versionId().equals(reviewed.getTemplateVersionId())
                || !reviewed.isActive() || reviewed.getReviewedAt() == null
                || !StringUtils.hasText(reviewed.getReviewedBy()) || !StringUtils.hasText(reviewed.getReviewReference())
                || !dependencyValidator.fingerprint(spec).equals(reviewed.getFingerprint())) {
                throw new IllegalStateException("REVIEWED_DEPENDENCY_SPEC_CHANGED");
            }
            dependencyOwnership.requireAllowed(spec);
            dependencyValidator.requireProductionInboxPersistence(spec);
        } catch (Exception error) {
            throw new IllegalStateException("REVIEWED_DEPENDENCY_PRODUCTION_READINESS_UNPROVEN", error);
        }
    }

    private Item coreConnectionItem(DeploymentEntity deployment, DeploymentVersionEntity version,
                                    DeploymentReleaseEntity release, CandidateEvidence candidate, Instant now) {
        List<String> failures = new ArrayList<>();
        try {
            releaseAuthorizations.require(deployment, version, release);
            documentBindings.requireUnchanged(deployment, version, release);
            if (candidate.run() == null || !"PASSED".equals(candidate.run().getStatus()))
                failures.add("Exact production connection verification is unavailable.");
        } catch (RuntimeException error) { failures.add("Owned production release or frozen connection binding changed."); }
        return item("production_connections", "Connections", "Verified production connections and bindings",
            Phase.ACTIVATION, failures.isEmpty() ? Status.PASSED : Status.BLOCKED, true,
            "Customer and Platform operator", "release:" + release.getId(),
            candidate.run() == null ? null : candidate.run().getCompletedAt(), null, failures,
            "Verify the production release and its customer-owned integrations.");
    }

    private Item externalOperationsItem(DeploymentReleaseEntity release, Instant now) {
        String mode;
        try { mode = deploymentOperations.status().mode(); }
        catch (RuntimeException unavailable) { mode = "UNAVAILABLE"; }
        return item("operations_ready", "Operations", "Optional deployment operations: " + mode,
            Phase.ACTIVATION, Status.NOT_APPLICABLE, false, "Integration team", "release:" + release.getId(),
            release.getUpdatedAt(), null, List.of(),
            "Capacity, expiry, graceful drain and advanced recovery are optional; actual release verification remains required.");
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

    private Item postActivationItem(DeploymentReleaseEntity release, Instant now) {
        return item("staging_disposition", "Lifecycle", "Integration-team staging cleanup",
            Phase.POST_ACTIVATION, Status.NOT_APPLICABLE, false, "Integration team", "release:" + release.getId(),
            now, null, List.of(), "Stop staging when it is no longer needed; automatic retirement is not required for promotion.");
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
            return run.getCompletedAt().plusSeconds(candidates.promotionEvidenceMaxAgeSeconds(target));
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
