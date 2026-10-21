package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Readback;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.Usage;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.Limits;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
    static Readback wallet(Instant effective, long attempts) {
        return new Readback("ACTIVE", UUID.fromString("00000000-0000-0000-0000-000000000001"), "approval", "runtime-fingerprint",
            "customer", "deployment1", AdmissionFixture.TARGET, "staging", "rate-card", new Limits(50, 400000, 12000000000L),
            EXPIRED, attempts, 2L, new Usage(20, 10, 4), false, effective);
    }
}
