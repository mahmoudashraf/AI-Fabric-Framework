package com.ai.fabric.platform.backend.deployment.admission;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
class RuntimeArtifactRepairDeadlinePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @ParameterizedTest @ValueSource(strings = {"2026-10-21T01:53:17.065664499Z", "2026-10-21T01:53:17.065664792Z", "2026-10-21T01:53:17.999999999Z"})
    void reconcilesActualPostgresTimestampWithoutReplayingTheDrain(String input) {
        var deadline = Instant.parse(input);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        var persisted = jdbc.queryForObject("SELECT CAST(? AS TIMESTAMP WITH TIME ZONE)",
            (row, index) -> row.getTimestamp(1).toInstant(), Timestamp.from(deadline));
        var h = new RuntimeArtifactRepairOperationsTest.Harness(Effect.BEGIN_DRAIN, Mode.IDLE_CHECKPOINT_COLD_RESTART, deadline);
        h.observedDeadline = persisted;
        assertThatCode(() -> h.ops.reconcile(h.plan, Effect.BEGIN_DRAIN)).doesNotThrowAnyException();
        verify(h.runtime, never()).command(anyString(), any(), anyMap());
        h.observedDeadline = persisted.plusNanos(1000);
        assertThatThrownBy(() -> h.ops.reconcile(h.plan, Effect.BEGIN_DRAIN))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("DRAIN_BINDING_CHANGED");
    }
}
