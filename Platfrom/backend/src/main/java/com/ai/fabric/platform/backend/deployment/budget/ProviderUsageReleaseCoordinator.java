package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.*;

/** Provisioning-time coordination only; customer inference traffic never passes through this service. */
@Service
public class ProviderUsageReleaseCoordinator {
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.ai.fabric.platform.backend.assistant.ManagedAssistantRuntime> managedAssistant;
    private final CustomerOfferDeploymentRepository offers;
    private final ProviderUsageAllowanceService allowances;
    private final ProviderUsageRuntimeClient runtime;
    private final DeploymentRuntimeAuthorityService authority;
    private final ProviderUsageRuntimeArtifactGate artifactGate;
    private final ProviderUsageModeResolver modes;

    @Autowired
    public ProviderUsageReleaseCoordinator(CustomerOfferDeploymentRepository offers, ProviderUsageAllowanceService allowances,
        ProviderUsageRuntimeClient runtime, DeploymentRuntimeAuthorityService authority, ProviderUsageRuntimeArtifactGate artifactGate,
        ProviderUsageModeResolver modes) {
        this.offers = offers; this.allowances = allowances; this.runtime = runtime; this.authority = authority;
        this.artifactGate = artifactGate; this.modes = modes;
    }

    public void prepare(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (existingAssistant(deployment) != null) { existingAssistant(deployment).requireInstalled(deployment, release); return; }
        if (funded(deployment, release)) {
            artifactGate.require(release);
            supported(allowances.prepareForAuthorizedRelease(release.getId()));
        }
        if (allowances.rerankingEnabledForRelease(release.getId())) {
            artifactGate.requireReranking(release);
            supported(allowances.prepareForAuthorizedRelease(release.getId(), ProviderUsagePurpose.RERANKING), ProviderUsagePurpose.RERANKING);
        }
    }

    /** Called last, after all customer-controlled/default environment merges. No environment-provided credits. */
    public Map<String, String> environment(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (existingAssistant(deployment) != null) { existingAssistant(deployment).requireInstalled(deployment, release); return Map.of("LOOMAI_PROVIDER_BUDGET_ENABLED", "true"); }
        var pinned = modes.forRelease(deployment, release);
        var environment = new java.util.LinkedHashMap<String, String>();
        if (pinned.map(ProviderUsageModeResolver.Mode.FUNDED.name()::equals).orElseGet(() -> offers.existsById(deployment.getId()))) {
            artifactGate.require(release);
            var allocation = required(release);
            environment.putAll(Map.of("LOOMAI_PROVIDER_BUDGET_ENABLED", "true",
                "LOOMAI_PROVIDER_BUDGET_ENVIRONMENT", allocation.scope().environment(),
                "LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY", "sha256:" + allocation.credentialSha256(),
                "LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE", allocation.pricingReference(),
                "LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE", "MAX_COMPLETION_TOKENS"));
        } else if (pinned.isPresent()) environment.put("LOOMAI_PROVIDER_BUDGET_ENABLED", "false");
        if (allowances.rerankingEnabledForRelease(release.getId())) {
            artifactGate.requireReranking(release);
            var allocation = required(release, ProviderUsagePurpose.RERANKING);
            environment.put("LOOMAI_RERANKING_BUDGET_ENVIRONMENT", allocation.scope().environment());
            environment.put("LOOMAI_RERANKING_BUDGET_CREDENTIAL_IDENTITY", "sha256:" + allocation.credentialSha256());
        }
        return Map.copyOf(environment);
    }

    public void initializeAndActivate(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (existingAssistant(deployment) != null) { existingAssistant(deployment).verifyRuntime(deployment, version, release); return; }
        if (!version.getId().equals(release.getDeploymentVersionId()) || !deployment.getId().equals(version.getDeploymentId())) fail();
        if (funded(deployment, release)) initializeAccount(deployment, version, release, ProviderUsagePurpose.GENERATION);
        if (allowances.rerankingEnabledForRelease(release.getId())) initializeAccount(deployment, version, release, ProviderUsagePurpose.RERANKING);
    }

    private void initializeAccount(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release,
                                   ProviderUsagePurpose purpose) {
        requireArtifact(release, purpose);
        var allocation = required(release, purpose);
        var headers = authority.issueProviderBudgetProvisioningHeaders(deployment, version, release);
        Readback observed = read(deployment.getRuntimeBaseUrl(), headers, purpose);
        verifyScope(observed, allocation);
        if (allocation.state() == InitializationState.UNINITIALIZED) {
            if (!"UNINITIALIZED".equals(observed.state()) && !"INITIALIZING".equals(observed.state())
                && !"INITIALIZED".equals(observed.state())) fail();
            if (!"UNINITIALIZED".equals(observed.state())) verifyReceipt(observed.receipt(), allocation, null);
            StoreReceipt initialized = purpose == ProviderUsagePurpose.GENERATION
                ? runtime.initialize(deployment.getRuntimeBaseUrl(), allocation, headers)
                : runtime.initialize(deployment.getRuntimeBaseUrl(), allocation, headers, purpose);
            if (!"INITIALIZED".equals(initialized.state())) fail();
            verifyReceipt(initialized, allocation, null);
            // This independent control-plane commit MUST precede the activation call.
            allocation = purpose == ProviderUsagePurpose.GENERATION
                ? allowances.markInitializedForAuthorizedRelease(release.getId(), initialized.storeUuid(), initialized.runtimeFingerprint())
                : allowances.markInitializedForAuthorizedRelease(release.getId(), initialized.storeUuid(), initialized.runtimeFingerprint(), purpose);
        } else {
            verifyReceipt(observed.receipt(), allocation, allocation.storeUuid());
            if (allocation.state() == InitializationState.ACTIVE && !"ACTIVE".equals(observed.state())) fail();
            if (allocation.state() == InitializationState.INITIALIZED
                && !"INITIALIZED".equals(observed.state()) && !"ACTIVE".equals(observed.state())) fail();
        }
        StoreReceipt receipt = new StoreReceipt("INITIALIZED", allocation.storeUuid(), allocation.approvalId(),
            allocation.runtimeAllocationFingerprint());
        StoreReceipt active = purpose == ProviderUsagePurpose.GENERATION
            ? runtime.activate(deployment.getRuntimeBaseUrl(), receipt, headers)
            : runtime.activate(deployment.getRuntimeBaseUrl(), receipt, headers, purpose);
        if (!"ACTIVE".equals(active.state())) fail();
        verifyReceipt(active, allocation, allocation.storeUuid());
        if (purpose == ProviderUsagePurpose.GENERATION) allowances.markActiveForAuthorizedRelease(release.getId(), active.storeUuid(), active.runtimeFingerprint());
        else allowances.markActiveForAuthorizedRelease(release.getId(), active.storeUuid(), active.runtimeFingerprint(), purpose);
        observed = read(deployment.getRuntimeBaseUrl(), headers, purpose);
        verifyScope(observed, allocation); verifyReceipt(observed.receipt(), allocation, allocation.storeUuid());
        if (!"ACTIVE".equals(observed.state()) || !Boolean.FALSE.equals(observed.stopped())
            || !allocation.caps().equals(observed.limits()) || !allocation.expiresAt().equals(observed.expiresAt())
            || !allocation.effectiveExpiresAt().equals(effectiveExpiry(observed))) fail();
    }

    /** Continuation may inspect the existing wallet, but must never initialize, activate or top it up. */
    public void verifyActive(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        exactRelease(deployment, release);
        if (existingAssistant(deployment) != null) { existingAssistant(deployment).verifyRuntime(deployment, version, release); return; }
        if (!version.getId().equals(release.getDeploymentVersionId())
            || !deployment.getId().equals(version.getDeploymentId())) fail();
        if (funded(deployment, release)) verifyAccount(deployment, release, ProviderUsagePurpose.GENERATION);
        if (allowances.rerankingEnabledForRelease(release.getId())) verifyAccount(deployment, release, ProviderUsagePurpose.RERANKING);
    }

    private void verifyAccount(DeploymentEntity deployment, DeploymentReleaseEntity release, ProviderUsagePurpose purpose) {
        requireArtifact(release, purpose);
        var allocation = required(release, purpose);
        if (allocation.state() != InitializationState.ACTIVE || allocation.storeUuid() == null
            || !allocation.effectiveExpiresAt().isAfter(java.time.Instant.now())) fail();
        var observed = read(deployment.getRuntimeBaseUrl(), authority.issueReleaseVerificationHeaders(deployment, release, false), purpose);
        verifyScope(observed, allocation); verifyReceipt(observed.receipt(), allocation, allocation.storeUuid());
        if (!"ACTIVE".equals(observed.state()) || !Boolean.FALSE.equals(observed.stopped())
            || !allocation.caps().equals(observed.limits()) || !allocation.expiresAt().equals(observed.expiresAt())
            || !allocation.effectiveExpiresAt().equals(effectiveExpiry(observed))) fail();
    }

    private static java.time.Instant effectiveExpiry(Readback readback) {
        // Old images can attest only their original validity; an extended CP projection cannot match this fallback.
        return readback.effectiveExpiresAt() == null ? readback.expiresAt() : readback.effectiveExpiresAt();
    }

    private SafeAllowanceProjection required(DeploymentReleaseEntity release) {
        return required(release, ProviderUsagePurpose.GENERATION);
    }

    private SafeAllowanceProjection required(DeploymentReleaseEntity release, ProviderUsagePurpose purpose) {
        var allocation = purpose == ProviderUsagePurpose.GENERATION ? allowances.findForAuthorizedRelease(release.getId())
            : allowances.findForAuthorizedRelease(release.getId(), purpose);
        return supported(allocation.orElseThrow(() -> new IllegalStateException("PROVIDER_USAGE_PREPARED_ALLOCATION_REQUIRED")), purpose);
    }

    private Readback read(String baseUrl, Map<String, String> headers, ProviderUsagePurpose purpose) {
        return purpose == ProviderUsagePurpose.GENERATION ? runtime.read(baseUrl, headers) : runtime.read(baseUrl, headers, purpose);
    }

    private void requireArtifact(DeploymentReleaseEntity release, ProviderUsagePurpose purpose) {
        if (purpose == ProviderUsagePurpose.GENERATION) artifactGate.require(release);
        else artifactGate.requireReranking(release);
    }

    private boolean funded(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        return modes.forRelease(deployment, release).map(ProviderUsageModeResolver.Mode.FUNDED.name()::equals)
            .orElseGet(() -> offers.existsById(deployment.getId()));
    }

    private static SafeAllowanceProjection supported(SafeAllowanceProjection allowance) {
        return supported(allowance, ProviderUsagePurpose.GENERATION);
    }

    private static SafeAllowanceProjection supported(SafeAllowanceProjection allowance, ProviderUsagePurpose purpose) {
        // Exact capability authorization, not a provider/model choice or a fallback.
        if (allowance == null) {
            throw new IllegalStateException("PROVIDER_USAGE_RUNTIME_PROFILE_NOT_REVIEWED");
        }
        if (purpose == ProviderUsagePurpose.GENERATION) ProviderUsageRuntimeProfile.require(allowance.binding(), allowance.pricingReference());
        else ProviderUsageRuntimeProfile.requireReranking(allowance.binding(), allowance.pricingReference());
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
    private com.ai.fabric.platform.backend.assistant.ManagedAssistantRuntime existingAssistant(DeploymentEntity deployment) {
        var assistant = managedAssistant == null ? null : managedAssistant.getIfAvailable();
        return assistant != null && assistant.matches(deployment) ? assistant : null;
    }
}
