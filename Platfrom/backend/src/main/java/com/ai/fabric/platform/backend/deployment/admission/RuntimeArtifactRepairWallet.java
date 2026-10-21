package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Readback;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Usage;
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
    public record ResumedUsage(String policy, Readback before, Readback after,
                              long additionalProviderAttempts, Usage additionalUsage) { }

    /** Resuming an unactivated conversational deployment may resume paid indexing, never customer chat. */
    public static ResumedUsage requireResumedNonChatUsage(Readback before, Readback after) {
        if (before == null || after == null || after.chatAttempts() == null || after.providerAttempts() == null
            || after.accountedUsage() == null) changed();
        requireUnchanged(before, new Readback(after.state(), after.storeUuid(), after.approvalId(), after.runtimeFingerprint(),
            after.customerId(), after.deploymentId(), after.targetProfileId(), after.environment(), after.rateCardReference(),
            after.limits(), after.expiresAt(), before.chatAttempts(), before.providerAttempts(), before.accountedUsage(),
            after.stopped(), after.effectiveExpiresAt()));
        var usage = after.accountedUsage(); var prior = before.accountedUsage();
        if (!after.chatAttempts().equals(before.chatAttempts()) || after.providerAttempts() < before.providerAttempts()
            || usage.inputTokens() < prior.inputTokens() || usage.outputTokens() != prior.outputTokens()
            || usage.costNanoUsd() < prior.costNanoUsd() || usage.inputTokens() < 0 || usage.outputTokens() < 0
            || usage.costNanoUsd() < 0 || after.providerAttempts() < 0
            || after.chatAttempts() > after.limits().chatAttempts()
            || usage.outputTokens() > after.limits().totalTokens()
            || usage.inputTokens() > after.limits().totalTokens() - usage.outputTokens()
            || usage.costNanoUsd() > after.limits().costNanoUsd()
            || after.providerAttempts().equals(before.providerAttempts()) && !usage.equals(prior)) changed();
        return new ResumedUsage("RESUMED_NON_CHAT_USAGE_V1", before, after,
            after.providerAttempts() - before.providerAttempts(),
            new Usage(usage.inputTokens() - prior.inputTokens(), 0, usage.costNanoUsd() - prior.costNanoUsd()));
    }
    private static void changed() { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_WALLET_CHANGED"); }
    private static Readback canonical(Readback value) {
        return new Readback(value.state(), value.storeUuid(), value.approvalId(), value.runtimeFingerprint(),
            value.customerId(), value.deploymentId(), value.targetProfileId(), value.environment(), value.rateCardReference(),
            value.limits(), value.expiresAt(), value.chatAttempts(), value.providerAttempts(), value.accountedUsage(),
            value.stopped(), effectiveExpiry(value));
    }
}
