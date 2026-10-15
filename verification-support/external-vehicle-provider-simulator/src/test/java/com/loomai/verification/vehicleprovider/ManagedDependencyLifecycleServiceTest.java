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

    @BeforeEach void setUp() {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:sim-lifecycle-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(data);
        SimulatorService simulator = mock(SimulatorService.class);
        when(simulator.status()).thenReturn(new ControlStatus("simulator", "test",
            new FixtureRun("fixture", "reset-1", Instant.now()), List.of()));
        when(simulator.accountId(Profile.AUTOTRADER)).thenReturn("advertiser");
        when(simulator.sourceVersion(Profile.AUTOTRADER, "advertiser")).thenReturn(7L);
        service = new ManagedDependencyLifecycleService(properties(), simulator,
            new JdbcTemplate(data), new ObjectMapper().findAndRegisterModules());
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

    private ManagedDependencyProperties properties() { return new ManagedDependencyProperties("k".repeat(32),
        "customer", "tenant", "deployment", "target", "version", "simulator", "service-ref", "account", "binding"); }
    private ManagedDependencyLifecycleService.Request request(String command, String operation) {
        return new ManagedDependencyLifecycleService.Request(ManagedDependencyLifecycleService.VERSION, command,
            "scoped-seed-sync-canary/v1", "customer", "tenant", "deployment", "target", "version",
            "simulator", "service-ref", "account", "binding", operation);
    }
}
