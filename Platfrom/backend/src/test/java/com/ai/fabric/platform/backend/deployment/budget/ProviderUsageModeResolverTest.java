package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

class ProviderUsageModeResolverTest {
    private final DeploymentOperationsProperties properties = new DeploymentOperationsProperties();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    private JdbcTemplate jdbc;
    private ProviderUsageModeResolver resolver;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:provider_modes_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE provider_usage_allocations(approval_id VARCHAR(96),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))");
        ProviderUsageCoreReleaseTestSchema.create(jdbc);
        resolver = new ProviderUsageModeResolver(properties, jdbc,
            new CoreReleaseAuthorizationStore(jdbc, new ObjectMapper().findAndRegisterModules()));
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        release.setId("release-deployment"); release.setDeploymentId("deployment"); release.setDeploymentVersionId("version-deployment");
        release.setTargetProfileId("target"); release.setSourceArtifactId("artifact");
    }

    @Test void defaultsToFundedWithoutCreatingAnAllowance() {
        assertThat(resolver.resolve(deployment, "target")).isEqualTo("FUNDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void teamModeRequiresAnExactTrustedTargetAllowlist() {
        properties.setProviderUsageMode("TEAM_AUTHORIZED");
        assertThatThrownBy(() -> resolver.resolve(deployment, "target")).hasMessageContaining("TEAM_TARGET_NOT_AUTHORIZED");
        properties.setTeamAuthorizedTargets(Set.of("*", "target-other"));
        assertThatThrownBy(() -> resolver.resolve(deployment, "target")).hasMessageContaining("TEAM_TARGET_NOT_AUTHORIZED");
        properties.setTeamAuthorizedTargets(Set.of("target"));
        assertThat(resolver.resolve(deployment, "target")).isEqualTo("TEAM_AUTHORIZED");
    }

    @Test void historicalAllocationsDoNotOverrideTheServerAuthorizedModeForANewRelease() {
        properties.setProviderUsageMode("TEAM_AUTHORIZED"); properties.setTeamAuthorizedTargets(Set.of("target", "other"));
        jdbc.update("INSERT INTO provider_usage_allocations VALUES ('approval','deployment','target')");
        assertThat(resolver.resolve(deployment, "target")).isEqualTo("TEAM_AUTHORIZED");
        assertThat(resolver.resolve(deployment, "other")).isEqualTo("TEAM_AUTHORIZED");
        properties.setProviderUsageMode("CUSTOMER_KEY");
        assertThatThrownBy(() -> resolver.resolve(deployment, "target")).hasMessageContaining("CUSTOMER_KEY_NOT_SUPPORTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void customerKeyWithoutProvenOwnedCredentialsIsExplicitlyUnsupported() {
        properties.setProviderUsageMode("CUSTOMER_KEY");
        assertThatThrownBy(() -> resolver.resolve(deployment, "target")).hasMessageContaining("CUSTOMER_KEY_NOT_SUPPORTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void unknownModeIsNotAnUnlimitedDefault() {
        properties.setProviderUsageMode("unlimited");
        assertThatThrownBy(() -> resolver.resolve(deployment, "target")).hasMessageContaining("MODE_INVALID");
    }

    @Test void releaseModeIsPinnedRatherThanReevaluatedFromConfigurationOrAvailability() {
        ProviderUsageCoreReleaseTestSchema.authorize(jdbc, "deployment", "TEAM_AUTHORIZED");
        properties.setProviderUsageMode("FUNDED"); properties.setTeamAuthorizedTargets(Set.of());
        assertThat(resolver.forRelease(deployment, release)).contains("TEAM_AUTHORIZED");
        properties.setProviderUsageMode("invalid-current-setting");
        assertThat(resolver.forRelease(deployment, release)).contains("TEAM_AUTHORIZED");
    }

    @Test void fundedReleaseNeverDowngradesWhenTheCurrentServerDefaultChanges() {
        ProviderUsageCoreReleaseTestSchema.authorize(jdbc, "deployment", "FUNDED");
        properties.setProviderUsageMode("TEAM_AUTHORIZED"); properties.setTeamAuthorizedTargets(Set.of("target"));
        assertThat(resolver.forRelease(deployment, release)).contains("FUNDED");
    }

    @Test void anAuthorizedTeamReleaseCanCoexistWithUnchangedHistoricalFunding() {
        ProviderUsageCoreReleaseTestSchema.authorize(jdbc, "deployment", "TEAM_AUTHORIZED");
        jdbc.update("INSERT INTO provider_usage_allocations VALUES ('approval','deployment','target')");
        assertThat(resolver.forRelease(deployment, release)).contains("TEAM_AUTHORIZED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void priorFundingDoesNotBypassTheTeamTargetAllowlist() {
        jdbc.update("INSERT INTO provider_usage_allocations VALUES ('approval','deployment','target')");
        properties.setProviderUsageMode("TEAM_AUTHORIZED");
        properties.setTeamAuthorizedTargets(Set.of("another-target"));
        assertThatThrownBy(() -> resolver.resolve(deployment, "target")).hasMessageContaining("TEAM_TARGET_NOT_AUTHORIZED");
        assertThat(resolver.forRelease(deployment, release)).contains("FUNDED");
    }

    @Test void missingCoreRecordDoesNotApplyTheCurrentTeamDefaultToAnOldRelease() {
        properties.setProviderUsageMode("TEAM_AUTHORIZED"); properties.setTeamAuthorizedTargets(Set.of("target"));
        assertThat(resolver.forRelease(deployment, release)).isEmpty();
    }

    @Test void historicalAllocationCannotLoseEnforcementWhenCoreAuthorizationIsAbsent() {
        properties.setProviderUsageMode("TEAM_AUTHORIZED"); properties.setTeamAuthorizedTargets(Set.of("target"));
        jdbc.update("INSERT INTO provider_usage_allocations VALUES ('historical','deployment','target')");
        assertThat(resolver.forRelease(deployment, release)).contains("FUNDED");
        release.setTargetProfileId("other");
        assertThat(resolver.forRelease(deployment, release)).isEmpty();
    }

    @Test void coreScopeAndDecisionCannotBeSubstitutedByReleaseMetadata() {
        ProviderUsageCoreReleaseTestSchema.authorize(jdbc, "deployment", "TEAM_AUTHORIZED");
        release.setTargetProfileId("foreign");
        assertThatThrownBy(() -> resolver.forRelease(deployment, release)).hasMessageContaining("CORE_AUTHORIZATION_SCOPE_CHANGED");
        release.setTargetProfileId("target");
        ProviderUsageCoreReleaseTestSchema.decision(jdbc, "DENY", "a".repeat(64));
        assertThatThrownBy(() -> resolver.forRelease(deployment, release)).hasMessageContaining("CORE_AUTHORIZATION_DENIED");
    }
}
