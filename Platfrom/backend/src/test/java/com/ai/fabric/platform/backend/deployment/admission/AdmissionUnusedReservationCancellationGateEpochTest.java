package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdmissionUnusedReservationCancellationGateEpochTest extends AdmissionUnusedConsumerDetachmentTest {
    @ParameterizedTest @ValueSource(longs = {1, 7})
    void availableConfigurationEpochDoesNotMeanRetirementHistory(long epoch) {
        gate(epoch);
        var originalGate = fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates");
        var receipt = cancel();
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(receipt.consumerDetachments()).hasSize(1);
        assertThat(receipt.evidence().targetGate()).isEqualTo(new UnusedTargetGate("gate", epoch, epoch));
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates")).isEqualTo(originalGate);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_target_retirement_operations", Long.class)).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT row_version FROM ai_workspace_installations", Long.class)).isZero();
        assertThat(cancel()).isEqualTo(receipt);
        verifyNoInteractions(ReflectionTestUtils.getField(owner, "retirementGates"));
    }

    @Test void cancellationKeepsCommittedAuthorizationEvenWhenEntitlementRevisionHasAdvanced() {
        gate(7);
        fixture.jdbc.execute("ALTER TABLE platform_customer_offer_entitlements ADD COLUMN revision BIGINT DEFAULT 2");
        assertThat(fixture.sourceScope.entitlementRevision()).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT revision FROM platform_customer_offer_entitlements", Long.class)).isEqualTo(2);
        fixture.store.requireCustomerAuthorization(fixture.reservation, fixture.sourceScope);
        assertThat(cancel().reservation().state()).isEqualTo(State.RELEASED);
    }

    @Test void realGateServiceAdvancesConfigurationGenerationWithoutRetirementOrEffects() {
        gate(0);
        var deployments = mock(DeploymentRepository.class);
        var deployment = new DeploymentEntity(); deployment.setId("deployment");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        var gates = mock(TargetRetirementGateRepository.class);
        var persisted = new TargetRetirementGateEntity(); persisted.setId("gate");
        persisted.setDeploymentId("deployment"); persisted.setTargetProfileId(TARGET); persisted.setState("AVAILABLE");
        when(gates.findForUpdate("deployment", TARGET)).thenReturn(Optional.of(persisted));
        when(gates.save(any(TargetRetirementGateEntity.class))).thenAnswer(call -> {
            TargetRetirementGateEntity value = call.getArgument(0);
            fixture.jdbc.update("UPDATE platform_target_retirement_gates SET epoch=?,row_version=row_version+1 WHERE id='gate'", value.getEpoch());
            return value;
        });
        var operations = mock(TargetRetirementOperationRepository.class);
        when(operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", TARGET)).thenReturn(List.of());
        var service = spy(new TargetRetirementGateService(deployments, gates, operations));
        ReflectionTestUtils.setField(owner, "retirementGates", service);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            for (long expected = 1; expected <= 7; expected++)
                assertThat(service.requireAvailableForMutation("deployment", TARGET)).isEqualTo(expected);
        });
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_target_retirement_operations", Long.class)).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_releases", Long.class)).isZero();
        var originalGate = fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates");
        clearInvocations(service);
        var receipt = cancel();
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(receipt.evidence().targetGate()).isEqualTo(new UnusedTargetGate("gate", 7, 7));
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates")).isEqualTo(originalGate);
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"epoch", "row-version", "identity", "delete", "insert"})
    void gateChangesDuringDetachmentRollBackConsumerHistoryHandleAndCapacity(String change) {
        if (!change.equals("insert")) gate(7);
        var originalGates = fixture.jdbc.queryForList("SELECT * FROM platform_target_retirement_gates ORDER BY id");
        doAnswer(call -> {
            switch (change) {
                case "epoch" -> fixture.jdbc.update("UPDATE platform_target_retirement_gates SET epoch=8");
                case "row-version" -> fixture.jdbc.update("UPDATE platform_target_retirement_gates SET row_version=8");
                case "identity" -> fixture.jdbc.update("UPDATE platform_target_retirement_gates SET id='replacement'");
                case "delete" -> fixture.jdbc.update("DELETE FROM platform_target_retirement_gates");
                case "insert" -> gate(7);
                default -> throw new AssertionError(change);
            }
            return null;
        }).when(audit).record(anyString(), anyString(), anyString(), anyMap());
        assertThatThrownBy(this::cancel).hasMessageContaining("UNUSED_CANCELLATION_EVIDENCE_CHANGED");
        assertUnchanged();
        assertThat(fixture.jdbc.queryForList("SELECT * FROM platform_target_retirement_gates ORDER BY id")).isEqualTo(originalGates);
    }

    @ParameterizedTest @ValueSource(strings = {"state", "operation", "negative-epoch", "retirement-history", "deletion-history"})
    void availableConfigurationEpochNeverHidesRetirementOrUncertainHistory(String blocker) {
        gate(7);
        switch (blocker) {
            case "state" -> fixture.jdbc.update("UPDATE platform_target_retirement_gates SET state='DRAINING'");
            case "operation" -> fixture.jdbc.update("UPDATE platform_target_retirement_gates SET operation_id='operation'");
            case "negative-epoch" -> fixture.jdbc.update("UPDATE platform_target_retirement_gates SET epoch=-1");
            case "retirement-history" -> fixture.jdbc.update("INSERT INTO platform_target_retirement_operations VALUES ('operation','deployment',?)", TARGET);
            case "deletion-history" -> fixture.jdbc.update("INSERT INTO platform_deployment_deletion_operations VALUES ('deletion','deployment')");
            default -> throw new AssertionError(blocker);
        }
        assertThatThrownBy(this::cancel).hasMessageContaining("UNUSED_CANCELLATION_EFFECT_HISTORY_PRESENT");
        assertUnchanged();
    }

    @ParameterizedTest @ValueSource(strings = {"release", "allocation", "graph", "event", "consumer-release", "consumer-history", "consumer-creation", "active-app", "stale-app"})
    void configurationGenerationDoesNotRelaxAnyNoEffectOrDetachmentProof(String blocker) {
        gate(7);
        String code = switch (blocker) {
            case "release" -> {
                fixture.jdbc.update("INSERT INTO platform_deployment_releases VALUES ('release','deployment',?)", TARGET);
                yield "UNUSED_CANCELLATION_EFFECT_HISTORY_PRESENT";
            }
            case "allocation" -> {
                fixture.jdbc.update("INSERT INTO provider_usage_allocations VALUES ('allocation','deployment',?)", TARGET);
                yield "UNUSED_CANCELLATION_EFFECT_HISTORY_PRESENT";
            }
            case "graph" -> {
                fixture.jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES ('graph','deployment',?)", TARGET);
                yield "UNUSED_CANCELLATION_EFFECT_HISTORY_PRESENT";
            }
            case "event" -> {
                fixture.jdbc.update("UPDATE deployment_admission_events SET event_type='PROVISIONING'");
                yield "UNUSED_CANCELLATION_EFFECT_HISTORY_PRESENT";
            }
            case "consumer-release" -> {
                fixture.jdbc.update("UPDATE platform_consumers SET bound_release_id='release'");
                yield "UNUSED_CONSUMER_ASSIGNMENT_CHANGED";
            }
            case "consumer-history" -> {
                fixture.jdbc.update("UPDATE platform_consumer_binding_history SET from_binding_revision=2");
                yield "UNUSED_CONSUMER_HISTORY_UNPROVEN";
            }
            case "consumer-creation" -> {
                fixture.jdbc.update("DELETE FROM platform_customer_entitlement_commands");
                yield "UNUSED_CONSUMER_CREATION_UNPROVEN";
            }
            case "active-app" -> {
                fixture.jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE'");
                yield "UNUSED_CONSUMER_WORKSPACE_CHANGED";
            }
            case "stale-app" -> {
                command = withDetachments(List.of(new UnusedConsumerDetachment("consumer", 1,
                    List.of(new DraftWorkspaceExpectation("draft-app", 99)))));
                yield "UNUSED_CONSUMER_WORKSPACE_CHANGED";
            }
            default -> throw new AssertionError(blocker);
        };
        assertThatThrownBy(this::cancel).hasMessageContaining(code);
        assertUnchanged();
    }

    void gate(long epoch) {
        fixture.jdbc.update("INSERT INTO platform_target_retirement_gates (id,deployment_id,target_profile_id,state,epoch,operation_id,row_version) VALUES ('gate','deployment',?,'AVAILABLE',?,NULL,?)", TARGET, epoch, epoch);
    }
}
