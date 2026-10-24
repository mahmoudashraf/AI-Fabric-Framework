package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Core identity/spec authority, deliberately independent of admission leases and capacity policy. */
@Component
class ManagedDependencyCoreReleaseAuthorization {
    record Proof(String releaseId, String versionConfigHash, boolean production) { }

    private final CoreReleaseAuthorizationStore authorizations;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentReleaseRepository releases;
    private final DeploymentTargetProfileRepository targets;
    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencySpecValidator validator;
    private final ManagedDependencyCustomerOfferGuard offerGuard;
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerOfferRepository offers;
    private final CustomerOfferCodec codec;
    private final CustomerReviewedCompositionService composition;
    private final CustomerTargetPlacementService placement;

    ManagedDependencyCoreReleaseAuthorization(CoreReleaseAuthorizationStore authorizations,
            DeploymentRepository deployments, DeploymentVersionRepository versions,
            DeploymentReleaseRepository releases, DeploymentTargetProfileRepository targets,
            ManagedDependencySpecRepository specs, ManagedDependencySpecValidator validator,
            ManagedDependencyCustomerOfferGuard offerGuard, CustomerOfferDeploymentRepository associations,
            CustomerOfferRepository offers, CustomerOfferCodec codec,
            CustomerReviewedCompositionService composition, CustomerTargetPlacementService placement) {
        this.authorizations = authorizations; this.deployments = deployments; this.versions = versions;
        this.releases = releases; this.targets = targets; this.specs = specs; this.validator = validator;
        this.offerGuard = offerGuard; this.associations = associations; this.offers = offers;
        this.codec = codec; this.composition = composition; this.placement = placement;
    }

    Optional<Proof> find(ManagedDependencyPrerequisites.Scope scope, ManagedDependencyGraphEntity graph,
                         ManagedDependencySpec spec) {
        var recorded = authorizations.find(scope.releaseId());
        if (recorded.isEmpty()) {
            if (graph.getReservationRef() == null || graph.getReservationRef().startsWith("core-release:"))
                fail("DEPENDENCY_CORE_RELEASE_AUTHORIZATION_REQUIRED");
            return Optional.empty();
        }
        var deployment = deployments.findById(scope.deploymentId())
            .orElseThrow(() -> conflict("DEPENDENCY_DEPLOYMENT_MISSING"));
        var version = versions.findById(scope.versionId())
            .orElseThrow(() -> conflict("DEPENDENCY_CANDIDATE_MISSING"));
        var release = releases.findById(scope.releaseId())
            .orElseThrow(() -> conflict("DEPENDENCY_RELEASE_BINDING_MISSING"));
        var authorization = authorizations.require(deployment, version, release);
        if (!recorded.get().equals(authorization)
            || !Objects.equals(scope.customerId(), deployment.getCustomerId())
            || !Objects.equals(scope.tenantId(), deployment.getTenantId()) || deployment.getArchivedAt() != null
            || !"PUBLISHED".equals(version.getStatus()) || version.getConfigHash() == null
            || version.getConfigHash().isBlank()) fail("DEPENDENCY_CORE_RELEASE_AUTHORIZATION_CHANGED");

        var reviewed = specs.findById(graph.getSpecRef())
            .orElseThrow(() -> conflict("REVIEWED_DEPENDENCY_SPEC_MISSING"));
        validator.validate(spec);
        if (!reviewed.isActive() || reviewed.getReviewedAt() == null || reviewed.getReviewedBy() == null
            || reviewed.getReviewedBy().isBlank() || reviewed.getTemplateVersionId() == null
            || reviewed.getReviewReference() == null || reviewed.getReviewReference().isBlank()
            || !scope.specFingerprint().equals(reviewed.getFingerprint())
            || !scope.specFingerprint().equals(validator.fingerprint(spec))
            || !scope.specFingerprint().equals(validator.fingerprint(codec.read(reviewed.getSpecJson(), ManagedDependencySpec.class))))
            fail("DEPENDENCY_SPEC_REVIEW_REVOKED");

        var target = targets.findById(scope.targetProfileId())
            .filter(value -> value.isActive() && value.getProviderType() == DeploymentProviderType.COOLIFY)
            .orElseThrow(() -> conflict("DEPENDENCY_TARGET_INACTIVE"));
        var association = associations.findById(scope.deploymentId()).orElse(null);
        TargetIntent intent;
        if (association != null) {
            offerGuard.requireActiveExecution(graph);
            var offer = offers.findById(association.getOfferId())
                .orElseThrow(() -> conflict("DEPENDENCY_OFFER_MISSING"));
            var definition = codec.read(offer.getDefinitionJson(),
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition.class);
            if (!reviewed.getTemplateVersionId().equals(definition.template().versionId()))
                fail("DEPENDENCY_SPEC_TEMPLATE_CHANGED");
            composition.requireVersion(definition, version);
            var permissions = definition.targets().stream()
                .filter(value -> scope.targetProfileId().equals(value.targetProfileId())).toList();
            if (permissions.size() != 1) fail("DEPENDENCY_TARGET_NOT_REVIEWED");
            var permission = permissions.getFirst();
            composition.requireProfile(target, permission);
            placement.requireConfiguredPlacement(target, permission.intent());
            intent = permission.intent();
        } else {
            var installs = codec.tree(version.getCompositionProvenanceJson()).path("marketplaceInstalls");
            boolean reviewedTemplate = false;
            for (var install : installs) {
                if (reviewed.getTemplateVersionId().equals(install.path("pluginVersionId").asText())) reviewedTemplate = true;
            }
            if (!reviewedTemplate) fail("DEPENDENCY_SPEC_TEMPLATE_CHANGED");
            if ("production".equals(target.getEnvironmentName())) intent = TargetIntent.CUSTOMER_PRODUCTION;
            else if ("staging".equals(target.getEnvironmentName())) intent = TargetIntent.CUSTOMER_STAGING;
            else throw conflict("DEPENDENCY_TARGET_ENVIRONMENT_UNSUPPORTED");
        }
        return Optional.of(new Proof(authorization.releaseId(), authorization.versionConfigHash(),
            intent == TargetIntent.CUSTOMER_PRODUCTION));
    }

    private ResponseStatusException conflict(String code) { return new ResponseStatusException(CONFLICT, code); }
}
