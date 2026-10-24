package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.*;
import static org.assertj.core.api.Assertions.*;

class CoreReleaseAuthorizationStoreTest {
    protected JdbcTemplate jdbc;
    private CoreReleaseAuthorizationStore store;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity release;

    protected DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:core_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @BeforeEach void setup() throws Exception {
        DataSource source = dataSource(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE platform_deployment_versions(id VARCHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE platform_deployment_releases(id VARCHAR(64) PRIMARY KEY)");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V210__core_release_authority_without_admission.sql"));
        }
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment')");
        jdbc.update("INSERT INTO platform_deployment_versions VALUES ('version')");
        jdbc.update("INSERT INTO platform_deployment_releases VALUES ('release')");
        store = new CoreReleaseAuthorizationStore(jdbc, new ObjectMapper().findAndRegisterModules());
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment");
        version.setStatus("PUBLISHED"); version.setConfigHash("a".repeat(64));
        release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setDeploymentVersionId("version"); release.setTargetProfileId("target"); release.setSourceArtifactId("artifact");
    }

    @Test void savesAndResolvesAuthorityWithoutAnyAdmissionSchema() {
        save();
        var result = store.require(deployment, version, release);
        assertThat(result.customerId()).isEqualTo("customer");
        assertThat(result.providerUsageMode()).isEqualTo("TEAM_AUTHORIZED");
        assertThat(result.operations().status()).isEqualTo("BYPASSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_release_authorizations", Integer.class)).isEqualTo(1);
    }

    @Test void changedOwnerTargetOrFrozenVersionCannotReuseTheAuthority() {
        save();
        deployment.setTenantId("different");
        assertThatThrownBy(() -> store.require(deployment, version, release)).hasMessageContaining("SCOPE_CHANGED");
        deployment.setTenantId("tenant"); release.setTargetProfileId("production");
        assertThatThrownBy(() -> store.require(deployment, version, release)).hasMessageContaining("SCOPE_CHANGED");
        release.setTargetProfileId("target"); version.setConfigHash("b".repeat(64));
        assertThatThrownBy(() -> store.require(deployment, version, release)).hasMessageContaining("SCOPE_CHANGED");
    }

    @Test void policyJsonDriftAndUnpublishedVersionsAreDenied() {
        save(); version.setStatus("DRAFT");
        assertThatThrownBy(() -> store.require(deployment, version, release)).hasMessageContaining("SCOPE_CHANGED");
        version.setStatus("PUBLISHED");
        jdbc.update("UPDATE platform_release_authorizations SET reason='OTHER_REASON' WHERE release_id='release'");
        assertThatThrownBy(() -> store.find("release")).hasMessage("CORE_RELEASE_AUTHORIZATION_CHANGED");
    }

    private void save() {
        store.save(deployment, version, release, "command", "TEAM_AUTHORIZED",
            new EffectiveDecision("DISABLED", "BYPASSED", "DISABLED_MANUAL_TEAM", "c".repeat(64), null, null, Instant.now()));
    }
}
