package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.*;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OwnedManagedDependencyProvisioningTest {
    final ObjectMapper mapper = new ObjectMapper();
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final PlatformProductProvisioningProperties properties = mock(PlatformProductProvisioningProperties.class);
    final PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint = mock(PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint.class);
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging",
        "environment", "server", "destination", "example.test", null, 1, 10, false, true, "8080", "/actuator/health", "8080");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(), "private", config);
    PlatformManagedProductProvisioningService service;
    @BeforeEach void setup() {
        when(properties.requestTimeout()).thenReturn(Duration.ofSeconds(1)); when(properties.pollInterval()).thenReturn(Duration.ofMillis(1));
        service = new PlatformManagedProductProvisioningService(mock(PlatformProvisioningProperties.class), properties,
            mock(PlatformDeliveryProperties.class), mock(RailwayGraphqlClient.class), resolver, api, targets,
            mock(PlatformSecretService.class), mock(PlatformManagedProductServiceRepository.class), mock(ShopifyStoreConnectionRepository.class),
            mock(PlatformManagedProductServiceService.class), mock(PlatformAuditService.class), mapper);
    }
    ObjectNode raw(String name, String marker) {
        return mapper.createObjectNode().put("name", name).put("description", marker).put("server_uuid", "server")
            .put("environment_uuid", "environment").put("project_uuid", "project").put("destination_uuid", "destination");
    }
    PlatformManagedProductProvisioningService.OwnedDependencyResources empty(boolean intent) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(null, null, intent, false, Map.of());
    }
    @Test void misleadingStagingProfileOnProductionServerIsRejected() {
        var target = new DeploymentTargetProfileEntity(); target.setId("dtp-coolify-prod-staging"); target.setActive(true);
        target.setEnvironmentName("staging"); target.setProviderType(DeploymentProviderType.COOLIFY);
        when(targets.findById(target.getId())).thenReturn(Optional.of(target)); when(resolver.requireConnection(target)).thenReturn(connection);
        when(api.getServer(connection, "server")).thenReturn(mapper.createObjectNode().put("uuid", "server").put("ip", "46.225.162.106"));
        assertThatThrownBy(() -> service.ownedDependencyConnection(target.getId(), "staging"))
            .hasMessageContaining("SERVER_PLACEMENT_CONFLICT");
        verify(api, never()).createDockerImageApplication(any(), any());
    }
    @Test void immutableImageAndCreationIntentArePassedThroughExistingProvider() {
        var spec = ManagedDependencyFixtures.http("simulator", false);
        var app = new CoolifyApplicationSummary("app", "dep-service", "https://dep-service.example.test", "running:healthy",
            "registry.example.test/dependency", "sha256-" + "a".repeat(64), raw("dep-service", "ManagedDependency:graph:simulator"));
        when(api.createDockerImageApplication(eq(connection), any())).thenReturn("app");
        when(api.getApplication(connection, "app")).thenReturn(Optional.of(app));
        when(api.reconcilePersistentVolumeStorage(eq(connection), eq("app"), anyString(), eq("/app/data")))
            .thenReturn(new CoolifyApplicationStorageSummary("volume", "persistent", "dep-service-" + Integer.toHexString("/app/data".hashCode()), "/app/data", null, false, mapper.createObjectNode()));
        when(api.start(connection, "app", true, true)).thenReturn(new CoolifyActionResponse("ok", "build", mapper.createObjectNode()));
        when(api.getDeployment(connection, "build")).thenReturn(Optional.of(new CoolifyDeploymentSummary("build", "dep-service", "app", "finished", null, null, null, null, null, mapper.createObjectNode())));
        var result = service.reconcileOwnedDependency(connection, "graph", "dep-service", spec, empty(false), unused -> List.of(), checkpoint);
        assertThat(result.applicationUuid()).isEqualTo("app");
        var request = ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(api).createDockerImageApplication(eq(connection), request.capture());
        assertThat(request.getValue().imageRepository()).isEqualTo("registry.example.test/dependency");
        assertThat(request.getValue().imageTag()).isEqualTo("sha256-" + "a".repeat(64));
        var order = inOrder(checkpoint, api); order.verify(checkpoint).guard(); order.verify(checkpoint).creating("APPLICATION");
        order.verify(api).createDockerImageApplication(eq(connection), any()); order.verify(checkpoint).created("APPLICATION", "app");
        verify(api, never()).createPublicApplication(any(), any());
    }
    @Test void uncertainCreateNeverAllocatesAnotherApplication() {
        when(api.listApplications(connection)).thenReturn(List.of());
        assertThatThrownBy(() -> service.reconcileOwnedDependency(connection, "graph", "dep-service",
            ManagedDependencyFixtures.http("simulator", false), empty(true), unused -> List.of(), checkpoint))
            .hasMessageContaining("CREATE_UNCERTAIN");
        verify(api, never()).createDockerImageApplication(any(), any());
    }
    @Test void foreignResourceCannotBeReconfiguredOrDeleted() {
        var spec = ManagedDependencyFixtures.http("simulator", false);
        var app = new CoolifyApplicationSummary("foreign", "dep-service", "https://dep-service.example.test", "running:healthy",
            spec.image(), null, raw("dep-service", "ManagedDependency:OTHER:simulator"));
        when(api.getApplication(connection, "foreign")).thenReturn(Optional.of(app));
        var resource = new PlatformManagedProductProvisioningService.OwnedDependencyResources("foreign", null, true, false, Map.of());
        assertThatThrownBy(() -> service.reconcileOwnedDependency(connection, "graph", "dep-service", spec, resource, unused -> List.of(), checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        assertThatThrownBy(() -> service.retireOwnedDependency(connection, "graph", "dep-service", "simulator", resource, checkpoint))
            .hasMessageContaining("OWNERSHIP_CONFLICT");
        verify(api, never()).updateEnvironmentVariables(any(), anyString(), anyList()); verify(api, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }
    @Test void databaseAddressMustComeFromActualProviderEvidence() {
        when(api.getDatabase(connection, "db")).thenReturn(Optional.of(new CoolifyDatabaseSummary("db", "name", "running:healthy", "postgresql", "dependency", "dependency", mapper.createObjectNode())));
        assertThatThrownBy(() -> service.ownedDependencyDatabaseJdbc(connection, "db")).hasMessageContaining("ADDRESS_UNVERIFIED");
    }
}
