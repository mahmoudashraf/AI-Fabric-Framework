package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

/** Packaged metadata from the existing signed overview; never infers support from an enable flag. */
@Component
public class ProviderUsageValidityCapabilityGate {
    private final ProviderUsageRuntimeClient runtime;
    private final DeploymentSourceCapabilityManifestService manifests;

    public ProviderUsageValidityCapabilityGate(ProviderUsageRuntimeClient runtime, DeploymentSourceCapabilityManifestService manifests) {
        this.runtime = runtime; this.manifests = manifests;
    }

    // Exact release/scope are supplied so the effective-artifact resolver can add its immutable-source check here.
    public String require(AllocationScope scope, String releaseId, ProviderUsageValidityRuntimeAccess.Access access) {
        var overview = runtime.overview(access.baseUrl(), access.headers());
        if (overview == null || !overview.path("success").isBoolean() || !overview.path("success").booleanValue()) throw unsupported();
        var normalized = manifests.normalize(overview.path("runtimeCapabilityManifest"));
        if (normalized.hash() == null || !normalized.hash().equals(overview.path("runtimeCapabilityManifestHash").asText())
            || !contains(normalized.manifest().path("capabilities"), "deployment-local-provider-budget-validity-extension-v1")
            || !contains(normalized.manifest().path("endpointClasses"), "provider-budget-administration")
            || !contains(normalized.manifest().path("migrationIds"), "provider-budget-validity-extensions-v1")
            || !contains(normalized.manifest().path("verificationPackIds"), "provider-budget-validity-extension-v1")) throw unsupported();
        return normalized.hash();
    }

    private static boolean contains(JsonNode values, String expected) {
        if (!values.isArray()) return false;
        for (var value : values) if (value.isTextual() && expected.equals(value.textValue())) return true;
        return false;
    }
    private static ProviderUsageAllowanceFailure unsupported() { return conflict("PROVIDER_USAGE_VALIDITY_CAPABILITY_REQUIRED"); }
}
