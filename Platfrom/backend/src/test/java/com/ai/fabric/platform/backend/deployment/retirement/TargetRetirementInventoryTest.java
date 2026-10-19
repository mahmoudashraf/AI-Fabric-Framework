package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerBindingHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetRetirementInventoryTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final PlatformConsumerBindingHistoryRepository bindingHistory = mock(PlatformConsumerBindingHistoryRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient client = mock(CoolifyApiClient.class);
    private final TargetRetirementJson json = new TargetRetirementJson(
        new ObjectMapper().registerModule(new JavaTimeModule()));
    private TargetRetirementInventory service;
    private PlanRequest request;
    private DeploymentProviderResourceHandleEntity handle;
    private DeploymentReleaseEntity release;
    private PlatformConsumerEntity consumer;
    private DeploymentTargetProfileEntity target;

    @BeforeEach void setup() {
        service = new TargetRetirementInventory(deployments, targets, releases, handles, consumers, bindingHistory,
            json, resolver, client, List.of());
        var deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        target = new DeploymentTargetProfileEntity();
        target.setId("staging"); target.setEnvironmentName("staging"); target.setProviderType(DeploymentProviderType.COOLIFY); target.setActive(true);
        target.setProviderConfigJson("{\"serverUuid\":\"staging-server\",\"destinationUuid\":\"destination\"}"); target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{}");
        when(targets.findById("staging")).thenReturn(Optional.of(target));
        var config = mock(CoolifyTargetProfileConfig.class);
        when(config.serverUuid()).thenReturn("staging-server");
        when(config.destinationUuid()).thenReturn("destination");
        var connection = new CoolifyConnection("https://coolify.test", "private", config);
        when(resolver.requireConnection(target)).thenReturn(connection);
        when(client.listApplicationStorages(connection, "staging-app")).thenReturn(List.of());
        var rawApplication = new ObjectMapper().createObjectNode();
        rawApplication.put("uuid", "staging-app"); rawApplication.put("name", "runtime");
        rawApplication.put("description", "Managed by AI Fabric deployment deployment");
        rawApplication.put("server_uuid", "staging-server"); rawApplication.put("project_uuid", "project");
        rawApplication.put("environment_uuid", "environment"); rawApplication.put("destination_uuid", "destination");
        when(client.getApplication(connection, "staging-app")).thenReturn(Optional.of(new CoolifyApplicationSummary(
            "staging-app", "runtime", null, "running:healthy", null, null, rawApplication)));
        release = new DeploymentReleaseEntity();
        release.setId("staging-release"); release.setDeploymentId("deployment"); release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version"); release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProviderResourceHandleId("handle"); release.setProvisioningDetailsJson("{}"); release.setCreatedAt(Instant.now());
        when(releases.findById("staging-release")).thenReturn(Optional.of(release));
        when(releases.findAll()).thenReturn(List.of(release));
        handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("handle"); handle.setDeploymentId("deployment"); handle.setTargetProfileId("staging"); handle.setReleaseId("staging-release");
        handle.setResourceKind("APPLICATION"); handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setProviderResourceUuid("staging-app"); handle.setProviderServerUuid("staging-server");
        handle.setProviderProjectUuid("project"); handle.setProviderEnvironmentUuid("environment"); handle.setCreatedAt(Instant.now());
        handle.setStatus("ACTIVE"); handle.setMetadataJson("{\"applicationName\":\"runtime\"}");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging")).thenReturn(List.of(handle));
        when(handles.findById("handle")).thenReturn(Optional.of(handle));
        when(handles.findAll()).thenReturn(List.of(handle));
        consumer = new PlatformConsumerEntity();
        consumer.setId("consumer"); consumer.setCustomerId("customer"); consumer.setBindingRevision(7);
        consumer.setBoundDeploymentId("deployment"); consumer.setBoundReleaseId("production-release"); consumer.setBoundTargetProfileId("production");
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        when(consumers.findAll()).thenReturn(List.of(consumer));
        request = new PlanRequest("key", List.of("staging-release"), List.of(new ConsumerExpectation("consumer", 7)), Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup-policy"), "post-promotion");
    }

    @Test void productionAssignmentIsPreservedWhileItsRevisionIsChecked() {
        var snapshot = capture();
        assertThat(snapshot.resources()).hasSize(1);
        assertThat(consumer.getBoundReleaseId()).isEqualTo("production-release");
        service.recheck(snapshot, request);
        consumer.setBindingRevision(8);
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("CONSUMER_REVISION_CHANGED");
        verify(consumers, never()).save(any());
        verify(releases, never()).delete(any());
    }
    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_AI_STATE_STORAGE_BINDING"})
    void externalBindingsAreFrozenButNeverObservedDeletedOrCountedAsOwned(String resourceKind) {
        var binding = externalBinding(resourceKind);
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging"))
            .thenReturn(List.of(handle, binding));
        when(handles.findById(binding.getId())).thenReturn(Optional.of(binding));
        when(handles.findAll()).thenReturn(List.of(handle, binding));

        var snapshot = capture();
        var retained = snapshot.resources().stream().filter(ResourceGeneration::preserved).toList();
        assertThat(retained).singleElement().satisfies(resource -> {
            assertThat(resource.handleId()).isEqualTo(binding.getId());
            assertThat(resource.volumes()).isEmpty();
            assertThat(resource.providerIdentityFingerprint()).isNull();
            assertThatThrownBy(() -> service.recordReclaimed(resource, Instant.now()))
                .hasMessageContaining("CUSTOMER_SOURCE_PRESERVED");
        });
        assertThat(snapshot.resources().stream().filter(resource -> !resource.preserved()).map(ResourceGeneration::handleId))
            .containsExactly(handle.getId());
        service.recheck(snapshot, request);
        verify(client, never()).getApplication(any(), eq("external-resource"));
        verify(client, never()).getDatabase(any(), eq("external-resource"));
        verify(handles, never()).save(binding);

        binding.setMetadataJson("{\"bindingFingerprint\":\"changed\"}");
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging"))
            .thenReturn(List.of(binding));
        assertThatThrownBy(this::capture).hasMessageContaining("RESOURCE_INVENTORY_EMPTY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_OTHER_STORAGE_BINDING", "external_vector_storage_binding",
        "EXTERNAL_VECTOR_STORAGE_BINDING ", "EXTERNAL_VECTOR_STORAGE_BINDING_V2"})
    void unknownBindingKindsCannotBeRelabeledAsPreserved(String resourceKind) {
        var binding = externalBinding(resourceKind);
        binding.setReleaseId(release.getId());
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging"))
            .thenReturn(List.of(handle, binding));
        assertThatThrownBy(this::capture).hasMessageContaining("RESOURCE_INVENTORY_UNSUPPORTED");
    }
    @Test void frozenTargetAssignmentCanDrainButAmbiguousAssignmentBlocksRetirement() {
        consumer.setBoundTargetProfileId("staging");
        assertThat(capture().consumers()).containsExactly(new ConsumerExpectation("consumer", 7));
        consumer.setBoundTargetProfileId(null);
        assertThatThrownBy(this::capture).hasMessageContaining("TARGET_STILL_ASSIGNED");
    }
    @Test void crossTargetReleasePointerAndSharedResourceAliasAreRejected() {
        var production = new DeploymentReleaseEntity();
        production.setId("production-release"); production.setProviderResourceHandleId("handle");
        when(releases.findAll()).thenReturn(List.of(release, production));
        assertThatThrownBy(this::capture).hasMessageContaining("SHARED_RELEASE_REFERENCE");
        when(releases.findAll()).thenReturn(List.of(release));
        var alias = new DeploymentProviderResourceHandleEntity();
        alias.setId("shared"); alias.setProviderType(DeploymentProviderType.COOLIFY); alias.setProviderResourceUuid("staging-app");
        when(handles.findAll()).thenReturn(List.of(handle, alias));
        assertThatThrownBy(this::capture).hasMessageContaining("SHARED_RESOURCE_REFERENCE");
    }
    @Test void replacementOrMetadataBindingChangeInvalidatesSnapshotButObservationsDoNot() {
        var snapshot = capture();
        handle.setUpdatedAt(Instant.now()); handle.setLastObservedStatus("running");
        service.recheck(snapshot, request);
        handle.setCreatedAt(handle.getCreatedAt().plusSeconds(1));
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
        assertThatThrownBy(() -> service.requireResource(snapshot.resources().get(0))).hasMessageContaining("RESOURCE_GENERATION_CHANGED");
    }
    @Test void releaseGenerationAndProfilePlacementChangeInvalidateSnapshot() {
        var snapshot = capture();
        release.setDeploymentVersionId("replacement-version");
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
        release.setDeploymentVersionId("version");
        target.setProviderConfigJson("{\"serverUuid\":\"production-server\"}");
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
    }
    @Test void liveProviderOwnershipOrGenerationDriftInvalidatesFrozenSnapshot() {
        var snapshot = capture();
        var connection = resolver.requireConnection(target);
        var drifted = new ObjectMapper().createObjectNode();
        drifted.put("uuid", "staging-app"); drifted.put("name", "runtime");
        drifted.put("description", "Managed by AI Fabric deployment deployment");
        drifted.put("server_uuid", "staging-server"); drifted.put("project_uuid", "other-project");
        drifted.put("environment_uuid", "environment"); drifted.put("destination_uuid", "destination");
        when(client.getApplication(connection, "staging-app")).thenReturn(Optional.of(new CoolifyApplicationSummary(
            "staging-app", "runtime", null, "running:healthy", null, null, drifted)));

        assertThatThrownBy(() -> service.recheck(snapshot, request))
            .hasMessageContaining("PROVIDER_RESOURCE_OWNERSHIP_CHANGED");
    }
    @Test void unownedOrUnenumeratedConsumersAndForeignResourceReleaseFailClosed() {
        consumer.setCustomerId("foreign");
        assertThatThrownBy(this::capture).hasMessageContaining("CONSUMER_REVISION_CHANGED");
        consumer.setCustomerId("customer");
        var unknown = new PlatformConsumerEntity(); unknown.setId("unknown"); unknown.setBoundDeploymentId("deployment");
        unknown.setBoundTargetProfileId("production");
        when(consumers.findAll()).thenReturn(List.of(consumer, unknown));
        assertThatThrownBy(this::capture).hasMessageContaining("CONSUMER_EXPECTATION_MISSING");
        when(consumers.findAll()).thenReturn(List.of(consumer));
        handle.setReleaseId("foreign-release");
        assertThatThrownBy(this::capture).hasMessageContaining("RESOURCE_INVENTORY_UNSUPPORTED");
    }
    @Test void productionTargetAndEmptyResourceInventoryAreNeverRetirementSuccess() {
        target.setEnvironmentName("production");
        assertThatThrownBy(this::capture).hasMessageContaining("STAGING_TARGET_REQUIRED");
        target.setEnvironmentName("staging");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging")).thenReturn(List.of());
        assertThatThrownBy(this::capture).hasMessageContaining("RESOURCE_INVENTORY_EMPTY");
    }
    @Test void disposableVolumeAbsenceIsResumableAndRequiresADurableReceipt() {
        var connection = resolver.requireConnection(target);
        var volume = new CoolifyApplicationStorageSummary("storage-1", "persistent", "runtime-data",
            "/app/data", null, false, new ObjectMapper().createObjectNode());
        when(client.listApplicationStorages(connection, "staging-app")).thenReturn(List.of(volume));
        var snapshot = capture();
        assertThat(snapshot.resources().getFirst().volumes()).singleElement()
            .satisfies(value -> assertThat(value.preserved()).isFalse());

        when(client.listApplicationStorages(connection, "staging-app")).thenReturn(List.of());
        service.recheck(snapshot, request);
        assertThat(service.volumesReclaimed(snapshot.resources().getFirst())).isFalse();
        assertThatThrownBy(() -> service.recordVolumesReclaimed(snapshot.resources().getFirst(), Instant.now()))
            .hasMessageContaining("PROVIDER_VOLUME_DELETE_INTENT_REQUIRED");
        service.recordVolumeDeletionIntent(snapshot.resources().getFirst(), Instant.now());
        assertThat(service.volumeDeletionIntentRecorded(snapshot.resources().getFirst())).isTrue();
        assertThat(service.volumesReclaimed(snapshot.resources().getFirst())).isFalse();
        assertThatThrownBy(() -> service.recordReclaimed(snapshot.resources().getFirst(), Instant.now()))
            .hasMessageContaining("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
        assertThat(handle.getStatus()).isEqualTo("ACTIVE");
        service.recordVolumesReclaimed(snapshot.resources().getFirst(), Instant.now());
        assertThat(service.volumesReclaimed(snapshot.resources().getFirst())).isTrue();
        service.recordReclaimed(snapshot.resources().getFirst(), Instant.now());
        assertThat(handle.getStatus()).isEqualTo("DELETED");
        assertThat(handle.getLastObservedStatus())
            .isEqualTo(service.volumeDispositionReceipt(snapshot.resources().getFirst()));
        assertThat(service.volumesReclaimed(snapshot.resources().getFirst())).isTrue();
    }

    @Test void applicationDeleteInventoryRequiresDisposableAttachedAndRetainedDetached() {
        var connection = resolver.requireConnection(target);
        var disposable = new CoolifyApplicationStorageSummary("storage-1", "persistent", "runtime-data",
            "/app/data", null, false, new ObjectMapper().createObjectNode());
        when(client.listApplicationStorages(connection, "staging-app")).thenReturn(List.of(disposable));
        var resource = capture().resources().getFirst();

        service.requireApplicationDeletionVolumeInventory(resource, List.of(disposable));
        assertThatThrownBy(() -> service.requireApplicationDeletionVolumeInventory(resource, List.of()))
            .hasMessageContaining("PERSISTENT_VOLUME_GENERATION_CHANGED");
    }
    @Test void hostBackedVolumeRequiresExplicitApprovedRetention() {
        var connection = resolver.requireConnection(target);
        var mounted = new CoolifyApplicationStorageSummary("storage-1", "persistent", "documents",
            "/app/document-sources", "/srv/loomai/documents", true, new ObjectMapper().createObjectNode());
        when(client.listApplicationStorages(connection, "staging-app")).thenReturn(List.of(mounted));
        assertThatThrownBy(this::capture).hasMessageContaining("PERSISTENT_VOLUME_RETENTION_UNAPPROVED");

        service = new TargetRetirementInventory(deployments, targets, releases, handles, consumers, bindingHistory,
            json, resolver, client, List.of("/app/document-sources"));
        var resource = capture().resources().getFirst();
        assertThat(resource.volumes()).singleElement()
            .satisfies(value -> assertThat(value.preserved()).isTrue());
        service.requireApplicationDeletionVolumeInventory(resource, List.of());
        assertThatThrownBy(() -> service.requireApplicationDeletionVolumeInventory(resource, List.of(mounted)))
            .hasMessageContaining("PERSISTENT_VOLUME_RETENTION_UNPROVEN");
    }

    @Test void runtimeDatabaseRequiresAnExclusiveProviderObservedVolume() {
        var connection = resolver.requireConnection(target);
        handle.setResourceKind("RUNTIME_POSTGRES_DATABASE");
        handle.setProviderResourceUuid("staging-db");
        handle.setMetadataJson("{\"databaseName\":\"runtime-db\"}");
        var rawDatabase = new ObjectMapper().createObjectNode();
        rawDatabase.put("uuid", "staging-db"); rawDatabase.put("name", "runtime-db");
        rawDatabase.put("description", "Durable runtime chat database for AI Fabric deployment deployment");
        rawDatabase.put("server_uuid", "staging-server"); rawDatabase.put("project_uuid", "project");
        rawDatabase.put("environment_uuid", "environment"); rawDatabase.put("destination_uuid", "destination");
        rawDatabase.put("type", "postgresql"); rawDatabase.put("postgres_db", "runtime");
        rawDatabase.put("postgres_user", "runtime_user");
        when(client.getDatabase(connection, "staging-db")).thenReturn(Optional.of(new CoolifyDatabaseSummary(
            "staging-db", "runtime-db", "running", "postgresql", "runtime_user", "runtime", rawDatabase)));
        var databaseVolume = new CoolifyApplicationStorageSummary("database-storage", "persistent", "runtime-db-volume",
            "/var/lib/postgresql/data", null, false, new ObjectMapper().createObjectNode());
        when(client.listDatabaseStorages(connection, "staging-db")).thenReturn(List.of(databaseVolume));

        assertThat(capture().resources().getFirst().volumes()).singleElement()
            .satisfies(volume -> {
                assertThat(volume.storageUuid()).isEqualTo("database-storage");
                assertThat(volume.preserved()).isFalse();
            });

        when(client.listDatabaseStorages(connection, "staging-db")).thenReturn(List.of());
        assertThatThrownBy(this::capture).hasMessageContaining("DATABASE_VOLUME_OWNERSHIP_UNPROVEN");
    }

    @Test void runtimeDatabaseVolumeSharedByAnotherManagedResourceIsRejected() {
        var connection = resolver.requireConnection(target);
        handle.setResourceKind("RUNTIME_POSTGRES_DATABASE");
        handle.setProviderResourceUuid("staging-db");
        handle.setMetadataJson("{\"databaseName\":\"runtime-db\"}");
        var rawDatabase = new ObjectMapper().createObjectNode();
        rawDatabase.put("uuid", "staging-db"); rawDatabase.put("name", "runtime-db");
        rawDatabase.put("description", "Durable runtime chat database for AI Fabric deployment deployment");
        rawDatabase.put("server_uuid", "staging-server"); rawDatabase.put("project_uuid", "project");
        rawDatabase.put("environment_uuid", "environment"); rawDatabase.put("destination_uuid", "destination");
        when(client.getDatabase(connection, "staging-db")).thenReturn(Optional.of(new CoolifyDatabaseSummary(
            "staging-db", "runtime-db", "running", "postgresql", "runtime_user", "runtime", rawDatabase)));
        var databaseVolume = new CoolifyApplicationStorageSummary("database-storage", "persistent", "shared-volume",
            "/var/lib/postgresql/data", null, false, new ObjectMapper().createObjectNode());
        when(client.listDatabaseStorages(connection, "staging-db")).thenReturn(List.of(databaseVolume));
        var other = new DeploymentProviderResourceHandleEntity();
        other.setId("other"); other.setProviderType(DeploymentProviderType.COOLIFY);
        other.setResourceKind("APPLICATION"); other.setProviderResourceUuid("other-app");
        other.setProviderServerUuid("staging-server"); other.setTargetProfileId("staging");
        when(handles.findAll()).thenReturn(List.of(handle, other));
        when(client.listApplicationStorages(connection, "other-app")).thenReturn(List.of(
            new CoolifyApplicationStorageSummary("other-storage", "persistent", "shared-volume", "/data",
                null, false, new ObjectMapper().createObjectNode())));

        assertThatThrownBy(this::capture).hasMessageContaining("SHARED_PERSISTENT_VOLUME_REFERENCE");
    }
    private DeploymentProviderResourceHandleEntity externalBinding(String resourceKind) {
        var binding = new DeploymentProviderResourceHandleEntity();
        binding.setId("external-binding"); binding.setDeploymentId("deployment"); binding.setTargetProfileId("staging");
        binding.setResourceKind(resourceKind); binding.setProviderType(DeploymentProviderType.COOLIFY);
        binding.setProviderResourceUuid("external-resource"); binding.setCreatedAt(Instant.now());
        binding.setStatus("CONFIGURED"); binding.setMetadataJson("{\"bindingFingerprint\":\"original\"}");
        return binding;
    }

    private Snapshot capture() { return service.capture("deployment", "staging", request, 0); }
}
