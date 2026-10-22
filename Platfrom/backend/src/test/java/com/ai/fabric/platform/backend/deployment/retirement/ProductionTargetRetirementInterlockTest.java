package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductionTargetRetirementInterlockTest {
    private final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
    private final TargetRetirementGateRepository gates = mock(TargetRetirementGateRepository.class);
    private final TargetRetirementInventory inventory = mock(TargetRetirementInventory.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final TargetRetirementProviderResources resources = mock(TargetRetirementProviderResources.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<TargetRetirementParticipation> participation = mock(ObjectProvider.class);
    private ProductionTargetRetirementInterlock interlock;
    private Context context;
    private TargetRetirementOperationEntity operation;

    @BeforeEach void setup() {
        var auth = new PlatformAuthProperties(true, "X-API-Key", true, true, "session", Duration.ofHours(1),
            true, "Strict", "operator", "admin", false, null, null, null);
        interlock = new ProductionTargetRetirementInterlock(operations, gates, inventory,
            new TargetRetirementJson(new ObjectMapper()), deployments, users, auth, secrets, resources,
            participation, List.of(), mock(PlatformTransactionManager.class));
        var resource = new ResourceGeneration("handle", "release", DeploymentProviderType.COOLIFY,
            "APPLICATION", "application", "server", "generation", false);
        var snapshot = new Snapshot("customer", "tenant", "deployment", "target", "target-generation", 1,
            List.of(new ReleaseGeneration("release", "version", "release-generation")), List.of(resource), List.of());
        context = new Context("operation", "operation-key", "fingerprint", snapshot, 2, "old-worker",
            "platform-admin", "PLATFORM_ADMIN", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
        operation = new TargetRetirementOperationEntity();
        operation.setId("operation");
        operation.setState(State.RETRYABLE_FAILURE);
        operation.setWorkerToken("old-worker");
        operation.setLeaseExpiresAt(Instant.now().minusSeconds(1));
        operation.setInFlightEffect("operation:2:RESOURCES:handle");
        when(operations.findById("operation")).thenReturn(Optional.of(operation));
        var gate = new TargetRetirementGateEntity();
        gate.setState("RETIRING");
        gate.setOperationId("operation");
        gate.setEpoch(2);
        when(gates.findByDeploymentIdAndTargetProfileId("deployment", "target")).thenReturn(Optional.of(gate));
    }

    @Test void retryRequiresAuthoritativeProviderAbsenceRatherThanLeaseExpiryAlone() {
        when(resources.readback(eq(context), any())).thenReturn(new EffectResult(false,
            "provider-resource:handle:present", Instant.now()));
        RecoveryEvidence present = interlock.recover(context, "old-worker", "operation:2:RESOURCES:handle");
        assertThat(present.workerFenced()).isTrue();
        assertThat(present.remoteOutcomeSettled()).isFalse();
        assertThat(present.resumeSafe()).isTrue();

        when(resources.readback(eq(context), any())).thenReturn(new EffectResult(true,
            "provider-resource:handle:absence-candidate", Instant.now()));
        RecoveryEvidence absent = interlock.recover(context, "old-worker", "operation:2:RESOURCES:handle");
        assertThat(absent.workerFenced()).isTrue();
        assertThat(absent.remoteOutcomeSettled()).isTrue();
        assertThat(absent.resumeSafe()).isTrue();
    }

    @Test void changedGateGenerationCannotFenceThePreviousWorker() {
        var gate = gates.findByDeploymentIdAndTargetProfileId("deployment", "target").orElseThrow();
        gate.setEpoch(3);
        RecoveryEvidence evidence = interlock.recover(context, "old-worker", "operation:2:RESOURCES:handle");
        assertThat(evidence.workerFenced()).isFalse();
        assertThat(evidence.remoteOutcomeSettled()).isFalse();
        assertThat(evidence.resumeSafe()).isFalse();
        verifyNoInteractions(resources);
    }

    @Test void fencedActiveDrainCanResumeOnlyWithExplicitParticipantRecoveryProof() {
        String effect = "operation:2:DRAIN:target";
        operation.setInFlightEffect(effect);
        var drain = mock(TargetRetirementParticipant.class);
        when(drain.kind()).thenReturn(ParticipantKind.RUNTIME_DRAIN);
        when(drain.readback(context, Step.DRAIN, effect))
            .thenReturn(new EffectResult(false, "runtime:active", Instant.now()));
        when(drain.recoverySafe(context, Step.DRAIN, effect)).thenReturn(true);
        var participants = new TargetRetirementParticipation(List.of(drain), List.of(interlock));
        when(participation.getObject()).thenReturn(participants);
        var evidence = interlock.recover(context, "old-worker", effect);
        assertThat(evidence.workerFenced()).isTrue();
        assertThat(evidence.remoteOutcomeSettled()).isFalse();
        assertThat(evidence.resumeSafe()).isTrue();

        when(drain.recoverySafe(context, Step.DRAIN, effect)).thenReturn(false);
        assertThat(interlock.recover(context, "old-worker", effect).resumeSafe()).isFalse();
        verifyNoInteractions(resources);
    }
}
