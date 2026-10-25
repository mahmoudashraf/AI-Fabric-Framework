package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Resolves only reviewed, server-owned dependency bindings for release planning. */
@Service
public class ManagedDependencyRuntimeEnvironmentResolver {
    private final CustomerOfferDeploymentRepository offers;
    private final ManagedDependencyLifecycleService lifecycle;
    private final DeploymentVersionRepository versions;
    private final ManagedDependencySpecRepository specs;
    private final CustomerOfferCodec codec;

    public ManagedDependencyRuntimeEnvironmentResolver(CustomerOfferDeploymentRepository offers,
            ManagedDependencyLifecycleService lifecycle, DeploymentVersionRepository versions,
            ManagedDependencySpecRepository specs, CustomerOfferCodec codec) {
        this.offers = offers;
        this.lifecycle = lifecycle;
        this.versions = versions;
        this.specs = specs;
        this.codec = codec;
    }

    public ManagedDependencyLifecycleService.RuntimeEnvironmentProjection resolve(String deploymentId,
            String targetProfileId, String versionId, String bindingFingerprint) {
        if (!offers.existsById(deploymentId)) {
            return new ManagedDependencyLifecycleService.RuntimeEnvironmentProjection(null, null);
        }
        var version = versions.findById(versionId).filter(value -> deploymentId.equals(value.getDeploymentId())
            && "PUBLISHED".equals(value.getStatus())).orElseThrow(() ->
                new ResponseStatusException(CONFLICT, "DEPENDENCY_CANDIDATE_MISMATCH"));
        var installs = codec.tree(version.getCompositionProvenanceJson()).path("marketplaceInstalls");
        if (!installs.isArray()) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TEMPLATE_EVIDENCE_REQUIRED");
        var templates = new java.util.ArrayList<String>();
        for (var install : installs) if ("TEMPLATE".equals(install.path("pluginType").asText())) {
            String template = install.path("pluginVersionId").asText();
            if (template.isBlank()) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TEMPLATE_EVIDENCE_REQUIRED");
            templates.add(template);
        }
        if (templates.size() > 1) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TEMPLATE_EVIDENCE_AMBIGUOUS");
        var selected = templates.isEmpty() ? java.util.List.<ManagedDependencySpecEntity>of()
            : specs.findByTemplateVersionIdAndActiveTrue(templates.getFirst());
        if (selected.size() > 1) throw new ResponseStatusException(CONFLICT, "REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        if (selected.isEmpty()) {
            return new ManagedDependencyLifecycleService.RuntimeEnvironmentProjection(null, null);
        }
        if (bindingFingerprint == null || !bindingFingerprint.matches("[a-f0-9]{64}")) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_RELEASE_BINDING_REQUIRED");
        }
        return lifecycle.runtimeEnvironment(deploymentId, targetProfileId, versionId, bindingFingerprint);
    }
}
