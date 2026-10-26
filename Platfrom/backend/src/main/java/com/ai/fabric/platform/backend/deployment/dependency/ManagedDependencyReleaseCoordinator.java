package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Drives the dependency topology selected by the exact published release. */
@Service
public class ManagedDependencyReleaseCoordinator {
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final CustomerOfferEntitlementRepository entitlements;
    private final CustomerOfferCodec codec;
    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencyGraphStore store;
    private final ManagedDependencyLifecycleService lifecycle;
    private final CustomerOfferEntitlementService ownerEligibility;
    private final CoreReleaseAuthorizationStore authorizations;
    private final PlatformTenantRepository tenants;

    public ManagedDependencyReleaseCoordinator(CustomerOfferDeploymentRepository offerDeployments,
            CustomerOfferRepository offers, CustomerOfferEntitlementRepository entitlements,
            CustomerOfferCodec codec, ManagedDependencySpecRepository specs, ManagedDependencyGraphStore store,
            ManagedDependencyLifecycleService lifecycle,
            CustomerOfferEntitlementService ownerEligibility, CoreReleaseAuthorizationStore authorizations,
            PlatformTenantRepository tenants) {
        this.offerDeployments = offerDeployments;
        this.entitlements = entitlements;
        this.codec = codec;
        this.specs = specs;
        this.store = store;
        this.lifecycle = lifecycle;
        this.ownerEligibility = ownerEligibility;
        this.authorizations = authorizations;
        this.tenants = tenants;
    }

    public void prepareFoundation(DeploymentEntity deployment, DeploymentVersionEntity version,
                                  DeploymentReleaseEntity release) {
        Context context = context(deployment, version, release);
        if (context == null) return;
        lifecycle.submitForRelease(deployment.getId(), release.getTargetProfileId(),
            new ManagedDependencyLifecycleService.Request(context.spec().getId(), version.getId(), release.getId(),
                release.getRuntimeAuthorityFingerprint(), operation(release, "submit")));
        lifecycle.provisionForRelease(deployment.getId(), release.getTargetProfileId(), release.getId(),
            operation(release, "foundation"));
    }

    public void completeRuntimeBound(DeploymentEntity deployment, DeploymentVersionEntity version,
                                     DeploymentReleaseEntity release) {
        completeRuntimeBound(deployment, version, release, "runtime-bound");
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
        ownerEligibility.requireActiveCustomer(deployment.getCustomerId());
        tenants.findById(deployment.getTenantId()).filter(value -> "ACTIVE".equals(value.getStatus())
            && deployment.getCustomerId().equals(value.getCustomerId()))
            .orElseThrow(() -> conflict("DEPENDENCY_CUSTOMER_SCOPE_CHANGED"));
        authorizations.require(deployment, version, release);
        var installs = codec.tree(version.getCompositionProvenanceJson()).path("marketplaceInstalls");
        if (!installs.isArray()) throw conflict("DEPENDENCY_TEMPLATE_EVIDENCE_REQUIRED");
        List<String> templates = new java.util.ArrayList<>();
        for (var install : installs) {
            if ("TEMPLATE".equals(install.path("pluginType").asText())) {
                String id = install.path("pluginVersionId").asText();
                if (!StringUtils.hasText(id)) throw conflict("DEPENDENCY_TEMPLATE_EVIDENCE_REQUIRED");
                templates.add(id);
            }
        }
        if (templates.size() > 1) throw conflict("DEPENDENCY_TEMPLATE_EVIDENCE_AMBIGUOUS");
        if (templates.isEmpty()) return null;
        String templateVersionId = templates.getFirst();
        var matches = specs.findByTemplateVersionIdAndActiveTrue(templateVersionId);
        if (matches.isEmpty()) return null;
        if (matches.size() != 1) throw conflict("REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        var spec = matches.getFirst();
        if (!templateVersionId.equals(spec.getTemplateVersionId())) {
            throw conflict("REVIEWED_DEPENDENCY_SPEC_CHANGED");
        }
        var topology = store.parse(spec.getSpecJson(), ManagedDependencySpec.class);
        ManagedDependencySpecValidator.requireProvisionable(topology);
        return new Context(spec);
    }

    private String operation(DeploymentReleaseEntity release, String phase) {
        return "release:" + release.getId() + ":dependency:" + phase;
    }

    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }

    private record Context(ManagedDependencySpecEntity spec) { }
}
