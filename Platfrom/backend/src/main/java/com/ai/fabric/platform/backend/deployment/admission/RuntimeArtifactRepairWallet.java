package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Readback;
import java.time.Instant;
import java.util.Objects;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Expiry is evidence, not permission to reinitialize an existing wallet. */
public final class RuntimeArtifactRepairWallet {
    private RuntimeArtifactRepairWallet() { }
    public static Instant effectiveExpiry(Readback value) {
        return value.effectiveExpiresAt() == null ? value.expiresAt() : value.effectiveExpiresAt();
    }
    public static void requireUnchanged(Readback before, Readback after) {
        if (before == null || after == null || !"ACTIVE".equals(before.state()) || before.storeUuid() == null
            || before.approvalId() == null || before.runtimeFingerprint() == null || before.expiresAt() == null
            || before.limits() == null || before.accountedUsage() == null || before.chatAttempts() == null
            || before.providerAttempts() == null || before.stopped() == null
            || !Objects.equals(canonical(before), canonical(after)))
            throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_WALLET_CHANGED");
    }
    private static Readback canonical(Readback value) {
        return new Readback(value.state(), value.storeUuid(), value.approvalId(), value.runtimeFingerprint(),
            value.customerId(), value.deploymentId(), value.targetProfileId(), value.environment(), value.rateCardReference(),
            value.limits(), value.expiresAt(), value.chatAttempts(), value.providerAttempts(), value.accountedUsage(),
            value.stopped(), effectiveExpiry(value));
    }
}
