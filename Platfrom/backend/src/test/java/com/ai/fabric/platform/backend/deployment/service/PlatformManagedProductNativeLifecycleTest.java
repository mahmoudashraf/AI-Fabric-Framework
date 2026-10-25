package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.provider.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real creation/placement receipts, not a mock of ownership authorization. */
class PlatformManagedProductNativeLifecycleTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.test", "project", "staging", "environment",
        "server", "destination", "example.test", null, 1, 10, false, true, "8085", "/health", "8085");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(), "private", config);
    OwnedDependencyOwnershipFixture fixture;
    PlatformManagedProductProvisioningService service;

    @BeforeEach void setup() {
        fixture = new OwnedDependencyOwnershipFixture(api, targets, resolver, mapper, connection);
        service = new PlatformManagedProductProvisioningService(mock(PlatformProvisioningProperties.class), mock(PlatformProductProvisioningProperties.class),
            mock(PlatformDeliveryProperties.class), mock(RailwayGraphqlClient.class), resolver, api, targets, mock(PlatformSecretService.class),
            mock(PlatformManagedProductServiceRepository.class), mock(ShopifyStoreConnectionRepository.class), mock(PlatformManagedProductServiceService.class),
            mock(PlatformAuditService.class), mapper, new CustomerSelfServiceProperties(Map.of(), 60), mock(CoolifyHostVolumeReclamationService.class));
        service.setOwnedDependencyOwnership(fixture.authority);
        var spec = ManagedDependencyFixtures.topology().services().stream().filter(value -> value.slot().equals("inbox")).findFirst().orElseThrow();
        fixture.graph(spec, "inbox", new PlatformManagedProductProvisioningService.OwnedDependencyResources("app", "db", true, true, Map.of()));
        fixture.applications.put("app", new CoolifyApplicationSummary("app", "inbox", null, "running:healthy", null, null,
            fixture.raw("app", "inbox", "ManagedDependency:graph:inbox")));
        fixture.databases.put("db", new CoolifyDatabaseSummary("db", "inbox-db", "running:healthy", "postgresql", "dependency", "dependency",
            fixture.raw("db", "inbox-db", "ManagedDependency:graph:inbox")));
        fixture.dbStorages.put("db", List.of(fixture.storage("db-storage", "owned-inbox-db", "/var/lib/postgresql/data")));
        fixture.receipts();
    }

    @Test void inspectsOwnedInboxAndDatabaseWithCreationReceiptStorage() {
        var result = service.inspectOwnedLifecycleResources(connection, "graph", "inbox", "inbox", fixture.resources());
        assertThat(result).extracting(PlatformManagedProductProvisioningService.OwnedLifecycleResource::uuid).containsExactly("app", "db");
        assertThat(result.getLast().storages()).extracting(CoolifyApplicationStorageSummary::name).containsExactly("owned-inbox-db");
    }

    @Test void refusesReusedResourceWhoseMarkerChanged() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.applications.get("app").raw()).put("description", "foreign");
        assertThatThrownBy(() -> service.dispatchOwnedLifecycleResource(connection, "graph", "inbox", "inbox", fixture.resources(), "app", "STOP", false))
            .hasMessageContaining("DEPENDENCY_PROVIDER_OWNERSHIP_CONFLICT");
        verify(api, never()).stop(any(), anyString(), anyBoolean());
    }

    @Test void unknownOrForeignResourceCannotBeStoppedOrCreated() {
        assertThatThrownBy(() -> service.dispatchOwnedLifecycleResource(connection, "graph", "inbox", "inbox", fixture.resources(), "foreign", "START", false))
            .hasMessageContaining("DEPENDENCY_LIFECYCLE_EFFECT_INVALID");
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test void deleteDispatchLeavesStorageForExplicitHostReclamation() {
        service.dispatchOwnedLifecycleResource(connection, "graph", "inbox", "inbox", fixture.resources(), "db", "DELETE", true);
        verify(api).deleteDatabase(connection, "db", true, false, false, false);
        assertThat(service.inspectOwnedLifecycleResources(connection, "graph", "inbox", "inbox", fixture.resources()).getLast().status()).isEqualTo("ABSENT");
        service.finishOwnedLifecycleResource(connection, "graph", "inbox", "inbox", fixture.resources(), "db");
        fixture.authority.requireDeleted(fixture.context(), OwnedDependencyOwnership.DATABASE, "db");
        assertThatThrownBy(() -> fixture.authority.requireVolumesDeleted(fixture.context(), "app", Map.of(), "db"))
            .hasMessageContaining("DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
    }

    @Test void absentReadbackDoesNotAdoptUnexplainedExternallyDeletedResource() {
        fixture.applications.remove("app");
        assertThatThrownBy(() -> service.inspectOwnedLifecycleResources(connection, "graph", "inbox", "inbox", fixture.resources()))
            .hasMessageContaining("DEPENDENCY_PROVIDER_OWNERSHIP_CONFLICT");
    }
}
