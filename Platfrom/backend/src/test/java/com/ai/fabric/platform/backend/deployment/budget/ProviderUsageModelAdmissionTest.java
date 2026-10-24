package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContract.authenticate;
import static org.assertj.core.api.Assertions.*;

class ProviderUsageModelAdmissionTest {
    static class Fixture extends ProviderUsageAllowanceContract {
        @Override DataSource freshDataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:model_admission_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000", "sa", "");
        }
    }

    private final Fixture f = new Fixture();

    @BeforeEach void setup() throws Exception { f.initialize(); }
    @AfterEach void cleanup() { f.clearIdentity(); }

    @ParameterizedTest @ValueSource(strings = {"gpt-5.4-mini", "gpt-5.4-mini-2026-03-17"})
    void freshCanaryConsumesTheWholeNewGrantWithoutChangingAnExistingActiveAllocation(String model) {
        var original = activeLegacyAllocation();
        var quota = f.service.readQuota(f.runtimeGrant().id());
        var grant = miniGrant(); f.service.approveGrant(grant);
        f.addDeployment("mini-canary"); configure("mini-canary", model); f.authorizeRelease("mini-canary");
        SecurityContextHolder.clearContext();

        var canary = f.service.prepareForAuthorizedRelease("release-mini-canary");
        assertThat(canary.scope().deploymentId()).isEqualTo("mini-canary");
        assertThat(canary.pricingReference()).isEqualTo(ProviderUsageRuntimeProfile.MINI_RATE_CARD);
        assertThat(canary.binding()).isEqualTo(grant.binding());
        assertThat(canary.caps()).isEqualTo(grant.credits());
        assertThat(canary.approvalId()).isNotEqualTo(original.approvalId());
        assertThat(f.service.prepareForAuthorizedRelease("release-mini-canary")).isEqualTo(canary);
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(f.service.readQuota(f.runtimeGrant().id())).isEqualTo(quota);
        assertThat(f.service.readAllocation(original.scope())).isEqualTo(original);
        assertThat(f.service.readQuota(grant.id()).remaining()).isEqualTo(new Limits(0, 0, 0));
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(2);
    }

    @Test void aNewMiniGrantCannotRetargetAnExistingLegacyAllocation() {
        var original = activeLegacyAllocation();
        var grant = miniGrant(); f.service.approveGrant(grant);
        configure("deployment", "gpt-5.4-mini"); SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment"))
            .isInstanceOf(ProviderUsageAllowanceFailure.class).hasMessageContaining("RUNTIME_MODEL_NOT_REVIEWED");
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(f.service.readAllocation(original.scope())).isEqualTo(original);
        assertThat(f.service.readQuota(grant.id()).remaining()).isEqualTo(grant.credits());
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void theNewCardStillRejectsUnreviewedModelsBeforeAnyDebit() {
        var grant = miniGrant(); f.service.approveGrant(grant);
        configure("deployment", "gpt-5.4"); f.authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment"))
            .isInstanceOf(ProviderUsageAllowanceFailure.class).hasMessageContaining("RUNTIME_MODEL_NOT_REVIEWED");
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(f.service.readQuota(grant.id()).remaining()).isEqualTo(grant.credits());
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    private SafeAllowanceProjection activeLegacyAllocation() {
        f.service.approveGrant(f.runtimeGrant()); f.authorizeRelease("deployment");
        var allocation = f.service.prepareForAuthorizedRelease("release-deployment");
        var store = UUID.randomUUID();
        f.service.markInitializedForAuthorizedRelease("release-deployment", store, allocation.runtimeAllocationFingerprint());
        return f.service.markActiveForAuthorizedRelease("release-deployment", store, allocation.runtimeAllocationFingerprint());
    }

    private void configure(String deployment, String model) {
        var version = f.versionRows.get("version-" + deployment);
        var config = f.codec.read(version.getProviderConfigJson(), ObjectNode.class);
        config.put("openaiModel", model);
        version.setProviderConfigJson(f.codec.write(config));
    }

    private ProviderUsageGrant miniGrant() {
        var original = f.runtimeGrant();
        return new ProviderUsageGrant("mini-grant", "mini-canary-campaign", original.customerId(), original.targetProfileId(),
            original.environment(), new Limits(50, 3_000_000, 5_000_000_000L), original.expiresAt(),
            new ProviderBinding("openai", List.of("gpt-4o-mini", "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17",
                "text-embedding-3-small"), original.binding().secretPurpose(), null),
            ProviderUsageRuntimeProfile.MINI_RATE_CARD, "reviewed-mini-canary-approval", original.payerRef());
    }
}
