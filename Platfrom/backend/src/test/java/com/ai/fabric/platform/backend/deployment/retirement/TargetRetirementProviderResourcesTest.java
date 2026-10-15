package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetRetirementProviderResourcesTest {
    private final TargetRetirementInventory inventory = mock(TargetRetirementInventory.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient client = mock(CoolifyApiClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private TargetRetirementProviderResources service;
    private Context context;
    private ResourceGeneration application;
    private CoolifyConnection connection;

    @BeforeEach void setup() throws Exception {
        service = new TargetRetirementProviderResources(inventory, profiles, resolver, client, List.of("46.224.145.148"));
        var profile = new DeploymentTargetProfileEntity();
        profile.setId("target");
        profile.setEnvironmentName("staging");
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        when(profiles.findById("target")).thenReturn(Optional.of(profile));
        var config = mock(CoolifyTargetProfileConfig.class);
        when(config.projectUuid()).thenReturn("project");
        when(config.environmentUuid()).thenReturn("environment");
        when(config.serverUuid()).thenReturn("server");
        when(config.destinationUuid()).thenReturn("destination");
        connection = new CoolifyConnection("https://coolify.test", "private-token", config);
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"46.224.145.148\"}"));
        application = application(List.of());
        context = context(application);
        var observed = new CoolifyApplicationSummary("app", "runtime", null, "running", null, null,
            mapper.readTree("{\"uuid\":\"app\",\"server_uuid\":\"server\"}"));
        when(client.getApplication(connection, "app")).thenReturn(Optional.of(observed));
    }

    @Test void deleteUsesExactUuidAndNoVolumeNetworkOrDockerCascadeOutsideTransaction() {
        when(client.delete(connection, "app", true, false, false, false)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });
        assertThat(service.delete(context, application).complete()).isTrue();
        var order = inOrder(inventory, client);
        order.verify(inventory).requireProviderIdentity(eq(application), any());
        order.verify(client).delete(connection, "app", true, false, false, false);
        verify(client, never()).deleteDatabase(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void deletionRequiresExactFrozenReleaseVersionAndGenerationBinding() {
        var incomplete = new Snapshot("customer", "tenant", "deployment", "target", "target-generation", 1,
            List.of(new ReleaseGeneration("release", "", "")), List.of(application), List.of());
        var invalidContext = new Context("operation", "operation-key", "fingerprint", incomplete, 2,
            "worker", "actor", "CUSTOMER_ADMIN", Instant.now().plusSeconds(30),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup-decision"));

        assertThatThrownBy(() -> service.delete(invalidContext, application))
            .hasMessageContaining("PROVIDER_RELEASE_GENERATION_UNFROZEN");
        verify(client, never()).getApplication(any(), anyString());
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void productionPlacementIsRejectedRegardlessOfStagingProfileName() throws Exception {
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"46.225.162.106\"}"));
        assertThatThrownBy(() -> service.delete(context, application)).hasMessageContaining("STAGING_PLACEMENT_NOT_APPROVED");
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(client, never()).getApplication(any(), anyString());
    }

    @Test void missingPlacementAndProviderReadbackAreNotProofOfAbsence() throws Exception {
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\"}"));
        assertThatThrownBy(() -> service.readback(context, application)).hasMessageContaining("STAGING_PLACEMENT_NOT_APPROVED");
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"46.224.145.148\"}"));
        when(client.getApplication(connection, "app")).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service.readback(context, application)).hasMessageContaining("timeout");
    }

    @Test void foreignApplicationServerEvidenceBlocksDeletion() throws Exception {
        when(client.getApplication(connection, "app")).thenReturn(Optional.of(new CoolifyApplicationSummary("app", "runtime", null,
            "running", null, null, mapper.readTree("{\"uuid\":\"app\",\"server_uuid\":\"production-server\"}"))));
        doThrow(new TargetRetirementFailure("PROVIDER_RESOURCE_OWNERSHIP_CHANGED"))
            .when(inventory).requireProviderIdentity(eq(application), any());
        assertThatThrownBy(() -> service.delete(context, application)).hasMessageContaining("PROVIDER_RESOURCE_OWNERSHIP_CHANGED");
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void customerSourcesAndUnsupportedProvidersAreNeverLifecycleResources() {
        var source = new ResourceGeneration("source", null, DeploymentProviderType.COOLIFY, "EXTERNAL_DOCUMENT_STORAGE_BINDING",
            "customer-bucket", null, "generation", true);
        assertThatThrownBy(() -> service.delete(context, source)).hasMessageContaining("PROVIDER_RESOURCE_UNSUPPORTED");
        verifyNoInteractions(client, resolver);
    }

    @Test void databaseVolumesAndSecretReferencesAreLeftForTheDependencyParticipant() throws Exception {
        var database = new ResourceGeneration("database", "release", DeploymentProviderType.COOLIFY, "RUNTIME_POSTGRES_DATABASE",
            "db", "server", "project", "environment", "destination", "database",
            "Durable runtime chat database for AI Fabric deployment deployment",
            "generation", "provider-generation", List.of(), false);
        context = context(database);
        when(client.getDatabase(connection, "db")).thenReturn(Optional.of(new CoolifyDatabaseSummary("db", "database", "running", "postgresql",
            null, null, mapper.readTree("{\"uuid\":\"db\",\"server_uuid\":\"server\"}"))));
        when(client.deleteDatabase(connection, "db", true, false, false, false)).thenAnswer(call -> {
            when(client.getDatabase(connection, "db")).thenReturn(Optional.empty());
            return null;
        });
        assertThat(service.delete(context, database).complete()).isTrue();
        verify(client).deleteDatabase(connection, "db", true, false, false, false);
    }

    @Test void absenceIsReadBackBeforeDeleteAndEmptyAllowlistFailsClosed() {
        when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
        assertThat(service.delete(context, application).complete()).isTrue();
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        var unconfigured = new TargetRetirementProviderResources(inventory, profiles, resolver, client, List.of());
        assertThatThrownBy(() -> unconfigured.readback(context, application)).hasMessageContaining("STAGING_PLACEMENT_NOT_APPROVED");
    }

    @Test void persistentVolumeIsDeletedAndProviderAbsenceIsProvedBeforeApplicationCapacityRelease() {
        var expectedVolume = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(expectedVolume));
        context = context(application);
        var observedVolume = new CoolifyApplicationStorageSummary("storage-1", "persistent", "runtime-data",
            "/app/data", null, false, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(false, true);
        when(client.listApplicationStorages(connection, "app"))
            .thenReturn(List.of(observedVolume), List.of());
        when(client.delete(connection, "app", true, false, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        var order = inOrder(client, inventory);
        order.verify(client).deleteApplicationStorage(connection, "app", "storage-1");
        order.verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
        order.verify(client).delete(connection, "app", true, false, false, false);
        verify(inventory, times(2)).requireVolumeInventory(eq(application), anyList(), eq(true));
    }

    @Test void absentApplicationWithoutFrozenVolumeDispositionFailsClosed() {
        var expectedVolume = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(expectedVolume));
        context = context(application);
        when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
        when(inventory.volumesReclaimed(application)).thenReturn(false);

        assertThatThrownBy(() -> service.readback(context, application))
            .hasMessageContaining("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
    }

    @Test void approvedRetainedVolumeMustRemainPresentAndIsNeverDeleted() {
        var retained = new ProviderVolumeGeneration("storage-retained", "persistent", "documents",
            "/app/document-sources", "/srv/loomai/documents", "retained-generation", true);
        application = application(List.of(retained));
        context = context(application);
        var observed = new CoolifyApplicationStorageSummary("storage-retained", "persistent", "documents",
            "/app/document-sources", "/srv/loomai/documents", true, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(false, true);
        when(client.listApplicationStorages(connection, "app"))
            .thenReturn(List.of(observed), List.of(observed));
        when(client.delete(connection, "app", true, false, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        verify(client, never()).deleteApplicationStorage(any(), anyString(), anyString());
        verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
        verify(inventory, times(2)).requireVolumeInventory(eq(application), anyList(), eq(true));
        verify(client).delete(connection, "app", true, false, false, false);
    }

    @Test void restartAfterProviderDeletedVolumeRecordsDispositionBeforeDeletingApplication() {
        var disposable = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(disposable));
        context = context(application);
        when(inventory.volumesReclaimed(application)).thenReturn(false, true);
        when(client.listApplicationStorages(connection, "app")).thenReturn(List.of(), List.of());
        when(client.delete(connection, "app", true, false, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        var order = inOrder(inventory, client);
        order.verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
        order.verify(client).delete(connection, "app", true, false, false, false);
        verify(client, never()).deleteApplicationStorage(any(), anyString(), anyString());
    }

    @Test void recordedVolumeDispositionDoesNotBypassFreshInventoryBeforeApplicationDeletion() {
        var disposable = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(disposable));
        context = context(application);
        var regenerated = new CoolifyApplicationStorageSummary("storage-1", "persistent", "runtime-data",
            "/app/data", null, false, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(true);
        when(client.listApplicationStorages(connection, "app")).thenReturn(List.of(regenerated));

        assertThatThrownBy(() -> service.delete(context, application))
            .hasMessageContaining("PROVIDER_VOLUME_ABSENCE_UNPROVEN");

        verify(inventory).requireVolumeInventory(application, List.of(regenerated), true);
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    private ResourceGeneration application(List<ProviderVolumeGeneration> volumes) {
        return new ResourceGeneration("handle", "release", DeploymentProviderType.COOLIFY,
            "APPLICATION", "app", "server", "project", "environment", "destination", "runtime",
            "Managed by AI Fabric deployment deployment", "generation", "provider-generation", volumes, false);
    }

    private Context context(ResourceGeneration resource) {
        var snapshot = new Snapshot("customer", "tenant", "deployment", "target", "target-generation", 1,
            List.of(new ReleaseGeneration("release", "version", "generation")), List.of(resource), List.of());
        return new Context("operation", "operation-key", "fingerprint", snapshot, 2, "worker", "actor",
            "CUSTOMER_ADMIN", Instant.now().plusSeconds(30),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup-decision"));
    }
}
