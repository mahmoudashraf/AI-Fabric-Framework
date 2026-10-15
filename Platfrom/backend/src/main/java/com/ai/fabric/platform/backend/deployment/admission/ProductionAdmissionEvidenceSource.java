package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

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
final class ProductionAdmissionEvidenceSource implements AdmissionEvidenceSource {
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentVersionRepository versions;
    private final CustomerTargetPlacementService placement;
    private final AdmissionOwnerContactResolver owners;
    private final AdmissionLifecycleProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AdmissionJson json;
    private final Clock clock;

    @Autowired
    ProductionAdmissionEvidenceSource(DeploymentTargetProfileRepository targets,
                                      DeploymentVersionRepository versions,
                                      CustomerTargetPlacementService placement,
                                      AdmissionOwnerContactResolver owners,
                                      AdmissionLifecycleProperties properties,
                                      ObjectProvider<JavaMailSender> mailSender,
                                      AdmissionJson json) {
        this(targets, versions, placement, owners, properties, mailSender, json, Clock.systemUTC());
    }

    ProductionAdmissionEvidenceSource(DeploymentTargetProfileRepository targets,
                                      DeploymentVersionRepository versions,
                                      CustomerTargetPlacementService placement,
                                      AdmissionOwnerContactResolver owners,
                                      AdmissionLifecycleProperties properties,
                                      ObjectProvider<JavaMailSender> mailSender,
                                      AdmissionJson json,
                                      Clock clock) {
        this.targets = targets;
        this.versions = versions;
        this.placement = placement;
        this.owners = owners;
        this.properties = properties;
        this.mailSender = mailSender;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public PrerequisiteReadback read(AdmissionContext context) {
        if (context == null || context.manifest() == null || context.policy() == null || context.grant() == null
            || context.target() == null) throw unavailable("ADMISSION_CONTEXT_REQUIRED");
        requireNoticeTransport(context);
        owners.resolve(context.customerId(), context.grant().ownerContactEvidenceReference());

        var profile = targets.findById(context.target().targetProfileId())
            .orElseThrow(() -> unavailable("TARGET_PLACEMENT_EVIDENCE_REQUIRED"));
        var placementEvidence = placement.requireFreshEvidence(profile, intent(context.target().intent()));
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

        Set<String> supported = supportedReferences(context, placementEvidence.getId());
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

    private Set<String> supportedReferences(AdmissionContext context, String placementEvidenceId) {
        Set<String> references = new LinkedHashSet<>();
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
