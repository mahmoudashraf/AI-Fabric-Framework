package com.loomai.verification.vehicleprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.verification.vehicleprovider.config.ManagedDependencyProperties;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.ControlStatus;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FixtureRun;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.service.ManagedDependencyLifecycleService;
import com.loomai.verification.vehicleprovider.service.SimulatorService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedDependencyLifecycleServiceTest {
    private ManagedDependencyLifecycleService service;
    private JdbcTemplate jdbc;

    @BeforeEach void setUp() {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:sim-lifecycle-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(data);
        SimulatorService simulator = mock(SimulatorService.class);
        when(simulator.status()).thenReturn(new ControlStatus("simulator", "test",
            new FixtureRun("fixture", "reset-1", Instant.now()), List.of()));
        when(simulator.accountId(Profile.AUTOTRADER)).thenReturn("advertiser");
        when(simulator.sourceVersion(Profile.AUTOTRADER, "advertiser")).thenReturn(7L);
        jdbc = new JdbcTemplate(data);
        service = new ManagedDependencyLifecycleService(properties(), simulator,
            jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test void validatesExactBindingAndDurablyReplays() {
        var first = service.execute(request("INITIALIZE", "op-1"));
        var replay = service.execute(request("INITIALIZE", "op-1"));
        assertThat(first.state()).isEqualTo("READY");
        assertThat(first.evidenceReference()).contains("reset-1", "source-version:7");
        assertThat(replay.replay()).isTrue();
        assertThatThrownBy(() -> service.execute(new ManagedDependencyLifecycleService.Request(
            ManagedDependencyLifecycleService.VERSION, "INITIALIZE", "scoped-seed-sync-canary/v1",
            "foreign", "tenant", "deployment", "target", "version", "simulator", "service-ref",
            "account", "binding", "op-2"))).hasMessageContaining("MANAGED_DEPENDENCY_BINDING_MISMATCH");
    }

    @Test void disableMakesTheDedicatedServiceUnavailableAndUnusedHasNoWork() {
        assertThat(service.execute(request("DISABLE", "op-disable")).state()).isEqualTo("DISABLED");
        assertThat(service.available()).isFalse();
        assertThat(service.execute(request("VERIFY_UNUSED", "op-unused")).activeWork()).isZero();
    }

    @Test void currentReadbackDoesNotReplayCachedReadyOrMutateDurableState() {
        var initialized = service.execute(request("INITIALIZE", "init"));
        service.execute(request("DISABLE", "disable"));
        assertThat(service.execute(request("INITIALIZE", "init")).state()).isEqualTo("READY");
        var before = jdbc.queryForMap("select * from loomai_managed_dependency_state where singleton_id=1");
        Long receipts = jdbc.queryForObject("select count(*) from loomai_managed_dependency_operation", Long.class);

        var current = service.current("fresh-challenge-012345");

        assertThat(current.challenge()).isEqualTo("fresh-challenge-012345");
        assertThat(current.current().state()).isEqualTo("DISABLED");
        assertThat(current.current().operationKey()).isEqualTo("disable");
        assertThat(current.current().observedAt()).isAfterOrEqualTo(initialized.observedAt());
        assertThat(current.current().replay()).isFalse();
        assertThat(service.available()).isFalse();
        assertThat(jdbc.queryForMap("select * from loomai_managed_dependency_state where singleton_id=1")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from loomai_managed_dependency_operation", Long.class)).isEqualTo(receipts);
    }

    @Test void currentReadbackReportsTheCurrentGenerationAndRejectsMissingOrUnprovenState() {
        assertThatThrownBy(() -> service.current("fresh-challenge-012345"))
            .hasMessageContaining("MANAGED_DEPENDENCY_NOT_INITIALIZED");
        service.execute(request("INITIALIZE", "generation-1"));
        service.execute(request("INITIALIZE", "generation-2"));
        service.execute(request("INITIALIZE", "generation-1"));
        assertThat(service.current("fresh-challenge-012345").current().operationKey()).isEqualTo("generation-2");
        jdbc.update("update loomai_managed_dependency_state set operation_key=null where singleton_id=1");
        assertThatThrownBy(() -> service.current("fresh-challenge-012345"))
            .hasMessageContaining("MANAGED_DEPENDENCY_CURRENT_STATE_UNPROVEN");
    }

    @Test void currentReadbackDoesNotTrustAReplayedDisableAfterReinitialization() {
        service.execute(request("DISABLE", "disable"));
        service.execute(request("INITIALIZE", "init"));

        assertThat(service.execute(request("DISABLE", "disable")).state()).isEqualTo("DISABLED");
        var status = service.current("fresh-challenge-012345");
        assertThat(status.current().state()).isEqualTo("READY");
        assertThat(status.current().operationKey()).isEqualTo("init");
        assertThat(service.available()).isTrue();
    }

    private ManagedDependencyProperties properties() { return new ManagedDependencyProperties("k".repeat(32),
        "customer", "tenant", "deployment", "target", "version", "simulator", "service-ref", "account", "binding"); }
    private ManagedDependencyLifecycleService.Request request(String command, String operation) {
        return new ManagedDependencyLifecycleService.Request(ManagedDependencyLifecycleService.VERSION, command,
            "scoped-seed-sync-canary/v1", "customer", "tenant", "deployment", "target", "version",
            "simulator", "service-ref", "account", "binding", operation);
    }
}
