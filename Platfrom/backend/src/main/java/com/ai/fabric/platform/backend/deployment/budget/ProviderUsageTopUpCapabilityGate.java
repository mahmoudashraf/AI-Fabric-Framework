package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

@Component
public class ProviderUsageTopUpCapabilityGate {
    private final ProviderUsageRuntimeClient runtime;
    private final DeploymentSourceCapabilityManifestService manifests;
    private final DeploymentReleaseRepository releases;
    private final ProviderUsageRuntimeArtifactGate artifacts;

    public ProviderUsageTopUpCapabilityGate(ProviderUsageRuntimeClient runtime, DeploymentSourceCapabilityManifestService manifests,
        DeploymentReleaseRepository releases, ProviderUsageRuntimeArtifactGate artifacts) {
        this.runtime = runtime; this.manifests = manifests; this.releases = releases; this.artifacts = artifacts;
    }

    public String require(AllocationScope scope, String releaseId, ProviderUsageValidityRuntimeAccess.Access access) {
        var release = releases.findById(releaseId).orElseThrow(ProviderUsageTopUpCapabilityGate::unsupported);
        if (!scope.deploymentId().equals(release.getDeploymentId()) || !scope.targetProfileId().equals(release.getTargetProfileId())) throw unsupported();
        var artifact = artifacts.requireArtifact(release);
        var overview = runtime.overview(access.baseUrl(), access.headers());
        if (overview == null || !overview.path("success").isBoolean() || !overview.path("success").booleanValue()) throw unsupported();
        var normalized = manifests.normalize(overview.path("runtimeCapabilityManifest"));
        if (normalized.hash() == null || !normalized.hash().equals(overview.path("runtimeCapabilityManifestHash").asText())
            || !normalized.hash().equals(artifact.getCapabilityManifestHash())
            || !artifact.getGitCommitSha().equals(overview.path("productSourceCommit").asText())
            || !contains(normalized.manifest().path("capabilities"), "deployment-local-provider-budget-top-up-v1")
            || !contains(normalized.manifest().path("endpointClasses"), "provider-budget-administration")
            || !contains(normalized.manifest().path("migrationIds"), "provider-budget-top-ups-v1")
            || !overview.path("runtimeMigrations").path("available").isBoolean()
            || !overview.path("runtimeMigrations").path("available").booleanValue()
            || !contains(overview.path("runtimeMigrations").path("appliedMigrationIds"), "provider-budget-top-ups-v1")) throw unsupported();
        return normalized.hash();
    }

    private static boolean contains(JsonNode values, String expected) {
        if (!values.isArray()) return false;
        for (var value : values) if (value.isTextual() && expected.equals(value.textValue())) return true;
        return false;
    }
    private static ProviderUsageAllowanceFailure unsupported() { return conflict("PROVIDER_USAGE_TOP_UP_CAPABILITY_REQUIRED"); }
}
