package com.loomai.demo.dealership.runtime;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.inventory.Vehicle;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RuntimeSyncContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void activeVehicleUsesServerOwnedScopeMetadata() {
        RuntimeSyncService service = service(propertiesWithRuntimeScope());

        Map<String, Object> operation = service.operation(vehicle("ACTIVE"));

        assertThat(operation.get("type")).isEqualTo("UPSERT");
        assertThat(operation.get("content").toString()).contains("Price: GBP 31950.00.");
        assertThat(operation.get("entity")).isInstanceOfSatisfying(Map.class, entity -> {
            assertThat(entity)
                .containsEntry("priceGbp", new java.math.BigDecimal("31950.00"))
                .doesNotContainKey("priceMinor");
        });
        assertThat(operation.get("metadata")).isInstanceOfSatisfying(Map.class, metadata -> {
            assertThat(metadata)
                .containsEntry("tenantId", "tenant-dealership-demo")
                .containsEntry("deploymentId", "dep-dealership-demo")
                .containsEntry("stockId", "DEMO-TEST-1")
                .containsEntry("lifecycleState", "ACTIVE");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void inactiveVehicleDeletesTheSameStableChunkIdentity() {
        RuntimeSyncService service = service(propertiesWithRuntimeScope());

        Map<String, Object> operation = service.operation(vehicle("SOLD"));

        assertThat(operation)
            .containsEntry("type", "DELETE")
            .containsEntry("vectorSpace", "dealer-vehicle")
            .containsEntry("id", "veh-test-1")
            .doesNotContainKeys("content", "entity", "metadata");
        assertThat(operation.get("identity")).isInstanceOfSatisfying(Map.class, identity -> {
            assertThat(identity)
                .containsEntry("sourceRecordId", "DEMO-TEST-1")
                .containsEntry("sourceRecordVersion", "3")
                .containsEntry("chunkId", "vehicle-profile");
        });
    }

    @Test
    void activeUpsertFailsClosedWithoutServerOwnedRuntimeScope() {
        RuntimeSyncService service = service(new DealershipDemoProperties());

        assertThatThrownBy(() -> service.operation(vehicle("ACTIVE")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LOOMAI_RUNTIME_TENANT_ID");
    }

    @Test
    void batchAssertionScopesMatchItsOperationTypes() {
        DealershipDemoProperties properties = propertiesWithRuntimeScope();
        RuntimeIngestionClient client = new RuntimeIngestionClient(
            properties,
            mock(RuntimePrivateAssertionSigner.class),
            RestClient.builder()
        );

        List<String> scopes = client.requiredBatchScopes(Map.of(
            "operations", List.of(
                Map.of("type", "UPSERT"),
                Map.of("type", "DELETE"),
                Map.of("type", "UPSERT")
            )
        ));

        assertThat(scopes).containsExactly(
            "data-sync:upsert",
            "data-sync:delete",
            "runtime:index:overview"
        );
    }

    private RuntimeSyncService service(DealershipDemoProperties properties) {
        return new RuntimeSyncService(
            mock(VehicleRepository.class),
            mock(SyncRepository.class),
            mock(RuntimeIngestionClient.class),
            properties
        );
    }

    private DealershipDemoProperties propertiesWithRuntimeScope() {
        DealershipDemoProperties properties = new DealershipDemoProperties();
        properties.getRuntime().setVectorSpace("dealer-vehicle");
        properties.getRuntime().getPrivateAccess().setTenantId("tenant-dealership-demo");
        properties.getRuntime().getPrivateAccess().setDeploymentId("dep-dealership-demo");
        return properties;
    }

    private Vehicle vehicle(String lifecycleState) {
        return new Vehicle(
            "veh-test-1",
            "DEMO-TEST-1",
            "aster-e1-test",
            "Aster",
            "E1",
            "Motion Long Range",
            2025,
            31_950_00L,
            "GBP",
            4_850,
            "Electric",
            "Automatic",
            "SUV",
            "Ocean blue",
            5,
            5,
            298,
            "Northfield Central",
            lifecycleState,
            "A quiet electric SUV for families.",
            List.of("Adaptive cruise control", "Heat pump"),
            "/assets/demos/dealership/vehicle-01.webp",
            "Demonstration inventory",
            Instant.parse("2026-09-29T10:00:00Z"),
            3L
        );
    }
}
