package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.demo.dealership.config.ManagedDependencyProperties;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedDependencyLifecycleServiceTest {
    private ManagedDependencyLifecycleService service;
    private RuntimeConnectorOperationsClient connector;
    private JdbcTemplate jdbc;

    @BeforeEach void setUp() {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:receiver-lifecycle-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__dealership_demo_schema.sql"),
            new ClassPathResource("db/migration/V2__managed_dependency_lifecycle.sql"),
            new ClassPathResource("db/migration/V3__managed_dependency_current_operation.sql")).execute(data);
        connector = mock(RuntimeConnectorOperationsClient.class);
        VehicleRepository vehicles = mock(VehicleRepository.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode evidence = mapper.createObjectNode().put("status", "READY");
        when(connector.reconcileSource()).thenReturn(evidence);
        when(connector.sourceStatus()).thenReturn(evidence);
        when(connector.configuredSourceId()).thenReturn("source-1");
        when(vehicles.count()).thenReturn(3L);
        jdbc = new JdbcTemplate(data);
        service = new ManagedDependencyLifecycleService(properties(), connector, vehicles,
            jdbc, mapper);
    }

    @Test void initializePerformsSeedSyncReadbackOnceAndReplays() {
        assertThat(service.execute(request("INITIALIZE", "op-1")).evidenceReference()).contains("source-1", "vehicles:3");
        assertThat(service.execute(request("INITIALIZE", "op-1")).replay()).isTrue();
        verify(connector, times(1)).reconcileSource();
        verify(connector, times(1)).sourceStatus();
    }

    @Test void changedPayloadForAnOperationAndForeignBindingFailClosed() {
        service.execute(request("DISABLE", "same"));
        assertThatThrownBy(() -> service.execute(request("VERIFY_UNUSED", "same")))
            .hasMessageContaining("MANAGED_DEPENDENCY_OPERATION_CONFLICT");
        assertThatThrownBy(() -> service.execute(new ManagedDependencyLifecycleService.Request(
            ManagedDependencyLifecycleService.VERSION, "DISABLE", "scoped-seed-sync-canary/v1",
            "customer", "tenant", "deployment", "other-target", "version", "receiver", "service-ref",
            "account", "binding", "foreign"))).hasMessageContaining("MANAGED_DEPENDENCY_BINDING_MISMATCH");
    }

    @Test void currentStateReflectsDisableAfterAnInitializationReplayWithoutMutatingState() {
        var initialized = service.execute(request("INITIALIZE", "init"));
        service.execute(request("DISABLE", "disable"));
        assertThat(service.execute(request("INITIALIZE", "init")).state()).isEqualTo("READY");
        var before = jdbc.queryForMap("SELECT * FROM loomai_managed_dependency_state WHERE singleton_id=1");
        Long receipts = jdbc.queryForObject("SELECT COUNT(*) FROM loomai_managed_dependency_operation", Long.class);
        clearInvocations(connector);

        var status = service.current("fresh-challenge-012345");

        assertThat(status.current().state()).isEqualTo("DISABLED");
        assertThat(status.current().operationKey()).isEqualTo("disable");
        assertThat(status.current().observedAt()).isAfterOrEqualTo(initialized.observedAt());
        assertThat(status.current().replay()).isFalse();
        assertThat(status.challenge()).isEqualTo("fresh-challenge-012345");
        assertThat(service.available()).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM loomai_managed_dependency_state WHERE singleton_id=1")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loomai_managed_dependency_operation", Long.class)).isEqualTo(receipts);
        verifyNoInteractions(connector);
    }

    @Test void currentUnusedStateCountsWorkCreatedAfterTheIdempotentReceipt() {
        service.execute(request("VERIFY_UNUSED", "unused"));
        jdbc.update("INSERT INTO dealership_sync_run (id,request_id,status,total_count,succeeded_count,failed_count,started_at) VALUES (?,?,?,?,?,?,?)",
            "run-1", "request-1", "RUNNING", 1, 0, 0, Timestamp.from(Instant.now()));

        assertThat(service.execute(request("VERIFY_UNUSED", "unused")).activeWork()).isZero();
        var status = service.current("fresh-challenge-012345");
        assertThat(status.current().state()).isEqualTo("UNUSED");
        assertThat(status.current().operationKey()).isEqualTo("unused");
        assertThat(status.current().activeWork()).isEqualTo(1);
    }

    @Test void currentStateRejectsMissingOrForeignBindingAndTracksTheLatestGeneration() {
        assertThatThrownBy(() -> service.current("fresh-challenge-012345"))
            .hasMessageContaining("MANAGED_DEPENDENCY_NOT_INITIALIZED");
        service.execute(request("INITIALIZE", "generation-1"));
        service.execute(request("INITIALIZE", "generation-2"));
        service.execute(request("INITIALIZE", "generation-1"));
        assertThat(service.current("fresh-challenge-012345").current().operationKey()).isEqualTo("generation-2");
        jdbc.update("UPDATE loomai_managed_dependency_state SET binding_id='foreign' WHERE singleton_id=1");
        assertThatThrownBy(() -> service.current("fresh-challenge-012345"))
            .hasMessageContaining("MANAGED_DEPENDENCY_CURRENT_STATE_UNPROVEN");
    }

    private ManagedDependencyProperties properties() { return new ManagedDependencyProperties("k".repeat(32),
        "customer", "tenant", "deployment", "target", "version", "receiver", "service-ref", "account", "binding"); }
    private ManagedDependencyLifecycleService.Request request(String command, String operation) {
        return new ManagedDependencyLifecycleService.Request(ManagedDependencyLifecycleService.VERSION, command,
            "scoped-seed-sync-canary/v1", "customer", "tenant", "deployment", "target", "version",
            "receiver", "service-ref", "account", "binding", operation);
    }
}
