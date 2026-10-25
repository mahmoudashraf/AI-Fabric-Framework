package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class LifecycleValidityMaintenanceGuardTest {
    private JdbcTemplate jdbc;
    private LifecycleClaimGuard guard;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:validity_claim_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE deployment_lifecycle_grants(grant_id VARCHAR(64),deployment_id VARCHAR(64),"
            + "target_profile_id VARCHAR(64),release_id VARCHAR(64),claimed BOOLEAN,state VARCHAR(24))");
        jdbc.execute("CREATE TABLE deployment_lifecycle_commands(grant_id VARCHAR(64),phase VARCHAR(32),status VARCHAR(24))");
        guard = new LifecycleClaimGuard(jdbc);
    }

    @Test void unclaimedTargetKeepsNormalMaintenanceAccess() {
        guard.requireValidityMaintenanceAvailable("deployment", "staging", "release");
        guard.requireAvailable("deployment", "staging");
    }

    @Test void exactSuccessfulRecoveryAllowsValidityButNotOrdinaryMutation() {
        claim(); command("RECOVER", "SUCCEEDED");
        guard.requireValidityMaintenanceAvailable("deployment", "staging", "release");
        assertThatThrownBy(() -> guard.requireAvailable("deployment", "staging"))
            .hasMessageContaining("TARGET_NATIVE_OPERATION_IN_PROGRESS");
        assertThatThrownBy(() -> guard.requireDeploymentAvailable("deployment"))
            .hasMessageContaining("TARGET_NATIVE_OPERATION_IN_PROGRESS");
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "UNKNOWN", "BLOCKED", "FAILED"})
    void incompleteOrFailedNativeRecoveryCannotAuthorizeMaintenance(String status) {
        claim(); command("RECOVER", status); denied("release");
    }

    @ParameterizedTest @ValueSource(strings = {"STOP", "DELETE", "FENCE", "VERIFY", "CLAIM"})
    void anotherSuccessfulPhaseDoesNotCountAsRecovery(String phase) {
        claim(); command(phase, "SUCCEEDED"); denied("release");
    }

    @Test void successfulRecoveryOnAnotherReleaseDoesNotLendAuthority() {
        claim(); command("RECOVER", "SUCCEEDED"); denied("another-release");
    }

    @Test void aSecondClaimCannotHideBehindTheRecoveredClaim() {
        claim(); command("RECOVER", "SUCCEEDED");
        jdbc.update("INSERT INTO deployment_lifecycle_grants VALUES ('other','deployment','staging','release',TRUE,'OPEN')");
        denied("release");
    }

    @Test void closedRecoveryDoesNotAuthorizeAClaimedTarget() {
        claim(); command("RECOVER", "SUCCEEDED");
        jdbc.update("UPDATE deployment_lifecycle_grants SET state='CLOSED'"); denied("release");
    }

    private void claim() {
        jdbc.update("INSERT INTO deployment_lifecycle_grants VALUES ('grant','deployment','staging','release',TRUE,'OPEN')");
    }
    private void command(String phase, String status) {
        jdbc.update("INSERT INTO deployment_lifecycle_commands VALUES ('grant',?,?)", phase, status);
    }
    private void denied(String releaseId) {
        assertThatThrownBy(() -> guard.requireValidityMaintenanceAvailable("deployment", "staging", releaseId))
            .hasMessageContaining("TARGET_NATIVE_OPERATION_IN_PROGRESS");
    }
}
