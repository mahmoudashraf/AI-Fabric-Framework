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
        when(config.serverUuid()).thenReturn("server");
        connection = new CoolifyConnection("https://coolify.test", "private-token", config);
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(client.getServer(connection, "server")).thenReturn(mapper.readTree("{\"uuid\":\"server\",\"ip\":\"46.224.145.148\"}"));
        application = new ResourceGeneration("handle", "release", DeploymentProviderType.COOLIFY, "APPLICATION", "app", "server", "generation", false);
        var snapshot = new Snapshot("customer", "tenant", "deployment", "target", "target-generation", 1,
            List.of(new ReleaseGeneration("release", "version", "generation")), List.of(application), List.of());
        context = new Context("operation", "fingerprint", snapshot, 2, "worker", "actor", "CUSTOMER_ADMIN", Instant.now().plusSeconds(30),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup-decision"));
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
        verify(client).delete(connection, "app", true, false, false, false);
        verify(client, never()).deleteDatabase(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
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
        assertThatThrownBy(() -> service.delete(context, application)).hasMessageContaining("PROVIDER_RESOURCE_PLACEMENT_UNPROVEN");
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
            "db", "server", "generation", false);
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
}
