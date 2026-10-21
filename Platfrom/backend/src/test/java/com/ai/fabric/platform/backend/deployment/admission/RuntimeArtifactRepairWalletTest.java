package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Readback;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Usage;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.Limits;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeArtifactRepairWalletTest {
    private static final Instant EXPIRED = Instant.parse("2020-01-01T00:00:00Z");
    @Test void expiredWalletRemainsReadableAndOldImageOmissionMeansOriginalExpiry() {
        var old = wallet(null, 3); var upgraded = wallet(EXPIRED, 3);
        assertThatCode(() -> RuntimeArtifactRepairWallet.requireUnchanged(old, upgraded)).doesNotThrowAnyException();
        assertThat(RuntimeArtifactRepairWallet.effectiveExpiry(old)).isEqualTo(EXPIRED);
    }
    @Test void rejectsAnUnreviewedEffectiveExpiryChange() {
        assertThatThrownBy(() -> RuntimeArtifactRepairWallet.requireUnchanged(wallet(null, 3), wallet(EXPIRED.plusSeconds(1), 3))).isInstanceOf(AdmissionFailure.class);
    }
    @Test void rejectsCounterReset() {
        assertThatThrownBy(() -> RuntimeArtifactRepairWallet.requireUnchanged(wallet(null, 3), wallet(EXPIRED, 0))).isInstanceOf(AdmissionFailure.class);
    }
    @Test void resumedIndexingRetainsOriginalTermsAndRecordsTheExactUsageDelta() {
        var before = usage(wallet(null, 0), 0, 12, 727, 0, 14540);
        var after = usage(before, 0, 18, 1156, 0, 23120);
        var proof = RuntimeArtifactRepairWallet.requireResumedNonChatUsage(before, after);
        assertThat(proof.before()).isEqualTo(before); assertThat(proof.after()).isEqualTo(after);
        assertThat(proof.additionalProviderAttempts()).isEqualTo(6);
        assertThat(proof.additionalUsage()).isEqualTo(new Usage(429, 0, 8580));
        assertThatThrownBy(() -> RuntimeArtifactRepairWallet.requireUnchanged(before, after)).isInstanceOf(AdmissionFailure.class);
    }
    @ParameterizedTest @CsvSource({
        "1,18,1156,0,23120", "0,11,1156,0,23120", "0,18,726,0,23120", "0,18,1156,1,23120",
        "0,18,1156,0,14539", "0,12,1156,0,23120", "0,18,400001,0,23120", "0,18,1156,0,12000000001"
    })
    void resumedAccountingNeverAcceptsChatGenerationCounterResetOrOverspend(long chat, long providers, long input, long output, long cost) {
        var before = usage(wallet(null, 0), 0, 12, 727, 0, 14540);
        assertThatThrownBy(() -> RuntimeArtifactRepairWallet.requireResumedNonChatUsage(before,
            usage(before, chat, providers, input, output, cost))).isInstanceOf(AdmissionFailure.class);
    }
    @Test void resumedAccountingDoesNotPermitNewExpiryOrChangedWalletIdentity() {
        var before = usage(wallet(null, 0), 0, 12, 727, 0, 14540);
        var later = usage(wallet(EXPIRED.plusSeconds(1), 0), 0, 18, 1156, 0, 23120);
        assertThatThrownBy(() -> RuntimeArtifactRepairWallet.requireResumedNonChatUsage(before, later)).isInstanceOf(AdmissionFailure.class);
        assertThatThrownBy(() -> RuntimeArtifactRepairWallet.requireResumedNonChatUsage(before, null)).isInstanceOf(AdmissionFailure.class);
    }
    static Readback usage(Readback before, long chat, long providers, long input, long output, long cost) {
        return new Readback(before.state(), before.storeUuid(), before.approvalId(), before.runtimeFingerprint(), before.customerId(),
            before.deploymentId(), before.targetProfileId(), before.environment(), before.rateCardReference(), before.limits(), before.expiresAt(),
            chat, providers, new Usage(input, output, cost), before.stopped(), before.effectiveExpiresAt());
    }
    static Readback wallet(Instant effective, long attempts) {
        return new Readback("ACTIVE", UUID.fromString("00000000-0000-0000-0000-000000000001"), "approval", "runtime-fingerprint",
            "customer", "deployment1", AdmissionFixture.TARGET, "staging", "rate-card", new Limits(50, 400000, 12000000000L),
            EXPIRED, attempts, 2L, new Usage(20, 10, 4), false, effective);
    }
}
