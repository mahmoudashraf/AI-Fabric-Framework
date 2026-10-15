package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionAcceptedActivityTest extends AdmissionFixture {
    @Test
    void observedActivityExtendsFromAcceptedTimeAndRepeatedPollingDoesNotExtendAgain() {
        Reservation active = active();
        ReleaseBinding binding = store.releaseBinding("release");
        var observed = signal("release", 1, 10);
        Reservation refreshed = store.acceptActivity(active.fence(), binding, observed);
        assertThat(refreshed.lastActivityAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(refreshed.expiresAt()).isEqualTo(NOW.plusSeconds(1010));
        assertThat(store.acceptActivity(refreshed.fence(), binding, observed)).isEqualTo(refreshed);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_activity_watermarks", Long.class)).isEqualTo(1L);
    }

    @Test
    void foreignReleaseFutureTimeAndRegressingSignalsCannotRefreshStaging() {
        Reservation active = active();
        ReleaseBinding binding = store.releaseBinding("release");
        assertThatThrownBy(() -> store.acceptActivity(active.fence(), binding, signal("foreign", 1, 10)))
            .hasMessageContaining("ACTIVITY_READBACK_SCOPE_INVALID");
        assertThatThrownBy(() -> store.acceptActivity(active.fence(), binding, signal("release", 1, 30)))
            .hasMessageContaining("ACTIVITY_SIGNAL_INVALID");
        Reservation refreshed = store.acceptActivity(active.fence(), binding, signal("release", 2, 10));
        assertThatThrownBy(() -> store.acceptActivity(refreshed.fence(), binding, signal("release", 1, 9)))
            .hasMessageContaining("ACTIVITY_SIGNAL_REGRESSED");
        Reservation unknown = store.outcome(refreshed.fence(), State.OUTCOME_UNKNOWN, "failed-release", "worker", NOW.plusSeconds(20));
        assertThatThrownBy(() -> store.acceptActivity(unknown.fence(), binding, signal("release", 3, 15)))
            .hasMessageContaining("ACTIVITY_ALLOCATION_CHANGED");
    }

    @Test
    void customerExtensionCommandReplaysExactlyWithoutSpendingAnotherExtension() {
        Reservation reservation = one();
        Reservation extended = store.extendCommand(reservation.fence(), false, grant(1), "extend", "request-fingerprint", "owner", NOW);
        assertThat(store.extendCommand(reservation.fence(), false, grant(1), "extend", "request-fingerprint", "owner", NOW)).isEqualTo(extended);
        assertThat(store.require(reservation.id()).extensions()).isEqualTo(1);
        assertThatThrownBy(() -> store.extendCommand(extended.fence(), true, grant(1), "extend", "other-fingerprint", "owner", NOW))
            .hasMessageContaining("OPERATION_KEY_REUSED");
    }

    @Test
    void acceptedRuntimeAndConfigurationActivityDoNotShortenApprovedExtensions() {
        Reservation active = active();
        Reservation extended = store.extendCommand(active.fence(), false, grant(1), "extend", "request", "owner", NOW.plusSeconds(20));
        Reservation refreshed = store.acceptActivity(extended.fence(), store.releaseBinding("release"), signal("release", 1, 10));
        assertThat(refreshed.lastActivityAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(refreshed.expiresAt()).isEqualTo(extended.expiresAt());
        Reservation configuration = store.activity(refreshed.fence(), Activity.CONFIGURATION_WRITE, "configuration", "owner", NOW.plusSeconds(20));
        assertThat(configuration.expiresAt()).isEqualTo(extended.expiresAt());
        assertThat(configuration.extensions()).isEqualTo(1);
    }

    private Reservation active() {
        Reservation reservation = one();
        Reservation active = store.reconcile(reservation.fence(), readback(reservation, NOW, false, 0L, 0L), false, "worker", NOW);
        jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
             (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,
              deployment_id,target_profile_id,state,created_at,updated_at) VALUES ('release',?,?,'apply','version','config-hash',
              'customer','tenant','deployment1',?,'ACTIVE',?,?)
            """, active.id(), active.generation(), TARGET, Timestamp.from(NOW), Timestamp.from(NOW));
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW.plusSeconds(20), ZoneOffset.UTC));
        return active;
    }

    private ActivityReadback signal(String release, long sequence, int acceptedSeconds) {
        return new ActivityReadback("loomai-runtime-admission-activity-v1", "deployment1", "tenant", TARGET, release,
            NOW.plusSeconds(20), List.of(new ActivitySignal(Activity.ACCEPTED_SESSION, sequence, NOW.plusSeconds(acceptedSeconds))));
    }
}
