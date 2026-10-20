package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Production evidence is derived from reviewed records plus fresh provider placement, never request flags. */
@Component
class ProductionAdmissionEvidenceSource implements AdmissionEvidenceSource {
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentDocumentBindingResolver documentBindings;
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence;
    private final CustomerTargetPlacementService placement;
    private final AdmissionOwnerContactResolver owners;
    private final AdmissionLifecycleProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AdmissionJson json;
    private final Clock clock;

    @Autowired
    private com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver externalIntegrations;
    @Autowired
    private AdmissionStore admissions;

    @Autowired
    ProductionAdmissionEvidenceSource(DeploymentTargetProfileRepository targets,
                                      DeploymentRepository deployments,
                                      DeploymentVersionRepository versions,
                                      DeploymentDocumentBindingResolver documentBindings,
                                      ReviewedManagedDependencyEvidenceService dependencyEvidence,
                                      CustomerTargetPlacementService placement,
                                      AdmissionOwnerContactResolver owners,
                                      AdmissionLifecycleProperties properties,
                                      ObjectProvider<JavaMailSender> mailSender,
                                      AdmissionJson json) {
        this(targets, deployments, versions, documentBindings, dependencyEvidence, placement, owners, properties,
            mailSender, json, Clock.systemUTC());
    }

    ProductionAdmissionEvidenceSource(DeploymentTargetProfileRepository targets,
                                      DeploymentRepository deployments,
                                      DeploymentVersionRepository versions,
                                      DeploymentDocumentBindingResolver documentBindings,
                                      ReviewedManagedDependencyEvidenceService dependencyEvidence,
                                      CustomerTargetPlacementService placement,
                                      AdmissionOwnerContactResolver owners,
                                      AdmissionLifecycleProperties properties,
                                      ObjectProvider<JavaMailSender> mailSender,
                                      AdmissionJson json,
                                      Clock clock) {
        this.targets = targets;
        this.deployments = deployments;
        this.versions = versions;
        this.documentBindings = documentBindings;
        this.dependencyEvidence = dependencyEvidence;
        this.placement = placement;
        this.owners = owners;
        this.properties = properties;
        this.mailSender = mailSender;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public PrerequisiteReadback read(AdmissionContext context) {
        return read(context, false);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PrerequisiteReadback readForExecution(AdmissionContext context) {
        return read(context, true);
    }

    private PrerequisiteReadback read(AdmissionContext context, boolean execution) {
        if (context == null || context.manifest() == null || context.policy() == null || context.grant() == null
            || context.target() == null) throw unavailable("ADMISSION_CONTEXT_REQUIRED");
        requireNoticeTransport(context);
        owners.resolve(context.customerId(), context.grant().ownerContactEvidenceReference());

        var profile = targets.findById(context.target().targetProfileId())
            .orElseThrow(() -> unavailable("TARGET_PLACEMENT_EVIDENCE_REQUIRED"));
        var placementEvidence = execution
            ? placement.probeAndRecord(profile, intent(context.target().intent()))
            : placement.requireFreshEvidence(profile, intent(context.target().intent()));
        if (execution) {
            profile = targets.findById(context.target().targetProfileId())
                .orElseThrow(() -> unavailable("TARGET_PLACEMENT_EVIDENCE_REQUIRED"));
            placementEvidence = placement.requireEvidenceById(profile, intent(context.target().intent()), placementEvidence.getId());
        }
        Placement observedPlacement = new Placement(placementEvidence.getServerUuid(), placementEvidence.getDestinationUuid(),
            placementEvidence.getServerAddress());
        if (!context.target().placement().equals(observedPlacement)
            || !context.target().profileFingerprint().equals(placementEvidence.getProfileFingerprint())) {
            throw unavailable("TARGET_PLACEMENT_EVIDENCE_CHANGED");
        }

        var version = versions.findById(context.manifest().versionId())
            .orElseThrow(() -> unavailable("IMMUTABLE_VERSION_EVIDENCE_REQUIRED"));
        if (!context.deploymentId().equals(version.getDeploymentId()) || !"PUBLISHED".equalsIgnoreCase(version.getStatus())
            || !context.manifest().versionConfigHash().equals(version.getConfigHash())) {
            throw unavailable("IMMUTABLE_VERSION_EVIDENCE_CHANGED");
        }

        Set<String> supported = new LinkedHashSet<>(supportedReferences(context, version, placementEvidence.getId()));
        for (String reference : context.manifest().prerequisiteReferences()) {
            if (!reference.startsWith("placement:") || supported.contains(reference)) continue;
            placement.requireEvidenceIdentity(profile, intent(context.target().intent()),
                reference.substring("placement:".length()), placementEvidence.getId());
            supported.add(reference);
        }
        if (!supported.containsAll(context.manifest().prerequisiteReferences())) {
            throw unavailable("TYPED_PREREQUISITE_EVIDENCE_REQUIRED");
        }
        Instant now = clock.instant();
        Instant validUntil = minimum(List.of(
            placementEvidence.getExpiresAt(),
            context.policy().fleet().validUntil(),
            context.target().budget().validUntil(),
            context.grant().validUntil()
        ));
        if (!validUntil.isAfter(now)) throw unavailable("ADMISSION_PREREQUISITE_EXPIRED");
        String evidenceReference = "admission-evidence:" + json.fingerprint(Map.of(
            "customerId", context.customerId(),
            "deploymentId", context.deploymentId(),
            "manifestFingerprint", context.manifestFingerprint(),
            "policyFingerprint", json.fingerprint(context.policy()),
            "policyContinuity", context.policyAuthority() == null ? "" : context.policyAuthority().continuityFingerprint(),
            "placementEvidenceId", placementEvidence.getId(),
            "references", new java.util.TreeSet<>(context.manifest().prerequisiteReferences())
        ));
        return new PrerequisiteReadback(
            placementEvidence.getCheckedAt(), validUntil, evidenceReference, observedPlacement,
            context.target().profileFingerprint(), context.manifestFingerprint(), context.manifest().bindingFingerprint(),
            context.manifest().prerequisiteReferences(), context.policy().restoreEvidenceReference(),
            context.policy().privacyEvidenceReference(), context.grant().ownerContactEvidenceReference(),
            context.policy().lifecycle().noticeTransportReference()
        );
    }

    private void requireNoticeTransport(AdmissionContext context) {
        if (!properties.workerEnabled() || !properties.retirementWorkerEnabled()
            || !properties.consoleEnabled() || !properties.ownerEmailEnabled()
            || properties.ownerEmailFrom().isBlank() || mailSender.getIfAvailable() == null
            || !properties.noticeTransportReference().equals(context.policy().lifecycle().noticeTransportReference())) {
            throw unavailable("NOTICE_TRANSPORT_UNAVAILABLE");
        }
    }

    private Set<String> supportedReferences(AdmissionContext context,
                                            com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity version,
                                            String placementEvidenceId) {
        Set<String> references = new LinkedHashSet<>();
        if (context.policyAuthority() != null) {
            if (admissions == null) throw unavailable("POLICY_CONTINUITY_REQUIRED");
            references.addAll(admissions.authorizedPolicyReferences(context));
        } else {
            references.add("policy:" + context.policy().id());
        }
        references.add(context.policy().lifecycle().policyReference());
        references.add(context.policy().restoreEvidenceReference());
        references.add(context.policy().privacyEvidenceReference());
        references.add(context.policy().supportReference());
        references.add(context.policy().lifecycle().noticeTransportReference());
        references.add(context.grant().ownerContactEvidenceReference());
        references.add(context.grant().payerReference());
        references.add(context.manifest().topologyReference());
        references.add("deployment-version:" + context.manifest().versionId() + ":" + context.manifest().versionConfigHash());
        references.add("binding:" + context.manifest().bindingFingerprint());
        references.add("placement:" + placementEvidenceId);
        var dependency = context.target().intent() == Intent.CUSTOMER_PRODUCTION
            ? dependencyEvidence.resolveForProductionDeployment(context.customerId(), context.tenantId(),
                context.deploymentId(), context.manifest().topologyReference(), context.manifest().demand())
            : dependencyEvidence.resolveForDeployment(context.customerId(), context.tenantId(),
                context.deploymentId(), context.manifest().topologyReference(), context.manifest().demand());
        if (dependency.isPresent()
            && !context.manifest().prerequisiteReferences().contains(dependency.get().reference())) {
            throw unavailable("REVIEWED_DEPENDENCY_PREREQUISITE_REQUIRED");
        }
        if (dependency.isEmpty() && context.manifest().prerequisiteReferences().stream()
            .anyMatch(reference -> reference.startsWith("managed-dependency-spec:"))) {
            throw unavailable("UNEXPECTED_REVIEWED_DEPENDENCY_PREREQUISITE");
        }
        dependency.ifPresent(value -> references.add(value.reference()));
        var deployment = deployments.findById(context.deploymentId())
            .orElseThrow(() -> unavailable("DEPLOYMENT_EVIDENCE_REQUIRED"));
        var documentBinding = documentBindings.snapshot(deployment, version, context.target().targetProfileId());
        if (!documentBinding.isEmpty()) {
            references.add("document-binding:" + json.fingerprint(documentBinding));
        }
        Set<String> externalReferences = new LinkedHashSet<>();
        if (com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationRequirement.required(
            json.read(version.getCompositionProvenanceJson() == null ? "{}" : version.getCompositionProvenanceJson(),
                com.fasterxml.jackson.databind.JsonNode.class))) {
            externalIntegrations.resolveLocal(deployment.getId(), version.getId(), context.target().targetProfileId())
                .forEach(binding -> externalReferences.add("external-integration:" + binding.fingerprint()));
        }
        var reviewedExternal = context.manifest().prerequisiteReferences().stream()
            .filter(reference -> reference.startsWith("external-integration:"))
            .collect(java.util.stream.Collectors.toSet());
        if (!externalReferences.equals(reviewedExternal)) throw unavailable("EXTERNAL_INTEGRATION_PREREQUISITE_CHANGED");
        references.addAll(externalReferences);
        return Set.copyOf(references);
    }

    private TargetIntent intent(Intent value) {
        return value == Intent.CUSTOMER_STAGING ? TargetIntent.CUSTOMER_STAGING : TargetIntent.CUSTOMER_PRODUCTION;
    }

    private Instant minimum(List<Instant> values) {
        if (values.stream().anyMatch(java.util.Objects::isNull)) throw unavailable("ADMISSION_PREREQUISITE_EXPIRED");
        return values.stream().min(Instant::compareTo).orElseThrow();
    }

    private AdmissionFailure unavailable(String code) {
        return new AdmissionFailure(PRECONDITION_FAILED, code);
    }
}
