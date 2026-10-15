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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedDependencyLifecycleServiceTest {
    private ManagedDependencyLifecycleService service;
    private RuntimeConnectorOperationsClient connector;

    @BeforeEach void setUp() {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:receiver-lifecycle-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__dealership_demo_schema.sql"),
            new ClassPathResource("db/migration/V2__managed_dependency_lifecycle.sql")).execute(data);
        connector = mock(RuntimeConnectorOperationsClient.class);
        VehicleRepository vehicles = mock(VehicleRepository.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode evidence = mapper.createObjectNode().put("status", "READY");
        when(connector.reconcileSource()).thenReturn(evidence);
        when(connector.sourceStatus()).thenReturn(evidence);
        when(connector.configuredSourceId()).thenReturn("source-1");
        when(vehicles.count()).thenReturn(3L);
        service = new ManagedDependencyLifecycleService(properties(), connector, vehicles,
            new JdbcTemplate(data), mapper);
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

    private ManagedDependencyProperties properties() { return new ManagedDependencyProperties("k".repeat(32),
        "customer", "tenant", "deployment", "target", "version", "receiver", "service-ref", "account", "binding"); }
    private ManagedDependencyLifecycleService.Request request(String command, String operation) {
        return new ManagedDependencyLifecycleService.Request(ManagedDependencyLifecycleService.VERSION, command,
            "scoped-seed-sync-canary/v1", "customer", "tenant", "deployment", "target", "version",
            "receiver", "service-ref", "account", "binding", operation);
    }
}
