package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementInventory;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementFailure;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementStore;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.EnumMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProductionAdmissionResourceReadbackSourceTest {
    @Test
    void incompleteR3OperationCannotProduceReleaseEvidence() {
        AdmissionStore admissions = mock(AdmissionStore.class);
        DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
        TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
        TargetRetirementStore retirements = mock(TargetRetirementStore.class);
        AdmissionJson json = new AdmissionJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        var source = new ProductionAdmissionResourceReadbackSource(admissions, targets,
            mock(DeploymentReleaseRepository.class), mock(DeploymentVersionRepository.class),
            mock(DeploymentProviderResourceHandleRepository.class),
            mock(PlatformConsumerRepository.class), operations, retirements,
            mock(TargetRetirementInventory.class),
            mock(CoolifyTargetProfileResolver.class), mock(CoolifyApiClient.class), properties(), json,
            Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));
        Reservation reservation = reservation();
        FleetPolicy policy = policy();
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId(AdmissionFixture.TARGET);
        profile.setActive(true);
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setProviderConfigJson("{\"serverUuid\":\"zf25hgk9694bt7q0zwb98ado\",\"destinationUuid\":\"xjhfu65nacrr30xax5cp0ry7\"}");
        when(targets.findById(AdmissionFixture.TARGET)).thenReturn(Optional.of(profile));
        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement-1");
        when(operations.findById(operation.getId())).thenReturn(Optional.of(operation));
        TargetRetirementContracts.Summary summary = mock(TargetRetirementContracts.Summary.class);
        when(summary.operationKey()).thenReturn("admission-expiry:" + reservation.id() + ":" + reservation.generation());
        when(summary.state()).thenReturn(TargetRetirementContracts.State.QUEUED);
        when(retirements.summary(operation)).thenReturn(summary);

        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy, reservation.workerToken())))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RETIREMENT_OUTCOME_UNSETTLED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_AI_STATE_STORAGE_BINDING"})
    void activeReadbackRequiresFreshExactCoolifyResourceEvidence(String retainedKind) throws Exception {
        AdmissionStore admissions = mock(AdmissionStore.class);
        DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
        DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
        DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
        PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient client = mock(CoolifyApiClient.class);
        AdmissionJson json = new AdmissionJson(new ObjectMapper().findAndRegisterModules());
        var source = new ProductionAdmissionResourceReadbackSource(admissions, targets, releases, versions,
            handles, consumers, mock(TargetRetirementOperationRepository.class), mock(TargetRetirementStore.class),
            mock(TargetRetirementInventory.class),
            resolver, client, properties(), json, Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));
        Manifest manifest = new Manifest("manifest1", "deployment", "version", "config-hash",
            AdmissionFixture.TARGET, "b".repeat(64), "topology", AdmissionFixture.vector(1), Set.of());
        Reservation reservation = activeReservation(manifest, json);
        when(admissions.reviewed("MANIFEST", "manifest1", Manifest.class)).thenReturn(manifest);

        var profile = profile();
        when(targets.findById(AdmissionFixture.TARGET)).thenReturn(Optional.of(profile));
        var version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setConfigHash("config-hash");
        version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId(AdmissionFixture.TARGET);
        release.setDeploymentVersionId("version"); release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningStatus("ACTIVE"); release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setRuntimeAuthorityFingerprint("a".repeat(64));
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(release));
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("runtime-handle"); handle.setDeploymentId("deployment"); handle.setTargetProfileId(AdmissionFixture.TARGET);
        handle.setReleaseId("release"); handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind("APPLICATION"); handle.setProviderResourceUuid("runtime-app");
        handle.setProviderServerUuid(AdmissionFixture.STAGING.serverUuid()); handle.setStatus("ACTIVE");
        var retained = new DeploymentProviderResourceHandleEntity();
        retained.setId("retained-binding"); retained.setResourceKind(retainedKind);
        retained.setProviderResourceUuid("external-resource"); retained.setStatus("CONFIGURED");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(handle, retained));
        var config = new CoolifyTargetProfileConfig("https://coolify.test", "project", "staging", "environment",
            AdmissionFixture.STAGING.serverUuid(), AdmissionFixture.STAGING.destinationUuid(), "example.test", "4.0.0",
            5, 600, false, true, "8097", "/actuator/health", "8097");
        var connection = new CoolifyConnection("https://coolify.test", "private", config);
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(client.getServer(connection, AdmissionFixture.STAGING.serverUuid())).thenReturn(new ObjectMapper().readTree(
            "{\"uuid\":\"" + AdmissionFixture.STAGING.serverUuid() + "\",\"ip\":\"host.docker.internal\"}"
        ));
        when(client.getApplication(connection, "runtime-app")).thenReturn(Optional.of(new CoolifyApplicationSummary(
            "runtime-app", "runtime", null, "running:healthy", null, null,
            new ObjectMapper().readTree("{\"uuid\":\"runtime-app\",\"server_uuid\":\"" + AdmissionFixture.STAGING.serverUuid() + "\"}")
        )));
        when(consumers.findAll()).thenReturn(List.of());

        var readback = source.read(new ReadbackContext(reservation, policy(), "worker-1"));
        assertThat(readback.inventoryComplete()).isTrue();
        assertThat(readback.evidenceReference()).startsWith("admission-active:");
        assertThat(readback.resourcesAbsent()).isFalse();
        verify(client, never()).getApplication(connection, "external-resource");
        verify(client, never()).getDatabase(connection, "external-resource");

        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(retained));
        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("ACTIVE_INVENTORY_UNPROVEN");

        retained.setResourceKind("EXTERNAL_VECTOR_STORAGE_BINDING_V2");
        retained.setReleaseId("release"); retained.setProviderType(DeploymentProviderType.COOLIFY);
        retained.setProviderServerUuid(AdmissionFixture.STAGING.serverUuid());
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(handle, retained));
        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("ACTIVE_PROVIDER_RESOURCE_UNSUPPORTED");
        retained.setResourceKind(retainedKind);

        when(client.getApplication(connection, "runtime-app")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("ACTIVE_PROVIDER_RESOURCE_ABSENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_AI_STATE_STORAGE_BINDING"})
    void completedRetirementMustStillMatchTheEntireFrozenInventorySnapshot(String retainedKind) {
        AdmissionStore admissions = mock(AdmissionStore.class);
        DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
        PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
        TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
        TargetRetirementStore retirements = mock(TargetRetirementStore.class);
        TargetRetirementInventory retirementInventory = mock(TargetRetirementInventory.class);
        AdmissionJson json = new AdmissionJson(new ObjectMapper().findAndRegisterModules());
        var source = new ProductionAdmissionResourceReadbackSource(admissions, targets,
            mock(DeploymentReleaseRepository.class), mock(DeploymentVersionRepository.class), handles, consumers,
            operations, retirements, retirementInventory, mock(CoolifyTargetProfileResolver.class),
            mock(CoolifyApiClient.class), properties(), json, Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));
        Reservation reservation = reservation();
        when(targets.findById(AdmissionFixture.TARGET)).thenReturn(Optional.of(profile()));

        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement-1");
        operation.setFenceEpoch(7);
        when(operations.findById(operation.getId())).thenReturn(Optional.of(operation));
        var owned = new TargetRetirementContracts.ResourceGeneration("owned", "release", DeploymentProviderType.COOLIFY,
            "APPLICATION", "runtime-app", AdmissionFixture.STAGING.serverUuid(), "owned-generation", false);
        var retained = new TargetRetirementContracts.ResourceGeneration("retained", null, DeploymentProviderType.COOLIFY,
            retainedKind, "external-resource", null, "retained-generation", true);
        var snapshot = new TargetRetirementContracts.Snapshot("customer", "tenant", "deployment",
            AdmissionFixture.TARGET, "target-generation", 1,
            List.of(new TargetRetirementContracts.ReleaseGeneration("release", "version", "release-generation")),
            List.of(owned, retained), List.of());
        var evidence = new TargetRetirementContracts.Evidence("request", 7, AdmissionFixture.NOW.minusSeconds(1),
            "durable-proof", true, true, true, true, 0L, 0L, 0L, 0L, true, List.of());
        var evidenceByKind = new EnumMap<TargetRetirementContracts.ParticipantKind, TargetRetirementContracts.Evidence>(
            TargetRetirementContracts.ParticipantKind.class);
        for (var kind : TargetRetirementContracts.ParticipantKind.values()) evidenceByKind.put(kind, evidence);
        var progress = new TargetRetirementContracts.Progress(Map.of("retirement-1:7:RESOURCES:owned",
            new TargetRetirementContracts.EffectResult(true, "owned-absent", AdmissionFixture.NOW.minusSeconds(1))),
            Map.copyOf(evidenceByKind), evidence, List.of());
        var retention = new TargetRetirementContracts.Retention(TargetRetirementContracts.RetentionDecision.RECLAIM,
            null, null, "backup-proof");
        var summary = new TargetRetirementContracts.Summary("retirement-1", "deployment", AdmissionFixture.TARGET,
            "customer", "admission-expiry:" + reservation.id() + ":" + reservation.generation(), "request",
            TargetRetirementContracts.State.COMPLETE, TargetRetirementContracts.Step.VERIFY, snapshot,
            AdmissionFixture.NOW.plusSeconds(60), retention, 1, null, null, progress, false, null,
            AdmissionFixture.NOW.minusSeconds(20), AdmissionFixture.NOW.minusSeconds(10),
            AdmissionFixture.NOW.minusSeconds(1), AdmissionFixture.NOW.minusSeconds(1));
        when(retirements.summary(operation)).thenReturn(summary);
        var ownedHandle = new DeploymentProviderResourceHandleEntity();
        ownedHandle.setId("owned"); ownedHandle.setProviderType(DeploymentProviderType.COOLIFY);
        ownedHandle.setProviderResourceUuid("runtime-app"); ownedHandle.setStatus("DELETED");
        ownedHandle.setLastObservedStatus("ABSENT"); ownedHandle.setLastObservedAt(AdmissionFixture.NOW.minusSeconds(1));
        var retainedHandle = new DeploymentProviderResourceHandleEntity();
        retainedHandle.setId("retained"); retainedHandle.setProviderType(DeploymentProviderType.COOLIFY);
        retainedHandle.setResourceKind(retainedKind); retainedHandle.setProviderResourceUuid("external-resource");
        retainedHandle.setStatus("CONFIGURED");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(ownedHandle, retainedHandle));
        when(consumers.findAll()).thenReturn(List.of());

        if ("EXTERNAL_VECTOR_STORAGE_BINDING".equals(retainedKind)) {
            assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
                .isInstanceOf(AdmissionFailure.class).hasMessageContaining("RETAINED_INDEX_CAPACITY_REQUIRED");
            assertThat(retainedHandle.getStatus()).isEqualTo("CONFIGURED");
            verify(handles, never()).save(retainedHandle);
            return;
        }
        var readback = source.read(new ReadbackContext(reservation, policy(), "worker-1"));
        assertThat(readback.resourcesAbsent()).isTrue();
        assertThat(retainedHandle.getStatus()).isEqualTo("CONFIGURED");
        verify(handles, never()).save(retainedHandle);

        ownedHandle.setStatus("ACTIVE");
        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("RETIREMENT_INVENTORY_INCOMPLETE");
        ownedHandle.setStatus("DELETED");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(ownedHandle));
        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("RETIREMENT_INVENTORY_INCOMPLETE");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(ownedHandle, retainedHandle));
        doThrow(new TargetRetirementFailure("SNAPSHOT_GENERATION_CHANGED"))
            .when(retirementInventory).recheck(org.mockito.ArgumentMatchers.eq(snapshot), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("STALE_RESOURCE_GENERATION");
    }

    private static DeploymentTargetProfileEntity profile() {
        var profile = new DeploymentTargetProfileEntity();
        profile.setId(AdmissionFixture.TARGET); profile.setActive(true); profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setProviderConfigJson("{\"serverUuid\":\"" + AdmissionFixture.STAGING.serverUuid()
            + "\",\"destinationUuid\":\"" + AdmissionFixture.STAGING.destinationUuid() + "\"}");
        return profile;
    }

    private static Reservation activeReservation(Manifest manifest, AdmissionJson json) {
        return new Reservation("reservation-1", "customer", "tenant", "deployment", AdmissionFixture.TARGET,
            Intent.CUSTOMER_STAGING, "operation", "request", "generation-1", 5, AdmissionContracts.State.ACTIVE,
            AdmissionFixture.vector(1), "policy1", manifest.id(), json.fingerprint(manifest), manifest.bindingFingerprint(),
            AdmissionFixture.NOW.minusSeconds(2000), AdmissionFixture.NOW, AdmissionFixture.NOW.minusSeconds(1000),
            AdmissionFixture.NOW.plusSeconds(1000), 0, null, null, ExpiryState.CURRENT, "cycle-1", null, 1,
            "worker-1", null, null, null, "evidence", null, null, null, null);
    }

    private Reservation reservation() {
        return new Reservation("reservation-1", "customer", "tenant", "deployment", AdmissionFixture.TARGET,
            Intent.CUSTOMER_STAGING, "operation", "request", "generation-1", 5, AdmissionContracts.State.ACTIVE,
            AdmissionFixture.vector(1), "policy1", "manifest1", "manifest-fingerprint", "binding-fingerprint",
            AdmissionFixture.NOW.minusSeconds(2000), AdmissionFixture.NOW, AdmissionFixture.NOW.minusSeconds(1000),
            AdmissionFixture.NOW.minusSeconds(1), 0, null, null, ExpiryState.HANDED_OFF, "cycle-1", null, 1,
            "worker-1", AdmissionFixture.NOW.minusSeconds(20), AdmissionFixture.NOW.minusSeconds(10), "retirement-1",
            "evidence", null, null, null, null);
    }

    private FleetPolicy policy() {
        Budget budget = new Budget(AdmissionFixture.vector(10), AdmissionFixture.vector(0), AdmissionFixture.vector(0),
            "measurement", AdmissionFixture.NOW.minusSeconds(1), AdmissionFixture.NOW.plusSeconds(100));
        TargetBudget target = new TargetBudget(AdmissionFixture.TARGET, Intent.CUSTOMER_STAGING,
            AdmissionFixture.STAGING, "profile", budget);
        return new FleetPolicy("policy1", budget, Map.of(AdmissionFixture.TARGET, target),
            new LifecyclePolicy(1000, 100, 100, 2, 3000, 200, 10, 10, 20, 2, 60, 3600,
                "policy", "approved-mail"), "restore", "privacy", "support");
    }

    private static CustomerSelfServiceProperties properties() {
        return new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer(
                AdmissionFixture.STAGING.serverUuid(), AdmissionFixture.STAGING.ip(), "host.docker.internal"),
            TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer(
                "production-server", "46.225.162.106", "production.internal")
        ), 300);
    }
}
