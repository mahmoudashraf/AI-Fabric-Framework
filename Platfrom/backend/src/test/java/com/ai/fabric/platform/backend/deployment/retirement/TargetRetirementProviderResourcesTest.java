package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private final CoolifyHostVolumeReclamationService volumeReclamation = mock(CoolifyHostVolumeReclamationService.class);
    private final CoolifyRetirementOwnership ownershipProvider = mock(CoolifyRetirementOwnership.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private TargetRetirementProviderResources service;
    private Context context;
    private ResourceGeneration application;
    private CoolifyConnection connection;

    @BeforeEach void setup() throws Exception {
        service = new TargetRetirementProviderResources(inventory, profiles, resolver, client, volumeReclamation,
            List.of("46.224.145.148"), properties(), ownershipProvider);
        when(inventory.requireResource(any())).thenAnswer(call -> {
            ResourceGeneration resource = call.getArgument(0);
            var handle = new com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity();
            handle.setId(resource.handleId());
            handle.setProviderResourceUuid(resource.providerResourceUuid());
            return handle;
        });
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
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"host.docker.internal\"}"));
        application = application(List.of());
        context = context(application);
        var observed = new CoolifyApplicationSummary("app", "runtime", null, "running", null, null,
            mapper.readTree("{\"uuid\":\"app\",\"server_uuid\":\"server\"}"));
        when(client.getApplication(connection, "app")).thenReturn(Optional.of(observed));
        when(volumeReclamation.reclaimAndVerify(eq(TargetIntent.CUSTOMER_STAGING), eq("server"), anyString(), anyList()))
            .thenAnswer(call -> {
                @SuppressWarnings("unchecked")
                List<CoolifyHostVolumeReclamationService.Volume> volumes = call.getArgument(3, List.class);
                return new CoolifyHostVolumeReclamationService.Receipt("receipt", Instant.now(),
                    volumes.stream().filter(value -> !value.preserved()).map(CoolifyHostVolumeReclamationService.Volume::uuid)
                        .collect(java.util.stream.Collectors.toSet()));
            });
        when(volumeReclamation.verifyContainersAbsent(eq(TargetIntent.CUSTOMER_STAGING), eq("server"),
            anyString(), anyString(), anyString())).thenAnswer(call ->
                new CoolifyHostVolumeReclamationService.ContainerReceipt("container-receipt", Instant.now()));
    }

    @Test void deleteUsesExactUuidAndNoVolumeNetworkOrDockerCascadeOutsideTransaction() {
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });
        assertThat(service.delete(context, application).complete()).isTrue();
        var order = inOrder(inventory, client);
        order.verify(inventory).requireProviderIdentity(eq(application), any());
        order.verify(client).delete(connection, "app", true, true, false, false);
        verify(volumeReclamation).verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server",
            "handle", "application", "app");
        verify(client, never()).deleteDatabase(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void statelessApplicationCannotCompleteWhileAnOwnedContainerRemains() {
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });
        when(volumeReclamation.verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server",
            "handle", "application", "app"))
            .thenThrow(new TargetRetirementFailure("PROVIDER_CONTAINER_ABSENCE_UNPROVEN"));

        assertThatThrownBy(() -> service.delete(context, application))
            .hasMessageContaining("PROVIDER_CONTAINER_ABSENCE_UNPROVEN");
        verify(ownershipProvider, never()).finishRetirementDeletion(any());
    }

    @Test void missingCreationOwnershipBlocksAllProviderAndVolumeMutations() {
        doThrow(new IllegalStateException("MANAGED_RESOURCE_OWNERSHIP_REQUIRED"))
            .when(ownershipProvider).requireRetirementOwnership(any());
        assertThatThrownBy(() -> service.delete(context, application)).hasMessageContaining("MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(client, never()).deleteApplicationStorage(any(), anyString(), anyString());
        verify(ownershipProvider, never()).beginRetirementDeletion(any());
        verifyNoInteractions(volumeReclamation);
    }

    @Test void failedDeletionJournalPreventsRemoteDelete() {
        doThrow(new IllegalStateException("MANAGED_RESOURCE_DELETE_NOT_CLAIMED"))
            .when(ownershipProvider).beginRetirementDeletion(any());
        assertThatThrownBy(() -> service.delete(context, application)).hasMessageContaining("MANAGED_RESOURCE_DELETE_NOT_CLAIMED");
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(ownershipProvider, never()).finishRetirementDeletion(any());
    }

    @Test void ownedDeletionIsJournaledBeforeEffectAndCompletedAfterPhysicalAbsenceProof() {
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });
        assertThat(service.delete(context, application).complete()).isTrue();
        var order = inOrder(ownershipProvider, client, volumeReclamation);
        order.verify(ownershipProvider).requireRetirementOwnership(any());
        order.verify(ownershipProvider).beginRetirementDeletion(any());
        order.verify(client).delete(connection, "app", true, true, false, false);
        order.verify(volumeReclamation).verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING, "server", "handle", "application", "app");
        order.verify(ownershipProvider).finishRetirementDeletion(argThat(handle -> "handle".equals(handle.getId())));
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
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"production.internal\"}"));
        assertThatThrownBy(() -> service.delete(context, application)).hasMessageContaining("STAGING_PLACEMENT_NOT_APPROVED");
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(client, never()).getApplication(any(), anyString());
    }

    @Test void missingPlacementAndProviderReadbackAreNotProofOfAbsence() throws Exception {
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\"}"));
        assertThatThrownBy(() -> service.readback(context, application)).hasMessageContaining("STAGING_PLACEMENT_NOT_APPROVED");
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"host.docker.internal\"}"));
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

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_AI_STATE_STORAGE_BINDING"})
    void customerSourcesAndUnsupportedProvidersAreNeverLifecycleResources(String resourceKind) {
        var source = new ResourceGeneration("source", null, DeploymentProviderType.COOLIFY, resourceKind,
            "customer-bucket", null, "generation", true);
        assertThatThrownBy(() -> service.delete(context, source)).hasMessageContaining("PROVIDER_RESOURCE_UNSUPPORTED");
        assertThatThrownBy(() -> service.readback(context, source)).hasMessageContaining("PROVIDER_RESOURCE_UNSUPPORTED");
        verifyNoInteractions(client, resolver);
    }

    @Test void exclusivelyOwnedDatabaseVolumeIsDeletedWithTheDatabase() throws Exception {
        var volume = new ProviderVolumeGeneration("database-storage", "persistent", "database-volume",
            "/var/lib/postgresql/data", null, "volume-generation", false);
        var database = new ResourceGeneration("database", "release", DeploymentProviderType.COOLIFY, "RUNTIME_POSTGRES_DATABASE",
            "db", "server", "project", "environment", "destination", "database",
            "Durable runtime chat database for AI Fabric deployment deployment",
            "generation", "provider-generation", List.of(volume), false);
        context = context(database);
        when(client.getDatabase(connection, "db")).thenReturn(Optional.of(new CoolifyDatabaseSummary("db", "database", "running", "postgresql",
            null, null, mapper.readTree("{\"uuid\":\"db\",\"server_uuid\":\"server\"}"))));
        when(client.listDatabaseStorages(connection, "db")).thenReturn(List.of(new CoolifyApplicationStorageSummary(
            "database-storage", "persistent", "database-volume", "/var/lib/postgresql/data", null, false,
            mapper.createObjectNode())));
        when(client.deleteDatabase(connection, "db", true, true, false, false)).thenAnswer(call -> {
            when(client.getDatabase(connection, "db")).thenReturn(Optional.empty());
            return null;
        });
        assertThat(service.delete(context, database).complete()).isTrue();
        verify(inventory).requireVolumeInventory(eq(database), anyList(), eq(false));
        verify(inventory).recordVolumeDeletionIntent(eq(database), any(Instant.class));
        verify(inventory).recordVolumesReclaimed(eq(database), any(Instant.class));
        verify(client).deleteDatabase(connection, "db", true, true, false, false);
    }

    @Test void databaseDeletionFailsClosedWithoutFrozenVolumeOwnership() throws Exception {
        var database = new ResourceGeneration("database", "release", DeploymentProviderType.COOLIFY, "RUNTIME_POSTGRES_DATABASE",
            "db", "server", "project", "environment", "destination", "database",
            "Durable runtime chat database for AI Fabric deployment deployment",
            "generation", "provider-generation", List.of(), false);
        context = context(database);
        when(client.getDatabase(connection, "db")).thenReturn(Optional.of(new CoolifyDatabaseSummary("db", "database", "running", "postgresql",
            null, null, mapper.readTree("{\"uuid\":\"db\",\"server_uuid\":\"server\"}"))));

        assertThatThrownBy(() -> service.delete(context, database))
            .hasMessageContaining("DATABASE_VOLUME_OWNERSHIP_UNPROVEN");

        verify(client, never()).deleteDatabase(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void databaseProviderFailureNeverRecordsVolumeReclamation() throws Exception {
        var volume = new ProviderVolumeGeneration("database-storage", "persistent", "database-volume",
            "/var/lib/postgresql/data", null, "volume-generation", false);
        var database = new ResourceGeneration("database", "release", DeploymentProviderType.COOLIFY, "RUNTIME_POSTGRES_DATABASE",
            "db", "server", "project", "environment", "destination", "database",
            "Durable runtime chat database for AI Fabric deployment deployment",
            "generation", "provider-generation", List.of(volume), false);
        context = context(database);
        when(client.getDatabase(connection, "db")).thenReturn(Optional.of(new CoolifyDatabaseSummary("db", "database", "running", "postgresql",
            null, null, mapper.readTree("{\"uuid\":\"db\",\"server_uuid\":\"server\"}"))));
        when(client.listDatabaseStorages(connection, "db")).thenReturn(List.of(new CoolifyApplicationStorageSummary(
            "database-storage", "persistent", "database-volume", "/var/lib/postgresql/data", null, false,
            mapper.createObjectNode())));
        when(client.deleteDatabase(connection, "db", true, true, false, false))
            .thenThrow(new IllegalStateException("provider timeout"));

        assertThatThrownBy(() -> service.delete(context, database)).hasMessageContaining("provider timeout");

        verify(inventory, never()).recordVolumesReclaimed(any(), any());
    }

    @Test void absenceIsReadBackBeforeDeleteAndEmptyAllowlistFailsClosed() {
        when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
        assertThat(service.delete(context, application).complete()).isTrue();
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        var unconfigured = new TargetRetirementProviderResources(inventory, profiles, resolver, client,
            volumeReclamation, List.of(), properties(), ownershipProvider);
        assertThatThrownBy(() -> unconfigured.readback(context, application)).hasMessageContaining("STAGING_PLACEMENT_NOT_APPROVED");
    }

    @Test void persistentVolumeIsDeletedWithApplicationAfterDurableIntent() {
        var expectedVolume = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(expectedVolume));
        context = context(application);
        var observedVolume = new CoolifyApplicationStorageSummary("storage-1", "persistent", "runtime-data",
            "/app/data", null, false, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(false);
        when(client.listApplicationStorages(connection, "app"))
            .thenReturn(List.of(observedVolume), List.of(observedVolume));
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        var order = inOrder(client, inventory);
        order.verify(inventory).requireVolumeInventory(application, List.of(observedVolume), false);
        order.verify(inventory).recordVolumeDeletionIntent(eq(application), any(Instant.class));
        order.verify(inventory).requireApplicationDeletionVolumeInventory(application, List.of(observedVolume));
        order.verify(client).delete(connection, "app", true, true, false, false);
        order.verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
        verify(client, never()).deleteApplicationStorage(any(), anyString(), anyString());
    }

    @Test void absentApplicationWithoutFrozenVolumeDispositionFailsClosed() {
        var expectedVolume = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(expectedVolume));
        context = context(application);
        when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(false);

        assertThatThrownBy(() -> service.readback(context, application))
            .hasMessageContaining("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
    }

    @Test void approvedRetainedVolumeIsDetachedBeforeDisposableVolumeDeletion() {
        var retained = new ProviderVolumeGeneration("storage-retained", "persistent", "documents",
            "/app/document-sources", "/srv/loomai/documents", "retained-generation", true);
        application = application(List.of(retained));
        context = context(application);
        var observed = new CoolifyApplicationStorageSummary("storage-retained", "persistent", "documents",
            "/app/document-sources", "/srv/loomai/documents", true, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(false);
        when(client.listApplicationStorages(connection, "app"))
            .thenReturn(List.of(observed), List.of());
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        verify(client).deleteApplicationStorage(connection, "app", "storage-retained");
        verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
        var order = inOrder(inventory, client);
        order.verify(inventory).requireVolumeInventory(application, List.of(observed), false);
        order.verify(inventory).recordVolumeDeletionIntent(eq(application), any(Instant.class));
        order.verify(client).deleteApplicationStorage(connection, "app", "storage-retained");
        verify(inventory).requireApplicationDeletionVolumeInventory(application, List.of());
        verify(client).delete(connection, "app", true, true, false, false);
    }

    @Test void restartAfterAcceptedApplicationDeleteFinalizesDispositionFromIntent() {
        var disposable = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(disposable));
        context = context(application);
        when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(true);

        assertThat(service.delete(context, application).complete()).isTrue();

        verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void providerAbsenceWithoutIndependentHostProofNeverRecordsReclamation() {
        var disposable = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(disposable));
        context = context(application);
        when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(true);
        when(volumeReclamation.reclaimAndVerify(any(), anyString(), anyString(), anyList()))
            .thenThrow(new TargetRetirementFailure("PROVIDER_HOST_VOLUME_READBACK_FAILED"));

        assertThatThrownBy(() -> service.readback(context, application))
            .hasMessageContaining("PROVIDER_HOST_VOLUME_READBACK_FAILED");

        verify(inventory, never()).recordVolumesReclaimed(any(), any());
    }

    @Test void recordedVolumeDispositionWithPresentApplicationFailsClosed() {
        var disposable = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(disposable));
        context = context(application);
        when(inventory.volumesReclaimed(application)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(context, application))
            .hasMessageContaining("PROVIDER_VOLUME_ABSENCE_UNPROVEN");

        verify(inventory, never()).requireVolumeInventory(any(), anyList(), anyBoolean());
        verify(client, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void preparedIntentRetriesApplicationDeleteWithoutDetachingDisposableVolumes() {
        var disposable = new ProviderVolumeGeneration("storage-1", "persistent", "runtime-data",
            "/app/data", null, "volume-generation", false);
        application = application(List.of(disposable));
        context = context(application);
        var observed = new CoolifyApplicationStorageSummary("storage-1", "persistent", "runtime-data",
            "/app/data", null, false, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(true);
        when(client.listApplicationStorages(connection, "app")).thenReturn(List.of(observed));
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        verify(inventory).requireVolumeDeletionProgress(application, List.of(observed));
        verify(inventory, never()).recordVolumeDeletionIntent(any(), any());
        verify(client, never()).deleteApplicationStorage(any(), anyString(), anyString());
        verify(client).delete(connection, "app", true, true, false, false);
        verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
    }

    @Test void preparedIntentResumesAfterRetainedVolumeWasAlreadyDetached() {
        var retained = new ProviderVolumeGeneration("storage-retained", "persistent", "documents",
            "/app/document-sources", "/srv/loomai/documents", "retained-generation", true);
        var disposable = new ProviderVolumeGeneration("storage-runtime", "persistent", "runtime-data",
            "/app/data", null, "runtime-generation", false);
        application = application(List.of(retained, disposable));
        context = context(application);
        var observedDisposable = new CoolifyApplicationStorageSummary("storage-runtime", "persistent", "runtime-data",
            "/app/data", null, false, mapper.createObjectNode());
        when(inventory.volumesReclaimed(application)).thenReturn(false);
        when(inventory.volumeDeletionIntentRecorded(application)).thenReturn(true);
        when(client.listApplicationStorages(connection, "app")).thenReturn(List.of(observedDisposable));
        when(client.delete(connection, "app", true, true, false, false)).thenAnswer(call -> {
            when(client.getApplication(connection, "app")).thenReturn(Optional.empty());
            return null;
        });

        assertThat(service.delete(context, application).complete()).isTrue();

        verify(inventory).requireVolumeDeletionProgress(application, List.of(observedDisposable));
        verify(client, never()).deleteApplicationStorage(any(), anyString(), anyString());
        verify(inventory).requireApplicationDeletionVolumeInventory(application, List.of(observedDisposable));
        verify(inventory).recordVolumesReclaimed(eq(application), any(Instant.class));
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

    private static CustomerSelfServiceProperties properties() {
        return new CustomerSelfServiceProperties(java.util.Map.of(
            TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer(
                "server", "46.224.145.148", "host.docker.internal"),
            TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer(
                "production-server", "46.225.162.106", "production.internal")
        ), 300);
    }
}
