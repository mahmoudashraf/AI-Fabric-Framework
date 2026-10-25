package com.loomai.operations;

import static com.loomai.operations.OperationsTestConfiguration.NOW;
import static com.loomai.operations.model.OperationsContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.operations.OperationsTestConfiguration.MutableClock;
import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.config.TargetCapacityPolicy;
import com.loomai.operations.persistence.OperationsRepository;
import com.loomai.operations.service.DeploymentOperationsService;
import com.loomai.operations.service.OperationsException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class AbstractOperationsServiceTest {

    @Autowired DeploymentOperationsService service;
    @Autowired OperationsRepository repository;
    @Autowired OperationsProperties properties;
    @Autowired TargetCapacityPolicy policy;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM deployment_operations_outcome");
        jdbc.update("DELETE FROM deployment_operations_allocation");
        jdbc.update("DELETE FROM deployment_operations_assessment");
        clock.set(NOW);
    }

    @Test
    void persistsExactResponseAndReplaysWithoutRenewingAnExpiredHold() {
        AssessmentRequest request = request("cmd-1", "dep-1");
        AssessmentResponse first = service.assess(request);
        assertThat(first.commandId()).isEqualTo(request.commandId());
        assertThat(first.requestFingerprint()).isEqualTo(request.requestFingerprint());
        assertThat(first.decision()).isEqualTo(Decision.ALLOW);
        assertThat(first.policyRevision()).isEqualTo("test-policy-v1");
        assertThat(first.observedAt()).isEqualTo(NOW);
        assertThat(first.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        clock.set(NOW.plus(Duration.ofDays(1)));
        DeploymentOperationsService restarted = new DeploymentOperationsService(repository, policy, properties, clock);
        assertThat(restarted.assess(request)).isEqualTo(first);
        assertThat(count("deployment_operations_assessment")).isEqualTo(1);
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().holdExpiresAt()).isEqualTo(first.expiresAt());
    }

    @Test
    void deniesCapacityWithoutCreatingAnAllocationForEnforcedDenial() {
        assertThat(service.assess(request("cmd-1", "dep-1")).decision()).isEqualTo(Decision.ALLOW);
        assertThat(service.assess(request("cmd-2", "dep-2")).decision()).isEqualTo(Decision.ALLOW);
        AssessmentResponse denied = service.assess(request("cmd-3", "dep-3"));
        assertThat(denied.decision()).isEqualTo(Decision.DENY);
        assertThat(denied.reason()).isEqualTo("TARGET_CAPACITY_EXCEEDED");
        assertThat(repository.allocation("dep-3", "stage")).isEmpty();
        assertThat(count("deployment_operations_assessment")).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cpuMillis", "memoryMiB", "diskMiB", "databases", "indexes", "dependencies", "replicas", "concurrentApplies"})
    void enforcesEveryResourceDimension(String dimension) throws Exception {
        ObjectNode node = mapper.valueToTree(request("cmd-1", "dep-1"));
        ((ObjectNode) node.get("resourceDemand")).put(dimension, 5000L);
        AssessmentRequest request = mapper.treeToValue(node, AssessmentRequest.class);
        assertThat(service.assess(request).decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void observeRecordsWouldDenyAndTracksTheOperationWithoutGrantingEnforcedPermission() throws Exception {
        ObjectNode node = mapper.valueToTree(request("cmd-observe", "dep-1"));
        node.put("mode", "OBSERVE");
        ((ObjectNode) node.get("resourceDemand")).put("cpuMillis", 3000L);
        AssessmentRequest request = mapper.treeToValue(node, AssessmentRequest.class);
        assertThat(service.assess(request).decision()).isEqualTo(Decision.DENY);
        service.outcome(outcome(request, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().state()).isEqualTo(AllocationState.ACTIVE);
        assertThat(service.assess(request("cmd-2", "dep-2")).decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void missingTargetPolicyIsUnavailableAndStillAcceptsTheCoresObservation() {
        AssessmentRequest request = withTarget(request("cmd-1", "dep-1"), "unknown");
        AssessmentResponse response = service.assess(request);
        assertThat(response.decision()).isEqualTo(Decision.UNAVAILABLE);
        assertThat(response.reason()).isEqualTo("TARGET_POLICY_UNAVAILABLE");
        service.outcome(outcome(request, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        assertThat(repository.allocation("dep-1", "unknown").orElseThrow().state()).isEqualTo(AllocationState.ACTIVE);
    }

    @Test
    void countsSameDeploymentTargetOnceAndTargetsIndependently() {
        AssessmentRequest original = request("cmd-1", "dep-1");
        service.assess(original);
        service.outcome(outcome(original, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        assertThat(service.assess(request("cmd-replace", "dep-1")).decision()).isEqualTo(Decision.ALLOW);
        assertThat(service.assess(request("cmd-2", "dep-2")).decision()).isEqualTo(Decision.ALLOW);
        assertThat(service.assess(withTarget(request("cmd-prod", "dep-1"), "production")).decision()).isEqualTo(Decision.ALLOW);
        assertThat(repository.allocations("stage")).hasSize(2);
        assertThat(repository.allocations("production")).hasSize(1);
    }

    @Test
    void resourceShrinkDoesNotAssertOldResourcesGoneAndStoppedObservationFreesCapacity() {
        AssessmentRequest large = withDemand(request("cmd-large", "dep-1"), new ResourceDemand(2000L, 1024L, 2048L, 1L, 1L, 0L, 1L, 1L));
        service.assess(large);
        service.outcome(outcome(large, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        AssessmentRequest replacement = request("cmd-small", "dep-1");
        assertThat(service.assess(replacement).decision()).isEqualTo(Decision.ALLOW);
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().demand().cpuMillis()).isEqualTo(2000L);
        assertThat(service.assess(request("cmd-denied", "dep-2")).decision()).isEqualTo(Decision.DENY);
        service.outcome(outcome(replacement, OutcomeStatus.STOPPED, NOW.plusSeconds(2)));
        assertThat(service.assess(request("cmd-fresh", "dep-1")).decision()).isEqualTo(Decision.ALLOW);
        assertThat(service.assess(request("cmd-other", "dep-2")).decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void stoppedComputeRetainsDiskAndProvisionedResourceCountsRatherThanClaimingReclamation() {
        AssessmentRequest original = withDemand(request("cmd-disk", "dep-1"),
            new ResourceDemand(1000L, 1024L, 4096L, 1L, 1L, 1L, 1L, 1L));
        service.assess(original);
        service.outcome(outcome(original, OutcomeStatus.STOPPED, NOW.plusSeconds(1)));
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().demand())
            .isEqualTo(original.resourceDemand().retainedAfterStop());
        assertThat(service.assess(request("cmd-other", "dep-2")).decision()).isEqualTo(Decision.DENY);
        assertThat(repository.allocations("stage")).hasSize(1);
    }

    @Test
    void confirmedActiveColdReplacementProjectsExactDesiredDemandAndFreesDownsizedCapacity() {
        AssessmentRequest original = withDemand(request("cmd-large", "dep-1"),
            new ResourceDemand(2000L, 2048L, 4096L, 2L, 2L, 2L, 2L, 1L));
        service.assess(original);
        service.outcome(outcome(original, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        AssessmentRequest replacement = request("cmd-downsized", "dep-1");
        assertThat(service.assess(replacement).decision()).isEqualTo(Decision.ALLOW);
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().demand().cpuMillis()).isEqualTo(2000L);
        service.outcome(outcome(replacement, OutcomeStatus.ACTIVE, NOW.plusSeconds(2)));
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().demand())
            .isEqualTo(replacement.resourceDemand().withoutApplySlot());
        assertThat(service.assess(request("cmd-other", "dep-2")).decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void permitsZeroDiskForNonpersistentApplication() {
        AssessmentRequest request = withDemand(request("cmd-no-disk", "dep-1"),
            new ResourceDemand(1000L, 1024L, 0L, 0L, 0L, 0L, 1L, 1L));
        assertThat(service.assess(request).decision()).isEqualTo(Decision.ALLOW);
        service.outcome(outcome(request, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().demand().diskMiB()).isZero();
    }

    @Test
    void expiredHoldsRemainUnconfirmedNotProofOfAbsentInfrastructure() {
        service.assess(request("cmd-1", "dep-1"));
        service.assess(request("cmd-2", "dep-2"));
        clock.set(NOW.plusSeconds(301));
        AssessmentRequest next = withTime(request("cmd-3", "dep-3"), clock.instant());
        assertThat(service.assess(next).decision()).isEqualTo(Decision.DENY);
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().state()).isEqualTo(AllocationState.UNCONFIRMED);
    }

    @Test
    void failureRetainsProviderResourceDemandAndNeverFabricatesARefundOrAbsence() {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        ResourceDemand held = repository.allocation("dep-1", "stage").orElseThrow().demand();
        service.outcome(outcome(first, OutcomeStatus.FAILED, NOW.plusSeconds(1)));
        Allocation failed = repository.allocation("dep-1", "stage").orElseThrow();
        assertThat(failed.state()).isEqualTo(AllocationState.UNCONFIRMED);
        assertThat(failed.demand()).isEqualTo(held);
        service.assess(request("cmd-2", "dep-2"));
        assertThat(service.assess(request("cmd-3", "dep-3")).decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void activeOutcomeReleasesOnlyTheConcurrentApplySlot() {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(1)));
        ResourceDemand demand = repository.allocation("dep-1", "stage").orElseThrow().demand();
        assertThat(demand.concurrentApplies()).isZero();
        assertThat(demand.cpuMillis()).isEqualTo(1000L);
        assertThat(demand.databases()).isEqualTo(1L);
    }

    @Test
    void lateActiveOutcomeIsAcceptedAfterHoldExpiryBecauseExpiryIsNotProviderAbsence() {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        clock.set(NOW.plusSeconds(400));
        service.outcome(outcome(first, OutcomeStatus.ACTIVE, clock.instant()));
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().state()).isEqualTo(AllocationState.ACTIVE);
    }

    @Test
    void rejectsCommandReuseWithDifferentFingerprint() {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        AssessmentRequest changed = new AssessmentRequest("1", first.commandId(), first.customerId(), first.tenantId(), first.deploymentId(),
            first.targetProfileId(), first.releaseId(), first.versionId(), first.versionConfigHash(), first.operation(), first.mode(),
            "b".repeat(64), first.resourceDemand(), first.requestedAt());
        assertConflict(() -> service.assess(changed), "COMMAND_PAYLOAD_CONFLICT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerId", "tenantId", "deploymentId", "targetProfileId", "releaseId", "versionId", "versionConfigHash", "mode", "operation", "requestedAt", "resourceDemand"})
    void alsoRejectsPayloadDriftEvenIfCallerReusesSameFingerprint(String field) throws Exception {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        ObjectNode node = mapper.valueToTree(first);
        switch (field) {
            case "mode" -> node.put(field, "OBSERVE");
            case "operation" -> node.put(field, "REPLACE");
            case "versionConfigHash" -> node.put(field, "b".repeat(64));
            case "requestedAt" -> node.put(field, "2026-10-24T14:00:01Z");
            case "resourceDemand" -> ((ObjectNode) node.get(field)).put("cpuMillis", 999L);
            default -> node.put(field, "other-id");
        }
        AssessmentRequest changed = mapper.treeToValue(node, AssessmentRequest.class);
        assertConflict(() -> service.assess(changed), "COMMAND_PAYLOAD_CONFLICT");
    }

    @Test
    void preventsCustomerAndTenantReassignmentAtAnExistingDeploymentTarget() {
        service.assess(request("cmd-1", "dep-1"));
        AssessmentRequest next = request("cmd-2", "dep-1");
        AssessmentRequest changed = new AssessmentRequest("1", next.commandId(), "customer-other", "tenant-other", next.deploymentId(),
            next.targetProfileId(), next.releaseId(), next.versionId(), next.versionConfigHash(), next.operation(), next.mode(),
            next.requestFingerprint(), next.resourceDemand(), next.requestedAt());
        assertConflict(() -> service.assess(changed), "DEPLOYMENT_SCOPE_CONFLICT");
        assertConflict(() -> service.assess(withTarget(changed, "production")), "DEPLOYMENT_SCOPE_CONFLICT");
    }

    @Test
    void exactDuplicateOutcomesAreSafeEvenAfterLaterTerminalObservation() {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        OutcomeRequest active = outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(1));
        service.outcome(active);
        service.outcome(active);
        OutcomeRequest stopped = outcome(first, OutcomeStatus.STOPPED, NOW.plusSeconds(2));
        service.outcome(stopped);
        service.outcome(stopped);
        service.outcome(active);
        assertThat(count("deployment_operations_outcome")).isEqualTo(2);
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().state()).isEqualTo(AllocationState.STOPPED);
        assertConflict(() -> service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(3))), "OUTCOME_TERMINAL");
    }

    @Test
    void rejectsOutcomesForSupersededScopeOrAnUnrelatedFingerprint() {
        AssessmentRequest old = request("cmd-1", "dep-1");
        service.assess(old);
        service.assess(request("cmd-2", "dep-1"));
        assertConflict(() -> service.outcome(outcome(old, OutcomeStatus.STOPPED, NOW.plusSeconds(2))), "OUTCOME_SUPERSEDED");
        assertConflict(() -> service.outcome(new OutcomeRequest("1", "cmd-2", "b".repeat(64), OutcomeStatus.ACTIVE, NOW.plusSeconds(2))),
            "OUTCOME_FINGERPRINT_CONFLICT");
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().state()).isEqualTo(AllocationState.HOLD);
    }

    @Test
    void deniesOutcomeOnEnforcedDenialAndUnknownCommand() {
        AssessmentRequest denied = withDemand(request("cmd-1", "dep-1"), new ResourceDemand(3000L, 1024L, 2048L, 1L, 1L, 0L, 1L, 1L));
        service.assess(denied);
        assertConflict(() -> service.outcome(outcome(denied, OutcomeStatus.ACTIVE, NOW.plusSeconds(1))), "OUTCOME_NOT_ADMITTED");
        assertThatThrownBy(() -> service.outcome(outcome(request("unknown", "dep-2"), OutcomeStatus.ACTIVE, NOW.plusSeconds(1))))
            .isInstanceOfSatisfying(OperationsException.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    @Test
    void rejectsFutureAndStaleTimesButAllowsAConfirmedResolutionAfterFailure() {
        AssessmentRequest first = request("cmd-1", "dep-1");
        service.assess(first);
        assertBadTime(() -> service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.minusSeconds(1))), "OUTCOME_TIME_INVALID");
        assertBadTime(() -> service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(31))), "OUTCOME_TIME_INVALID");
        service.outcome(outcome(first, OutcomeStatus.FAILED, NOW.plusSeconds(2)));
        assertConflict(() -> service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(1))), "OUTCOME_STALE");
        assertConflict(() -> service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(2))), "OUTCOME_STALE");
        service.outcome(outcome(first, OutcomeStatus.ACTIVE, NOW.plusSeconds(3)));
        assertThat(repository.allocation("dep-1", "stage").orElseThrow().state()).isEqualTo(AllocationState.ACTIVE);
    }

    @Test
    void rejectsStaleOrFutureNewAssessmentAndZeroRequestDemand() {
        assertBadTime(() -> service.assess(withTime(request("old", "dep-1"), NOW.minusSeconds(301))), "REQUEST_TIME_INVALID");
        assertBadTime(() -> service.assess(withTime(request("future", "dep-1"), NOW.plusSeconds(31))), "REQUEST_TIME_INVALID");
        assertBadTime(() -> service.assess(withDemand(request("zero", "dep-1"), ResourceDemand.zero())), "RESOURCE_DEMAND_INVALID");
        assertThat(count("deployment_operations_assessment")).isZero();
    }

    @Test
    void concurrentRequestsCannotOversubscribeCapacity() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Decision>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                AssessmentRequest request = request("parallel-" + i, "dep-" + i);
                calls.add(() -> service.assess(request).decision());
            }
            var results = executor.invokeAll(calls);
            List<Decision> decisions = new ArrayList<>();
            for (var result : results) {
                decisions.add(result.get());
            }
            assertThat(decisions.stream().filter(Decision.ALLOW::equals)).hasSize(2);
            assertThat(decisions.stream().filter(Decision.DENY::equals)).hasSize(8);
            assertThat(repository.allocations("stage")).hasSize(2);
        }
    }

    @Test
    void concurrentSameCommandReturnsOnePersistentAssessment() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            AssessmentRequest request = request("same-command", "dep-1");
            List<Callable<AssessmentResponse>> calls = java.util.stream.IntStream.range(0, 6)
                .<Callable<AssessmentResponse>>mapToObj(i -> () -> service.assess(request)).toList();
            var results = executor.invokeAll(calls);
            AssessmentResponse first = results.getFirst().get();
            for (var result : results) {
                assertThat(result.get()).isEqualTo(first);
            }
            assertThat(count("deployment_operations_assessment")).isEqualTo(1);
            assertThat(repository.allocations("stage")).hasSize(1);
        }
    }

    @Test
    void resourceSummationSaturatesInsteadOfWrappingIntoAnAllow() {
        ResourceDemand huge = new ResourceDemand(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
            Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        assertThat(huge.plus(huge)).isEqualTo(huge);
        assertThat(huge.plus(huge).fits(request("unused", "dep-1").resourceDemand())).isFalse();
    }

    static AssessmentRequest request(String command, String deployment) {
        return new AssessmentRequest("1", command, "customer-1", "tenant-1", deployment, "stage", "release-1", "version-1",
            "a".repeat(64), "APPLY", Mode.ENFORCE, "f".repeat(64),
            new ResourceDemand(1000L, 1024L, 2048L, 1L, 1L, 0L, 1L, 1L), NOW);
    }

    static AssessmentRequest withTarget(AssessmentRequest value, String target) {
        return new AssessmentRequest(value.schemaVersion(), value.commandId(), value.customerId(), value.tenantId(), value.deploymentId(),
            target, value.releaseId(), value.versionId(), value.versionConfigHash(), value.operation(), value.mode(),
            value.requestFingerprint(), value.resourceDemand(), value.requestedAt());
    }

    static AssessmentRequest withTime(AssessmentRequest value, Instant time) {
        return new AssessmentRequest(value.schemaVersion(), value.commandId(), value.customerId(), value.tenantId(), value.deploymentId(),
            value.targetProfileId(), value.releaseId(), value.versionId(), value.versionConfigHash(), value.operation(), value.mode(),
            value.requestFingerprint(), value.resourceDemand(), time);
    }

    static AssessmentRequest withDemand(AssessmentRequest value, ResourceDemand demand) {
        return new AssessmentRequest(value.schemaVersion(), value.commandId(), value.customerId(), value.tenantId(), value.deploymentId(),
            value.targetProfileId(), value.releaseId(), value.versionId(), value.versionConfigHash(), value.operation(), value.mode(),
            value.requestFingerprint(), demand, value.requestedAt());
    }

    static OutcomeRequest outcome(AssessmentRequest value, OutcomeStatus status, Instant at) {
        return new OutcomeRequest("1", value.commandId(), value.requestFingerprint(), status, at);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertConflict(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(OperationsException.class, error -> {
            assertThat(error.status()).isEqualTo(409);
            assertThat(error.code()).isEqualTo(code);
        });
    }

    private void assertBadTime(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(OperationsException.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error.code()).isEqualTo(code);
        });
    }
}
