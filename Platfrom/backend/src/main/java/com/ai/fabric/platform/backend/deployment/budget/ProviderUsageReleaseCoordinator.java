package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.*;

/** Provisioning-time coordination only; customer inference traffic never passes through this service. */
@Service
public class ProviderUsageReleaseCoordinator {
    private static final String REVIEWED_RATE_CARD = "openai-gpt4omini-embedding3small-20261018-v1";
    private static final java.util.Set<String> REVIEWED_MODELS = java.util.Set.of("gpt-4o-mini", "text-embedding-3-small");
    private final CustomerOfferDeploymentRepository offers;
    private final ProviderUsageAllowanceService allowances;
    private final ProviderUsageRuntimeClient runtime;
    private final DeploymentRuntimeAuthorityService authority;
    private final ProviderUsageRuntimeArtifactGate artifactGate;

    public ProviderUsageReleaseCoordinator(CustomerOfferDeploymentRepository offers, ProviderUsageAllowanceService allowances,
        ProviderUsageRuntimeClient runtime, DeploymentRuntimeAuthorityService authority, ProviderUsageRuntimeArtifactGate artifactGate) {
        this.offers = offers; this.allowances = allowances; this.runtime = runtime; this.authority = authority; this.artifactGate = artifactGate;
    }

    public void prepare(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (offers.existsById(deployment.getId())) {
            artifactGate.require(release);
            supported(allowances.prepareForAuthorizedRelease(release.getId()));
        }
    }

    /** Called last, after all customer-controlled/default environment merges. No environment-provided credits. */
    public Map<String, String> environment(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (!offers.existsById(deployment.getId())) return Map.of();
        artifactGate.require(release);
        var allocation = required(release);
        return Map.of("LOOMAI_PROVIDER_BUDGET_ENABLED", "true",
            "LOOMAI_PROVIDER_BUDGET_ENVIRONMENT", allocation.scope().environment(),
            "LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY", "sha256:" + allocation.credentialSha256(),
            "LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE", allocation.pricingReference(),
            "LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE", "MAX_COMPLETION_TOKENS");
    }

    public void initializeAndActivate(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (!offers.existsById(deployment.getId())) return;
        artifactGate.require(release);
        if (!version.getId().equals(release.getDeploymentVersionId()) || !deployment.getId().equals(version.getDeploymentId())) fail();
        var allocation = required(release);
        var headers = authority.issueProviderBudgetProvisioningHeaders(deployment, version, release);
        Readback observed = runtime.read(deployment.getRuntimeBaseUrl(), headers);
        verifyScope(observed, allocation);
        if (allocation.state() == InitializationState.UNINITIALIZED) {
            if (!"UNINITIALIZED".equals(observed.state()) && !"INITIALIZING".equals(observed.state())
                && !"INITIALIZED".equals(observed.state())) fail();
            if (!"UNINITIALIZED".equals(observed.state())) verifyReceipt(observed.receipt(), allocation, null);
            StoreReceipt initialized = runtime.initialize(deployment.getRuntimeBaseUrl(), allocation, headers);
            if (!"INITIALIZED".equals(initialized.state())) fail();
            verifyReceipt(initialized, allocation, null);
            // This independent control-plane commit MUST precede the activation call.
            allocation = allowances.markInitializedForAuthorizedRelease(release.getId(), initialized.storeUuid(), initialized.runtimeFingerprint());
        } else {
            verifyReceipt(observed.receipt(), allocation, allocation.storeUuid());
            if (allocation.state() == InitializationState.ACTIVE && !"ACTIVE".equals(observed.state())) fail();
            if (allocation.state() == InitializationState.INITIALIZED
                && !"INITIALIZED".equals(observed.state()) && !"ACTIVE".equals(observed.state())) fail();
        }
        StoreReceipt receipt = new StoreReceipt("INITIALIZED", allocation.storeUuid(), allocation.approvalId(),
            allocation.runtimeAllocationFingerprint());
        StoreReceipt active = runtime.activate(deployment.getRuntimeBaseUrl(), receipt, headers);
        if (!"ACTIVE".equals(active.state())) fail();
        verifyReceipt(active, allocation, allocation.storeUuid());
        allowances.markActiveForAuthorizedRelease(release.getId(), active.storeUuid(), active.runtimeFingerprint());
        observed = runtime.read(deployment.getRuntimeBaseUrl(), headers);
        verifyScope(observed, allocation); verifyReceipt(observed.receipt(), allocation, allocation.storeUuid());
        if (!"ACTIVE".equals(observed.state()) || !Boolean.FALSE.equals(observed.stopped())
            || !allocation.caps().equals(observed.limits()) || !allocation.expiresAt().equals(observed.expiresAt())) fail();
    }

    private SafeAllowanceProjection required(DeploymentReleaseEntity release) {
        return supported(allowances.findForAuthorizedRelease(release.getId())
            .orElseThrow(() -> new IllegalStateException("PROVIDER_USAGE_PREPARED_ALLOCATION_REQUIRED")));
    }

    private static SafeAllowanceProjection supported(SafeAllowanceProjection allowance) {
        // Exact capability authorization, not a provider/model choice or a fallback.
        if (allowance == null || !"openai".equals(allowance.binding().provider())
            || !REVIEWED_MODELS.equals(java.util.Set.copyOf(allowance.binding().modelIds()))
            || !REVIEWED_RATE_CARD.equals(allowance.pricingReference())) {
            throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        }
        return allowance;
    }

    private static void verifyScope(Readback readback, SafeAllowanceProjection allocation) {
        var scope = allocation.scope();
        if (readback == null || !scope.customerId().equals(readback.customerId())
            || !scope.deploymentId().equals(readback.deploymentId()) || !scope.targetProfileId().equals(readback.targetProfileId())
            || !scope.environment().equals(readback.environment()) || !allocation.pricingReference().equals(readback.rateCardReference())) fail();
    }

    private static void verifyReceipt(StoreReceipt receipt, SafeAllowanceProjection allocation, java.util.UUID expectedStore) {
        if (receipt == null || receipt.storeUuid() == null || !allocation.approvalId().equals(receipt.approvalId())
            || !allocation.runtimeAllocationFingerprint().equals(receipt.runtimeFingerprint())
            || (expectedStore != null && !expectedStore.equals(receipt.storeUuid()))) fail();
    }

    private static void exactRelease(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        if (deployment == null || release == null || !Objects.equals(deployment.getId(), release.getDeploymentId())) fail();
    }

    private static void fail() { throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_IDENTITY_OR_STATE_CHANGED"); }
}
