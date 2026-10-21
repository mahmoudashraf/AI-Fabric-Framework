package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Drives the reviewed dependency topology as part of the exact customer-offer release. */
@Service
public class ManagedDependencyReleaseCoordinator {
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final CustomerOfferRepository offers;
    private final CustomerOfferEntitlementRepository entitlements;
    private final CustomerOfferCodec codec;
    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencyGraphStore store;
    private final ManagedDependencyLifecycleService lifecycle;
    private final ManagedDependencyOperatorService operators;

    public ManagedDependencyReleaseCoordinator(CustomerOfferDeploymentRepository offerDeployments,
            CustomerOfferRepository offers, CustomerOfferEntitlementRepository entitlements,
            CustomerOfferCodec codec, ManagedDependencySpecRepository specs, ManagedDependencyGraphStore store,
            ManagedDependencyLifecycleService lifecycle, ManagedDependencyOperatorService operators) {
        this.offerDeployments = offerDeployments;
        this.offers = offers;
        this.entitlements = entitlements;
        this.codec = codec;
        this.specs = specs;
        this.store = store;
        this.lifecycle = lifecycle;
        this.operators = operators;
    }

    public void prepareFoundation(DeploymentEntity deployment, DeploymentVersionEntity version,
                                  DeploymentReleaseEntity release) {
        Context context = context(deployment, version, release);
        if (context == null) return;
        lifecycle.submitForRelease(deployment.getId(), release.getTargetProfileId(),
            new ManagedDependencyLifecycleService.Request(context.spec().getId(), version.getId(), release.getId(),
                release.getRuntimeAuthorityFingerprint(), operation(release, "submit")));
        List<ManagedDependencySpec.Service> inboxes = context.topology().services().stream()
            .filter(service -> service.contract() == ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1)
            .toList();
        if (inboxes.size() > 1) throw conflict("DEPENDENCY_INITIAL_OPERATOR_AMBIGUOUS");
        if (inboxes.size() == 1) {
            operators.enrollInitialOwnerForRelease(deployment.getId(), release.getTargetProfileId(), release.getId(),
                new ManagedDependencyOperatorService.Enroll(inboxes.getFirst().slot(), context.ownerUserId(),
                    operation(release, "initial-owner")));
        }
        lifecycle.provisionForRelease(deployment.getId(), release.getTargetProfileId(), release.getId(),
            operation(release, "foundation"));
    }

    public void completeRuntimeBound(DeploymentEntity deployment, DeploymentVersionEntity version,
                                     DeploymentReleaseEntity release) {
        completeRuntimeBound(deployment, version, release, "runtime-bound");
    }

    public void resumeRuntimeBound(DeploymentEntity deployment, DeploymentVersionEntity version,
                                   DeploymentReleaseEntity release, String proofFingerprint) {
        if (proofFingerprint == null || !proofFingerprint.matches("[a-f0-9]{64}")) throw conflict("DEPENDENCY_CONTINUATION_PROOF_REQUIRED");
        Context context = context(deployment, version, release);
        if (context == null) return;
        lifecycle.resumeForRelease(deployment.getId(), release.getTargetProfileId(), release.getId(),
            operation(release, "resume:" + proofFingerprint));
    }

    private void completeRuntimeBound(DeploymentEntity deployment, DeploymentVersionEntity version,
                                      DeploymentReleaseEntity release, String operation) {
        Context context = context(deployment, version, release);
        if (context == null) return;
        lifecycle.reconcileForRelease(deployment.getId(), release.getTargetProfileId(), release.getId(),
            operation(release, operation));
    }

    private Context context(DeploymentEntity deployment, DeploymentVersionEntity version,
                            DeploymentReleaseEntity release) {
        var association = offerDeployments.findById(deployment.getId()).orElse(null);
        if (association == null) return null;
        if (!deployment.getId().equals(version.getDeploymentId())
            || !deployment.getId().equals(release.getDeploymentId())
            || !version.getId().equals(release.getDeploymentVersionId())
            || !association.getCustomerId().equals(deployment.getCustomerId())
            || !association.getTenantId().equals(deployment.getTenantId())) {
            throw conflict("DEPENDENCY_RELEASE_SCOPE_CHANGED");
        }
        if (!StringUtils.hasText(release.getTargetProfileId())
            || !StringUtils.hasText(release.getRuntimeAuthorityFingerprint())
            || !release.getRuntimeAuthorityFingerprint().matches("[a-f0-9]{64}")) {
            throw conflict("DEPENDENCY_RELEASE_BINDING_REQUIRED");
        }
        var offer = offers.findById(association.getOfferId())
            .orElseThrow(() -> conflict("DEPENDENCY_OFFER_MISSING"));
        if (!association.getOfferFingerprint().equals(offer.getFingerprint())) {
            throw conflict("DEPENDENCY_OFFER_CHANGED");
        }
        var entitlement = entitlements.findByCustomerIdAndOfferId(association.getCustomerId(), association.getOfferId())
            .filter(value -> value.getStatus()
                == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus.ACTIVE)
            .filter(value -> value.getExpiresAt() != null && value.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> conflict("DEPENDENCY_ENTITLEMENT_INACTIVE"));
        if (entitlement.getRevision() != association.getAuthorizationEntitlementRevision()) {
            throw conflict("DEPENDENCY_ENTITLEMENT_CHANGED");
        }
        Definition definition = codec.read(offer.getDefinitionJson(), Definition.class);
        String templateVersionId = definition.template().versionId();
        var matches = specs.findByTemplateVersionIdAndActiveTrue(templateVersionId);
        if (definition.limits().dependencies() == 0) {
            if (!matches.isEmpty()) throw conflict("UNEXPECTED_REVIEWED_DEPENDENCY_SPEC");
            return null;
        }
        if (matches.size() != 1) throw conflict("REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        var spec = matches.getFirst();
        if (!templateVersionId.equals(spec.getTemplateVersionId())) {
            throw conflict("REVIEWED_DEPENDENCY_SPEC_CHANGED");
        }
        return new Context(spec, store.parse(spec.getSpecJson(), ManagedDependencySpec.class),
            entitlement.getOwnerUserId());
    }

    private String operation(DeploymentReleaseEntity release, String phase) {
        return "release:" + release.getId() + ":dependency:" + phase;
    }

    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }

    private record Context(ManagedDependencySpecEntity spec, ManagedDependencySpec topology,
                           String ownerUserId) { }
}
