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
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.EnumMap;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    @MethodSource("activeResourceShapes")
    void activeReadbackRequiresFreshExactCoolifyResourceEvidence(String retainedKind, String resourceKind,
                                                                boolean nestedPlacement) throws Exception {
        var fixture = new ActiveReadbackFixture(retainedKind, resourceKind, nestedPlacement);
        var source = fixture.source;
        var reservation = fixture.reservation;
        var handles = fixture.handles;
        var consumers = fixture.consumers;
        var client = fixture.client;
        var connection = fixture.connection;
        var handle = fixture.handle;
        var retained = fixture.retained;
        var raw = fixture.raw;
        var unbound = new PlatformConsumerEntity();
        var otherDeployment = consumer("other-deployment", null, null);
        var configuredTarget = consumer("deployment", AdmissionFixture.TARGET, null);
        var legacyTarget = consumer("deployment", null, null);
        var releaseReference = consumer("other-deployment", "other-target", "release");
        var unrelatedRelease = consumer("other-deployment", "other-target", "other-release");
        var otherTarget = consumer("deployment", "other-target", null);
        when(consumers.findAll()).thenReturn(List.of(unbound, otherDeployment, configuredTarget, legacyTarget,
            releaseReference, unrelatedRelease, otherTarget));

        var readback = source.read(new ReadbackContext(reservation, policy(), "worker-1"));
        assertThat(readback.inventoryComplete()).isTrue();
        assertThat(readback.evidenceReference()).startsWith("admission-active:");
        assertThat(readback.resourcesAbsent()).isFalse();
        assertThat(readback.assignmentCount()).isEqualTo(3);
        verify(client, never()).getApplication(connection, "external-resource");
        verify(client, never()).getDatabase(connection, "external-resource");

        for (String invalid : List.of("missing-placement", "wrong-resource", "wrong-destination",
                "wrong-nested-server", "conflicting-direct-server")) {
            raw.remove(List.of("server_uuid", "destination"));
            raw.put("uuid", "wrong-resource".equals(invalid) ? "other-resource" : "runtime-app");
            if (!"missing-placement".equals(invalid)) {
                raw.putObject("destination").put("uuid", "wrong-destination".equals(invalid)
                        ? "other-destination" : AdmissionFixture.STAGING.destinationUuid())
                    .putObject("server").put("uuid", "wrong-nested-server".equals(invalid)
                        ? "other-server" : AdmissionFixture.STAGING.serverUuid());
                if ("conflicting-direct-server".equals(invalid)) raw.put("server_uuid", "other-server");
            }
            assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
                .isInstanceOf(AdmissionFailure.class).hasMessageContaining("ACTIVE_PROVIDER_RESOURCE_CHANGED");
        }
        raw.remove(List.of("server_uuid", "destination"));
        raw.put("uuid", "runtime-app").put("server_uuid", AdmissionFixture.STAGING.serverUuid());

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
        when(client.getDatabase(connection, "runtime-app")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> source.read(new ReadbackContext(reservation, policy(), "worker-1")))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("ACTIVE_PROVIDER_RESOURCE_ABSENT");
    }

    @Test
    void verificationReapplyReadsExactUnassignedFailedReleaseWithoutMutationsButNormalReadRejectsIt() {
        var fixture = verificationReapplyFixture();
        var reservation = fixture.reservation;
        var context = new ReadbackContext(reservation, policy(), null);

        var readback = fixture.source.readForVerificationReapply(context);

        assertThat(readback.reservationId()).isEqualTo(reservation.id());
        assertThat(readback.generation()).isEqualTo(reservation.generation());
        assertThat(readback.expectedRevision()).isEqualTo(reservation.revision());
        assertThat(readback.workerToken()).isNull();
        assertThat(readback.manifestFingerprint()).isEqualTo(reservation.manifestFingerprint());
        assertThat(readback.bindingFingerprint()).isEqualTo(reservation.bindingFingerprint());
        assertThat(readback.placement()).isEqualTo(AdmissionFixture.STAGING);
        assertThat(readback.observedAt()).isEqualTo(AdmissionFixture.NOW);
        assertThat(readback.evidenceReference()).startsWith("admission-active:");
        assertThat(readback.inventoryComplete()).isTrue();
        assertThat(readback.outcomeSettled()).isTrue();
        assertThat(readback.resourcesAbsent()).isFalse();
        assertThat(readback.assignmentCount()).isZero();
        assertThat(fixture.release.getStatus()).isEqualTo("APPLIED_VERIFICATION_FAILED");
        assertThat(fixture.release.getVerificationStatus()).isEqualTo("FAILED");
        assertThat(fixture.release.getProvisioningStatus()).isEqualTo("ACTIVE");
        assertThat(fixture.handle.getStatus()).isEqualTo("ACTIVE");

        verify(fixture.admissions).reviewed("MANIFEST", "manifest1", Manifest.class);
        verify(fixture.admissions).currentReleaseId(reservation);
        verify(fixture.targets).findById(AdmissionFixture.TARGET);
        verify(fixture.versions).findById("version");
        verify(fixture.releases).findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", AdmissionFixture.TARGET);
        verify(fixture.handles).findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET);
        verify(fixture.consumers, times(2)).findAll();
        verify(fixture.resolver).requireConnection(fixture.profile);
        verify(fixture.client).getServer(fixture.connection, AdmissionFixture.STAGING.serverUuid());
        verify(fixture.client).getApplication(fixture.connection, "runtime-app");
        verifyNoMoreInteractions(fixture.admissions, fixture.targets, fixture.versions, fixture.releases,
            fixture.handles, fixture.consumers, fixture.resolver, fixture.client);
        verifyNoInteractions(fixture.operations, fixture.retirements, fixture.retirementInventory);

        assertThatThrownBy(() -> fixture.source.read(context))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("ACTIVE_RELEASE_UNPROVEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"target", "release", "dynamic-target"})
    void verificationReapplyRejectsAssignedConsumer(String assignment) {
        var fixture = verificationReapplyFixture();
        var assigned = "target".equals(assignment)
            ? consumer("deployment", AdmissionFixture.TARGET, "other-release")
            : "dynamic-target".equals(assignment) ? consumer("deployment", null, null)
                : consumer("other-deployment", "other-target", "release");
        when(fixture.consumers.findAll()).thenReturn(List.of(assigned));

        assertThatThrownBy(() -> fixture.source.readForVerificationReapply(
            new ReadbackContext(fixture.reservation, policy(), null)))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("VERIFICATION_REAPPLY_UNASSIGNED_RELEASE_REQUIRED");
        verifyNoInteractions(fixture.client);
    }

    @Test
    void verificationReapplyAllowsExplicitConfigurationOnlyAssignmentWithoutChangingRetirementReferences() {
        var fixture = verificationReapplyFixture();
        when(fixture.consumers.findAll()).thenReturn(List.of(consumer("deployment", AdmissionFixture.TARGET, null)));

        var readback = fixture.source.readForVerificationReapply(
            new ReadbackContext(fixture.reservation, policy(), null));

        assertThat(readback.assignmentCount()).isZero();
        assertThat(readback.inventoryComplete()).isTrue();
        verify(fixture.consumers, times(2)).findAll();
        fixture.release.setStatus("APPLIED_VERIFIED");
        fixture.release.setVerificationStatus("PASSED");
        assertThat(fixture.source.read(new ReadbackContext(fixture.reservation, policy(), null)).assignmentCount())
            .isEqualTo(1);
        verifyNoInteractions(fixture.operations, fixture.retirements, fixture.retirementInventory);
    }

    @Test
    void verificationReapplyRequiresTheCurrentBoundRelease() {
        var fixture = verificationReapplyFixture();
        var context = new ReadbackContext(fixture.reservation, policy(), null);
        when(fixture.admissions.currentReleaseId(fixture.reservation)).thenReturn("other-release");

        assertThatThrownBy(() -> fixture.source.readForVerificationReapply(context))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("ACTIVE_RELEASE_UNPROVEN");

        when(fixture.admissions.currentReleaseId(fixture.reservation)).thenReturn(null);
        assertThatThrownBy(() -> fixture.source.readForVerificationReapply(context))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("VERIFICATION_REAPPLY_UNASSIGNED_RELEASE_REQUIRED");
        verifyNoInteractions(fixture.client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unhealthy", "wrong-resource", "unavailable"})
    void verificationReapplyRejectsPartialOrFailedNativeInventory(String nativeFailure) {
        var fixture = verificationReapplyFixture();
        var database = new DeploymentProviderResourceHandleEntity();
        database.setId("database-handle"); database.setDeploymentId("deployment");
        database.setTargetProfileId(AdmissionFixture.TARGET); database.setReleaseId("release");
        database.setProviderType(DeploymentProviderType.COOLIFY); database.setResourceKind("RUNTIME_POSTGRES_DATABASE");
        database.setProviderResourceUuid("runtime-db"); database.setStatus("ACTIVE");
        database.setProviderServerUuid(AdmissionFixture.STAGING.serverUuid());
        when(fixture.handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
            .thenReturn(List.of(fixture.handle, database, fixture.retained));
        var raw = fixture.raw.deepCopy().put("uuid", "wrong-resource".equals(nativeFailure) ? "other-db" : "runtime-db");
        var summary = new CoolifyDatabaseSummary("runtime-db", "runtime",
            "unhealthy".equals(nativeFailure) ? "running:unhealthy" : "running:healthy", "postgresql", null, null, raw);
        if ("unavailable".equals(nativeFailure)) {
            when(fixture.client.getDatabase(fixture.connection, "runtime-db"))
                .thenThrow(new IllegalStateException("native readback unavailable"));
        } else {
            when(fixture.client.getDatabase(fixture.connection, "runtime-db"))
                .thenReturn("missing".equals(nativeFailure) ? Optional.empty() : Optional.of(summary));
        }
        String expectedFailure = switch (nativeFailure) {
            case "missing" -> "ACTIVE_PROVIDER_RESOURCE_ABSENT";
            case "unhealthy" -> "ACTIVE_PROVIDER_RESOURCE_NOT_READY";
            case "wrong-resource" -> "ACTIVE_PROVIDER_RESOURCE_CHANGED";
            default -> "ACTIVE_PROVIDER_READBACK_UNAVAILABLE";
        };

        assertThatThrownBy(() -> fixture.source.readForVerificationReapply(
            new ReadbackContext(fixture.reservation, policy(), null)))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining(expectedFailure);
        verify(fixture.client).getApplication(fixture.connection, "runtime-app");
        verify(fixture.client).getDatabase(fixture.connection, "runtime-db");
    }

    @ParameterizedTest
    @MethodSource("verificationReapplyIneligibleStates")
    void verificationReapplyRejectsWorkerRetirementExpiryOrWrongAllocationState(
        AdmissionContracts.State state, String workerToken, String retirementOperationId, ExpiryState expiryState) {
        var fixture = verificationReapplyFixture();
        var reservation = activeReservation(fixture.manifest, fixture.json, state, workerToken,
            retirementOperationId, expiryState);

        assertThatThrownBy(() -> fixture.source.readForVerificationReapply(new ReadbackContext(reservation, policy(), null)))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("VERIFICATION_REAPPLY_STATE_UNPROVEN");
        verifyNoInteractions(fixture.admissions, fixture.targets, fixture.releases, fixture.versions,
            fixture.handles, fixture.consumers, fixture.resolver, fixture.client,
            fixture.operations, fixture.retirements, fixture.retirementInventory);
    }

    private static Stream<Arguments> verificationReapplyIneligibleStates() {
        return Stream.of(
            Arguments.of(AdmissionContracts.State.ACTIVE, null, null, ExpiryState.CURRENT),
            Arguments.of(AdmissionContracts.State.OUTCOME_UNKNOWN, "worker-1", null, ExpiryState.CURRENT),
            Arguments.of(AdmissionContracts.State.OUTCOME_UNKNOWN, null, "retirement-1", ExpiryState.CURRENT),
            Arguments.of(AdmissionContracts.State.OUTCOME_UNKNOWN, null, null, ExpiryState.HANDED_OFF));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", "PASSED"})
    void verificationReapplyRejectsVerificationStatusOtherThanFailed(String verificationStatus) {
        var fixture = verificationReapplyFixture();
        fixture.release.setVerificationStatus(verificationStatus);

        assertThatThrownBy(() -> fixture.source.readForVerificationReapply(
            new ReadbackContext(fixture.reservation, policy(), null)))
            .isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("VERIFICATION_REAPPLY_UNASSIGNED_RELEASE_REQUIRED");
        verifyNoInteractions(fixture.client);
    }

    private static ActiveReadbackFixture verificationReapplyFixture() {
        var fixture = new ActiveReadbackFixture("EXTERNAL_DOCUMENT_STORAGE_BINDING", "APPLICATION", true);
        fixture.reservation = activeReservation(fixture.manifest, fixture.json, AdmissionContracts.State.OUTCOME_UNKNOWN,
            null, null, ExpiryState.CURRENT);
        fixture.release.setStatus("APPLIED_VERIFICATION_FAILED");
        fixture.release.setVerificationStatus("FAILED");
        when(fixture.admissions.currentReleaseId(fixture.reservation)).thenReturn("release");
        return fixture;
    }

    private static Stream<Arguments> activeResourceShapes() {
        return Stream.of("EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_AI_STATE_STORAGE_BINDING")
            .flatMap(retained -> Stream.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE")
                .flatMap(resource -> Stream.of(false, true).map(nested -> Arguments.of(retained, resource, nested))));
    }

    private static PlatformConsumerEntity consumer(String deployment, String target, String release) {
        var consumer = new PlatformConsumerEntity();
        consumer.setBoundDeploymentId(deployment);
        consumer.setBoundTargetProfileId(target);
        consumer.setBoundReleaseId(release);
        return consumer;
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

    private static final class ActiveReadbackFixture {
        final AdmissionStore admissions = mock(AdmissionStore.class);
        final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
        final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
        final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
        final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
        final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
        final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
        final TargetRetirementStore retirements = mock(TargetRetirementStore.class);
        final TargetRetirementInventory retirementInventory = mock(TargetRetirementInventory.class);
        final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        final CoolifyApiClient client = mock(CoolifyApiClient.class);
        final AdmissionJson json = new AdmissionJson(new ObjectMapper().findAndRegisterModules());
        final Manifest manifest = new Manifest("manifest1", "deployment", "version", "config-hash",
            AdmissionFixture.TARGET, "b".repeat(64), "topology", AdmissionFixture.vector(1), Set.of());
        final DeploymentTargetProfileEntity profile = profile();
        final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        final DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        final DeploymentProviderResourceHandleEntity retained = new DeploymentProviderResourceHandleEntity();
        final ObjectNode raw = new ObjectMapper().createObjectNode().put("uuid", "runtime-app");
        final CoolifyConnection connection;
        final ProductionAdmissionResourceReadbackSource source;
        Reservation reservation = activeReservation(manifest, json);

        ActiveReadbackFixture(String retainedKind, String resourceKind, boolean nestedPlacement) {
            source = new ProductionAdmissionResourceReadbackSource(admissions, targets, releases, versions,
                handles, consumers, operations, retirements, retirementInventory, resolver, client, properties(),
                json, Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));
            when(admissions.reviewed("MANIFEST", "manifest1", Manifest.class)).thenReturn(manifest);
            when(targets.findById(AdmissionFixture.TARGET)).thenReturn(Optional.of(profile));
            var version = new DeploymentVersionEntity();
            version.setId("version"); version.setDeploymentId("deployment"); version.setConfigHash("config-hash");
            version.setStatus("PUBLISHED");
            when(versions.findById("version")).thenReturn(Optional.of(version));
            release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId(AdmissionFixture.TARGET);
            release.setDeploymentVersionId("version"); release.setStatus("APPLIED_VERIFIED");
            release.setProvisioningStatus("ACTIVE"); release.setProviderType(DeploymentProviderType.COOLIFY);
            release.setRuntimeAuthorityFingerprint("a".repeat(64));
            when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", AdmissionFixture.TARGET))
                .thenReturn(List.of(release));
            handle.setId("runtime-handle"); handle.setDeploymentId("deployment"); handle.setTargetProfileId(AdmissionFixture.TARGET);
            handle.setReleaseId("release"); handle.setProviderType(DeploymentProviderType.COOLIFY);
            handle.setResourceKind(resourceKind); handle.setProviderResourceUuid("runtime-app");
            handle.setProviderServerUuid(AdmissionFixture.STAGING.serverUuid()); handle.setStatus("ACTIVE");
            retained.setId("retained-binding"); retained.setResourceKind(retainedKind);
            retained.setProviderResourceUuid("external-resource"); retained.setStatus("CONFIGURED");
            when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", AdmissionFixture.TARGET))
                .thenReturn(List.of(handle, retained));
            var config = new CoolifyTargetProfileConfig("https://coolify.test", "project", "staging", "environment",
                AdmissionFixture.STAGING.serverUuid(), AdmissionFixture.STAGING.destinationUuid(), "example.test", "4.0.0",
                5, 600, false, true, "8097", "/actuator/health", "8097");
            connection = new CoolifyConnection("https://coolify.test", "private", config);
            when(resolver.requireConnection(profile)).thenReturn(connection);
            when(client.getServer(connection, AdmissionFixture.STAGING.serverUuid())).thenReturn(new ObjectMapper()
                .createObjectNode().put("uuid", AdmissionFixture.STAGING.serverUuid()).put("ip", "host.docker.internal"));
            if (nestedPlacement) {
                raw.putObject("destination").put("uuid", AdmissionFixture.STAGING.destinationUuid())
                    .putObject("server").put("uuid", AdmissionFixture.STAGING.serverUuid());
            } else raw.put("server_uuid", AdmissionFixture.STAGING.serverUuid());
            when(client.getApplication(connection, "runtime-app")).thenReturn(Optional.of(new CoolifyApplicationSummary(
                "runtime-app", "runtime", null, "running:healthy", null, null, raw)));
            when(client.getDatabase(connection, "runtime-app")).thenReturn(Optional.of(new CoolifyDatabaseSummary(
                "runtime-app", "runtime", "running:healthy", "postgresql", null, null, raw)));
            when(consumers.findAll()).thenReturn(List.of());
        }
    }

    private static DeploymentTargetProfileEntity profile() {
        var profile = new DeploymentTargetProfileEntity();
        profile.setId(AdmissionFixture.TARGET); profile.setActive(true); profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setProviderConfigJson("{\"serverUuid\":\"" + AdmissionFixture.STAGING.serverUuid()
            + "\",\"destinationUuid\":\"" + AdmissionFixture.STAGING.destinationUuid() + "\"}");
        return profile;
    }

    private static Reservation activeReservation(Manifest manifest, AdmissionJson json) {
        return activeReservation(manifest, json, AdmissionContracts.State.ACTIVE, "worker-1", null, ExpiryState.CURRENT);
    }

    private static Reservation activeReservation(Manifest manifest, AdmissionJson json, AdmissionContracts.State state,
                                                 String workerToken, String retirementOperationId, ExpiryState expiryState) {
        return new Reservation("reservation-1", "customer", "tenant", "deployment", AdmissionFixture.TARGET,
            Intent.CUSTOMER_STAGING, "operation", "request", "generation-1", 5, state,
            AdmissionFixture.vector(1), "policy1", manifest.id(), json.fingerprint(manifest), manifest.bindingFingerprint(),
            AdmissionFixture.NOW.minusSeconds(2000), AdmissionFixture.NOW, AdmissionFixture.NOW.minusSeconds(1000),
            AdmissionFixture.NOW.plusSeconds(1000), 0, null, null, expiryState, "cycle-1", null, 1,
            workerToken, null, null, retirementOperationId, "evidence", null, null, null, null);
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
