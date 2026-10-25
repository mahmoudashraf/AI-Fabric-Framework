package com.loomai.operations.lifecycle;

import static com.loomai.operations.OperationsTestConfiguration.API_KEY;
import static com.loomai.operations.OperationsTestConfiguration.NOW;
import static com.loomai.operations.lifecycle.LifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.operations.OperationsTestConfiguration;
import com.loomai.operations.OperationsTestConfiguration.MutableClock;
import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.model.OperationsContracts;
import com.loomai.operations.model.OperationsContracts.AllocationState;
import com.loomai.operations.model.OperationsContracts.AssessmentRequest;
import com.loomai.operations.model.OperationsContracts.OutcomeRequest;
import com.loomai.operations.model.OperationsContracts.OutcomeStatus;
import com.loomai.operations.model.OperationsContracts.ResourceDemand;
import com.loomai.operations.persistence.OperationsRepository;
import com.loomai.operations.service.DeploymentOperationsService;
import com.loomai.operations.service.OperationsException;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + API_KEY,
    "spring.datasource.url=jdbc:h2:mem:lifecycle-engine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000"
})
@AutoConfigureMockMvc
@Import({OperationsTestConfiguration.class, LifecycleEngineTests.GatewayConfiguration.class})
class LifecycleEngineH2Test extends LifecycleEngineTests {
    @Autowired MockMvc mvc;

    @Test void authenticatedJobStatusListEventsAndExplicitAdvanceUseTheFrozenWireContract() throws Exception {
        mvc.perform(post("/internal/v1/deployment-operations/lifecycle/jobs").header("Authorization", "Bearer " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request("api", "target", Goal.STOP, Mode.COLD))))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("SCHEDULED"));
        mvc.perform(post("/internal/v1/deployment-operations/lifecycle/jobs/api/advance").header("Authorization", "Bearer " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phase").value("STOP"));
        mvc.perform(get("/internal/v1/deployment-operations/lifecycle/jobs/api").header("Authorization", "Bearer " + API_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.request.grantId").value("grant-api"));
        mvc.perform(get("/internal/v1/deployment-operations/lifecycle/jobs").param("limit", "1").header("Authorization", "Bearer " + API_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.jobs.length()").value(1));
        mvc.perform(get("/internal/v1/deployment-operations/lifecycle/jobs/api/events").param("limit", "1").header("Authorization", "Bearer " + API_KEY))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1)).andExpect(jsonPath("$.nextCursor").isNumber());
    }

    @Test void lifecycleEndpointsRejectBrowserAndAmbiguousAuthenticationAndCallerUris() throws Exception {
        mvc.perform(post("/internal/v1/deployment-operations/lifecycle/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/deployment-operations/lifecycle/jobs").header("Authorization", "Bearer " + API_KEY, "Bearer " + API_KEY))
            .andExpect(status().isUnauthorized());
        ObjectNode json = mapper.valueToTree(request("api", "target", Goal.STOP, Mode.COLD));
        json.put("gatewayBaseUrl", "https://caller.example");
        mvc.perform(post("/internal/v1/deployment-operations/lifecycle/jobs").header("Authorization", "Bearer " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(json)))
            .andExpect(status().isBadRequest());
        mvc.perform(options("/internal/v1/deployment-operations/lifecycle/jobs").header("Origin", "https://browser.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

abstract class LifecycleEngineTests {
    @Autowired LifecycleService service;
    @Autowired LifecycleStore store;
    @Autowired FakeGateway gateway;
    @Autowired HttpLifecycleGateway webhook;
    @Autowired LifecycleProperties properties;
    @Autowired OperationsProperties bounds;
    @Autowired Validator validator;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired OperationsRepository transactions;
    @Autowired DeploymentOperationsService capacity;
    @Autowired ObjectMapper mapper;

    @TestConfiguration static class GatewayConfiguration {
        @Bean @Primary FakeGateway fakeGateway(MutableClock clock) { return new FakeGateway(clock); }
    }

    static class FakeGateway implements LifecycleGateway {
        final Map<String, Receipt> receipts = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> posts = new ConcurrentHashMap<>();
        final AtomicInteger polls = new AtomicInteger();
        final MutableClock clock;
        Function<Command, Receipt> onSubmit;
        Function<Command, Receipt> onObserve;
        FakeGateway(MutableClock clock) { this.clock = clock; reset(); }
        void reset() {
            receipts.clear(); posts.clear(); polls.set(0);
            onSubmit = command -> result(command, Status.SUCCEEDED);
            onObserve = command -> {
                Receipt receipt = receipts.get(command.commandId());
                if (receipt == null) throw new HttpLifecycleGateway.Unavailable();
                return receipt;
            };
        }
        Receipt result(Command command, Status status) {
            return new Receipt("1", command.commandId(), command.operationId(), command.grantId(), command.phase(),
                status, "CORE_" + status, clock.instant(), List.of());
        }
        @Override public Receipt submit(Command command) {
            posts.computeIfAbsent(command.commandId(), ignored -> new AtomicInteger()).incrementAndGet();
            Receipt receipt = onSubmit.apply(command); receipts.put(command.commandId(), receipt); return receipt;
        }
        @Override public Receipt observe(Command command) { polls.incrementAndGet(); return onObserve.apply(command); }
        int posts() { return posts.values().stream().mapToInt(AtomicInteger::get).sum(); }
    }

    @BeforeEach void resetLifecycle() {
        jdbc.update("DELETE FROM deployment_lifecycle_event");
        jdbc.update("DELETE FROM deployment_lifecycle_command");
        jdbc.update("DELETE FROM deployment_lifecycle_target");
        jdbc.update("DELETE FROM deployment_lifecycle_job");
        jdbc.update("DELETE FROM deployment_operations_outcome");
        jdbc.update("DELETE FROM deployment_operations_allocation");
        jdbc.update("DELETE FROM deployment_operations_assessment");
        clock.set(NOW); gateway.reset();
    }

    JobRequest request(String id, String target, Goal goal, Mode mode) {
        return new JobRequest("1", id, "grant-" + id, "customer", "tenant", "deployment", target, "release",
            "a".repeat(64), goal, mode, "policy-v1", NOW, NOW.plusSeconds(3600), NOW);
    }

    LifecycleService restart() { return new LifecycleService(store, gateway, webhook, properties, bounds, validator, clock); }

    void advanceTo(String operationId, Phase phase) {
        int steps = LifecycleStore.phases(store.status(operationId).request()).indexOf(phase);
        assertThat(steps).isNotNegative();
        for (int index = 0; index < steps; index++) service.advance(operationId);
        assertThat(store.status(operationId).phase()).isEqualTo(phase);
    }

    AssessmentRequest capacityRequest(JobRequest job, String commandId) {
        return new AssessmentRequest("1", commandId, job.customerId(), job.tenantId(), job.deploymentId(), job.targetProfileId(),
            job.releaseId(), "version", "b".repeat(64), "APPLY", OperationsContracts.Mode.ENFORCE, "c".repeat(64),
            new ResourceDemand(1000L, 1024L, 4096L, 1L, 1L, 1L, 1L, 1L), clock.instant());
    }

    void activateCapacity(AssessmentRequest request) {
        capacity.assess(request);
        capacity.outcome(new OutcomeRequest("1", request.commandId(), request.requestFingerprint(), OutcomeStatus.ACTIVE, clock.instant()));
    }

    void completeJob(JobRequest job) {
        for (int index = 0; index < LifecycleStore.phases(job).size(); index++) service.advance(job.operationId());
        assertThat(store.status(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
    }

    Instant sqlInstant(String sql, String id) {
        return jdbc.queryForObject(sql, (rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(), id);
    }

    @Test void utcJdbcInstantsPreserveLondonDstOverlapLeasesSchedulesAndAssessmentDates() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        try {
            Instant overlap = Instant.parse("2026-10-25T01:05:00Z");
            clock.set(overlap);
            var job = new JobRequest("1", "dst", "grant-dst", "customer", "tenant", "deployment", "target", "release",
                "a".repeat(64), Goal.STOP, Mode.COLD, "policy", overlap.plusSeconds(60), overlap.plusSeconds(3600), overlap);
            service.register(job);
            assertThat(store.status("dst").createdAt()).isEqualTo(overlap);
            assertThat(store.events("dst", 0, 100).events().getFirst().occurredAt()).isEqualTo(overlap);
            assertThat(sqlInstant("SELECT due_at FROM deployment_lifecycle_job WHERE operation_id=?", "dst")).isEqualTo(job.dueAt());
            assertThat(sqlInstant("SELECT deadline_at FROM deployment_lifecycle_job WHERE operation_id=?", "dst")).isEqualTo(job.deadlineAt());
            assertThat(store.eligible()).doesNotContain("dst");
            assertThat(store.acquire("dst", false)).isEmpty();
            clock.set(job.dueAt());
            assertThat(store.eligible()).contains("dst");
            var lease = store.acquire("dst", false).orElseThrow();
            assertThat(sqlInstant("SELECT lease_until FROM deployment_lifecycle_job WHERE operation_id=?", "dst"))
                .isEqualTo(clock.instant().plus(properties.workerLease()));
            assertThat(store.acquire("dst", false)).isEmpty();
            assertThat(store.markSent(lease)).isTrue();
            store.received(lease, gateway.result(lease.command(), Status.SUCCEEDED));
            assertThat(store.status("dst").phase()).isEqualTo(Phase.STOP);
            assertThat(store.status("dst").updatedAt()).isEqualTo(job.dueAt());

            var assessment = capacityRequest(job, "dst-capacity");
            capacity.assess(assessment);
            var held = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
            assertThat(held.updatedAt()).isEqualTo(clock.instant());
            assertThat(held.holdExpiresAt()).isEqualTo(clock.instant().plus(bounds.holdTtl()));
            assertThat(sqlInstant("SELECT observed_at FROM deployment_operations_assessment WHERE command_id=?", assessment.commandId()))
                .isEqualTo(clock.instant());
            assertThat(sqlInstant("SELECT expires_at FROM deployment_operations_assessment WHERE command_id=?", assessment.commandId()))
                .isEqualTo(held.holdExpiresAt());
            clock.set(clock.instant().plusSeconds(1));
            capacity.outcome(new OutcomeRequest("1", assessment.commandId(), assessment.requestFingerprint(), OutcomeStatus.ACTIVE, clock.instant()));
            var active = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
            assertThat(active.updatedAt()).isEqualTo(clock.instant()).isAfter(held.updatedAt());
            assertThat(sqlInstant("SELECT observed_at FROM deployment_operations_outcome WHERE command_id=?", assessment.commandId()))
                .isEqualTo(clock.instant());
            transactions.expireHolds(held.holdExpiresAt());
            assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow().state()).isEqualTo(AllocationState.ACTIVE);

            var noticeProperties = new LifecycleProperties(false, "", "", false, Duration.ofSeconds(2), Duration.ofSeconds(30),
                Duration.ofHours(24), "https://trusted.example/notices", "trusted-only-notice-key-0000000000000");
            var noticeStore = new LifecycleStore(jdbc, transactions, mapper, noticeProperties, clock);
            var noticeJob = new JobRequest("1", "dst-notice", "grant-dst-notice", "customer", "tenant", "deployment", "notice-target", "release",
                "a".repeat(64), Goal.STOP, Mode.COLD, "policy", clock.instant(), job.deadlineAt(), clock.instant());
            noticeStore.register(noticeJob, () -> { });
            var delivery = noticeStore.claimDelivery().orElseThrow();
            assertThat(delivery.event().occurredAt()).isEqualTo(clock.instant());
            assertThat(sqlInstant("SELECT lease_until FROM deployment_lifecycle_event WHERE event_id=?", delivery.event().eventId()))
                .isEqualTo(clock.instant().plus(noticeProperties.workerLease()));
            assertThat(noticeStore.claimDelivery()).isEmpty();
            noticeStore.delivered(delivery, true);
            assertThat(jdbc.queryForObject("SELECT delivery FROM deployment_lifecycle_event WHERE event_id=?", String.class, delivery.event().eventId()))
                .isEqualTo("DELIVERED");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest @EnumSource(value = Goal.class, names = {"STOP", "RETIRE"})
    void verifiedReleaseSettlesOnlyItsPinnedAllocationWithRetainedOrReclaimedDemandAndImmutableHistory(Goal goal) {
        var job = request("settlement", "target", goal, Mode.COLD);
        var assessment = capacityRequest(job, "capacity-original");
        activateCapacity(assessment);
        var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        var history = transactions.assessment(assessment.commandId()).orElseThrow();
        service.register(job);
        assertThat(jdbc.queryForObject("SELECT capacity_command_id FROM deployment_lifecycle_job WHERE operation_id=?", String.class, job.operationId()))
            .isEqualTo(assessment.commandId());
        advanceTo(job.operationId(), Phase.RELEASE);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
        gateway.onSubmit = command -> {
            gateway.receipts.put(command.commandId(), gateway.result(command, Status.SUCCEEDED));
            throw new HttpLifecycleGateway.Unavailable();
        };
        var unknown = service.advance(job.operationId());
        assertThat(unknown.state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
        assertThat(restart().advance(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
        var settled = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        assertThat(settled.state()).isEqualTo(AllocationState.STOPPED);
        assertThat(settled.demand()).isEqualTo(goal == Goal.STOP ? before.demand().retainedAfterStop() : ResourceDemand.zero());
        assertThat(settled.commandId()).isEqualTo(before.commandId());
        assertThat(settled.holdExpiresAt()).isEqualTo(before.holdExpiresAt());
        capacity.outcome(history.lastOutcome());
        assertThatThrownBy(() -> capacity.outcome(new OutcomeRequest("1", assessment.commandId(), assessment.requestFingerprint(),
            OutcomeStatus.ACTIVE, clock.instant().plusSeconds(1))))
            .isInstanceOfSatisfying(OperationsException.class, error -> assertThat(error.code()).isEqualTo("OUTCOME_TERMINAL"));
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(settled);
        assertThat(transactions.assessment(assessment.commandId())).contains(history);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_operations_outcome", Integer.class)).isEqualTo(1);
        restart().register(job); restart().advance(job.operationId());
        assertThat(store.events(job.operationId(), 0, 100).events()).filteredOn(event -> event.type().equals("CAPACITY_SETTLED"))
            .singleElement().satisfies(event -> {
                assertThat(event.state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
                assertThat(event.phase()).isEqualTo(Phase.RELEASE);
                assertThat(event.code()).isEqualTo(goal == Goal.STOP ? "CAPACITY_STOPPED_RETAINED" : "CAPACITY_RETIRED_RECLAIMED");
            });
        assertThat(gateway.posts.get(unknown.command().commandId())).hasValue(1);
    }

    @ParameterizedTest @ValueSource(strings = {"customerId", "tenantId", "deploymentId", "targetProfileId", "releaseId"})
    void lifecycleCompletionCannotSettleForeignCapacityOrADifferentRelease(String field) throws Exception {
        var job = request("foreign-capacity", "target", Goal.RETIRE, Mode.COLD);
        ObjectNode json = mapper.valueToTree(capacityRequest(job, "foreign-assessment"));
        json.put(field, "foreign");
        var assessment = mapper.treeToValue(json, AssessmentRequest.class);
        activateCapacity(assessment);
        var before = transactions.allocation(assessment.deploymentId(), assessment.targetProfileId()).orElseThrow();
        service.register(job); completeJob(job);
        assertThat(transactions.allocation(assessment.deploymentId(), assessment.targetProfileId())).contains(before);
        assertThat(store.events(job.operationId(), 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
    }

    @ParameterizedTest @ValueSource(strings = {"release", "new-release"})
    void lifecycleCompletionNeverReducesASupersedingCapacityCommandEvenForTheSameRelease(String release) throws Exception {
        var job = request("superseded-capacity", "target", Goal.RETIRE, Mode.COLD);
        var original = capacityRequest(job, "capacity-original"); activateCapacity(original);
        service.register(job);
        ObjectNode json = mapper.valueToTree(capacityRequest(job, "capacity-superseding"));
        json.put("releaseId", release);
        var superseding = mapper.treeToValue(json, AssessmentRequest.class); activateCapacity(superseding);
        var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        completeJob(job);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
        assertThat(store.events(job.operationId(), 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingOrLegacyCapacityCorrelationCannotBeBackfilledByJobReplay(boolean legacy) {
        var job = request("uncorrelated-capacity", "target", Goal.RETIRE, Mode.COLD);
        var assessment = capacityRequest(job, "uncorrelated-assessment");
        if (legacy) activateCapacity(assessment);
        service.register(job);
        if (legacy) jdbc.update("UPDATE deployment_lifecycle_job SET capacity_command_id=NULL WHERE operation_id=?", job.operationId());
        else activateCapacity(assessment);
        var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        restart().register(job); completeJob(job);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
        assertThat(store.events(job.operationId(), 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
    }

    @ParameterizedTest @EnumSource(value = Goal.class, names = {"RECOVER", "RECONCILE"})
    void recoveryAndReconciliationCompletionNeverDecreaseCapacity(Goal goal) {
        var job = request("nonreducing-capacity", "target", goal, Mode.COLD);
        var assessment = capacityRequest(job, "capacity-original"); activateCapacity(assessment);
        var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        service.register(job); completeJob(job);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
        assertThat(store.events(job.operationId(), 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
    }

    @ParameterizedTest @EnumSource(value = Phase.class, names = {"STOP", "DELETE", "VERIFY", "RELEASE"})
    void incompleteNativeWorkCannotGiveBackCapacity(Phase phase) {
        for (Status status : List.of(Status.PENDING, Status.UNKNOWN, Status.BLOCKED, Status.FAILED)) {
            String id = "incomplete-" + phase + "-" + status;
            var job = request(id, id, Goal.RETIRE, Mode.COLD);
            var assessment = capacityRequest(job, "capacity-" + id); activateCapacity(assessment);
            var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
            service.register(job); advanceTo(id, phase);
            gateway.onSubmit = command -> gateway.result(command, command.phase() == phase ? status : Status.SUCCEEDED);
            service.advance(id);
            assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
            assertThat(store.events(id, 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
        }
    }

    @Test void capacitySettlementAndItsEventCommitAtomicallyWithReleaseAndRetryTheSameReceiptAfterFailure() {
        var job = request("atomic-settlement", "target", Goal.RETIRE, Mode.COLD);
        var assessment = capacityRequest(job, "capacity-original"); activateCapacity(assessment);
        var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        service.register(job); advanceTo(job.operationId(), Phase.RELEASE);
        jdbc.execute("ALTER TABLE deployment_lifecycle_event ADD CONSTRAINT reject_capacity_settlement CHECK (event_type <> 'CAPACITY_SETTLED')");
        try {
            assertThat(service.advance(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
            assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
            assertThat(store.events(job.operationId(), 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
        } finally {
            jdbc.execute("ALTER TABLE deployment_lifecycle_event DROP CONSTRAINT reject_capacity_settlement");
        }
        assertThat(restart().advance(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow().demand()).isEqualTo(ResourceDemand.zero());
        assertThat(store.events(job.operationId(), 0, 100).events()).filteredOn(event -> event.type().equals("CAPACITY_SETTLED")).hasSize(1);
    }

    @ParameterizedTest @EnumSource(Goal.class)
    void exactWorkflowCompletesOnlyAfterEveryMatchingCorePhaseIncludingRelease(Goal goal) {
        for (Mode mode : Mode.values()) {
            String id = goal + "-" + mode;
            JobRequest request = request(id, id, goal, mode); service.register(request);
            for (Phase phase : LifecycleStore.phases(request)) {
                assertThat(store.status(id).phase()).isEqualTo(phase);
                service.advance(id);
            }
            assertThat(store.status(id).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
            assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command WHERE operation_id=? ORDER BY phase", String.class, id))
                .containsExactlyInAnyOrderElementsOf(LifecycleStore.phases(request).stream().map(Enum::name).toList());
            assertThat(gateway.posts.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        }
    }

    @Test void duplicateAfterRestartAndExpiryReturnsOriginalJobWithoutRenewal() {
        JobRequest request = request("duplicate", "target", Goal.STOP, Mode.COLD);
        var original = service.register(request);
        clock.set(NOW.plusSeconds(7200));
        assertThat(restart().register(request)).isEqualTo(original);
        assertThat(gateway.posts()).isZero();
        assertThat(store.events("duplicate", 0, 100).events()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"grantId", "customerId", "tenantId", "deploymentId", "targetProfileId", "releaseId", "scopeHash", "goal", "mode", "policyRevision", "dueAt", "deadlineAt", "requestedAt"})
    void sameOperationCannotChangeAnyFrozenAuthorityOrScheduleField(String field) throws Exception {
        JobRequest request = request("duplicate", "target", Goal.STOP, Mode.COLD); service.register(request);
        ObjectNode changed = mapper.valueToTree(request);
        changed.put(field, switch (field) {
            case "scopeHash" -> "b".repeat(64); case "goal" -> "RETIRE"; case "mode" -> "GRACEFUL";
            case "dueAt", "deadlineAt", "requestedAt" -> NOW.plusSeconds(10).toString(); default -> "changed";
        });
        assertThatThrownBy(() -> service.register(mapper.treeToValue(changed, JobRequest.class)))
            .isInstanceOfSatisfying(OperationsException.class, error -> assertThat(error.code()).isEqualTo("LIFECYCLE_JOB_CONFLICT"));
        assertThat(store.status("duplicate").request()).isEqualTo(request);
    }

    @Test void gatewayAndProviderEndpointsAreNeverRequestFieldsAndTimeWindowsAreBounded() {
        JobRequest request = request("invalid", "target", Goal.STOP, Mode.COLD);
        assertThatThrownBy(() -> service.register(new JobRequest("1", "invalid", request.grantId(), "customer", "tenant", "deployment",
            "target", "release", request.scopeHash(), Goal.STOP, Mode.COLD, "policy", NOW, NOW, NOW))).isInstanceOf(OperationsException.class);
        clock.set(NOW.plusSeconds(301));
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(OperationsException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_job", Long.class)).isZero();
    }

    @Test void cancelledOrCompletedGrantsCannotBeRecycledIntoAnotherJob() {
        service.register(request("old-grant", "target", Goal.RECONCILE, Mode.COLD));
        for (int index = 0; index < 3; index++) service.advance("old-grant");
        ObjectNode reused = mapper.valueToTree(request("new-operation", "target", Goal.RECONCILE, Mode.COLD));
        reused.put("grantId", "grant-old-grant");
        assertThatThrownBy(() -> service.register(mapper.convertValue(reused, JobRequest.class)))
            .isInstanceOfSatisfying(OperationsException.class, error -> assertThat(error.code()).isEqualTo("LIFECYCLE_GRANT_REUSE"));
        assertThat(service.register(request("new-operation", "target", Goal.RECONCILE, Mode.COLD))).isNotNull();
    }

    @Test void lostPostResponsePollsTheSameCoreCommandAcrossRestartWithoutRedispatch() {
        service.register(request("lost", "target", Goal.STOP, Mode.COLD));
        gateway.onSubmit = command -> {
            gateway.receipts.put(command.commandId(), gateway.result(command, Status.SUCCEEDED));
            throw new HttpLifecycleGateway.Unavailable();
        };
        var unknown = service.advance("lost");
        assertThat(unknown.state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
        Command command = unknown.command();
        assertThat(restart().advance("lost").phase()).isEqualTo(Phase.STOP);
        assertThat(gateway.posts()).isEqualTo(1);
        assertThat(gateway.polls).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT command_id FROM deployment_lifecycle_command WHERE phase='CLAIM'", String.class)).isEqualTo(command.commandId());
    }

    @Test void crashWithPreparedButUnsentCommandCanContinueAfterLeaseExpiry() {
        service.register(request("prepared", "target", Goal.STOP, Mode.COLD));
        var lease = store.acquire("prepared", false).orElseThrow();
        restart().advance("prepared"); assertThat(gateway.posts()).isZero();
        clock.set(NOW.plusSeconds(31));
        restart().advance("prepared");
        assertThat(gateway.posts.get(lease.command().commandId())).hasValue(1);
        assertThat(store.status("prepared").phase()).isEqualTo(Phase.STOP);
    }

    @Test void dispatchIntentWithoutAResponseOrCoreRecordNeverBecomesANewPost() {
        service.register(request("crash", "target", Goal.STOP, Mode.COLD));
        var lease = store.acquire("crash", false).orElseThrow(); assertThat(store.markSent(lease)).isTrue();
        clock.set(NOW.plusSeconds(31));
        for (int index = 0; index < 3; index++) restart().advance("crash");
        assertThat(gateway.posts()).isZero(); assertThat(gateway.polls).hasValue(3);
        assertThat(store.status("crash").command()).isEqualTo(lease.command());
        assertThat(store.status("crash").state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_command", Long.class)).isEqualTo(1);
    }

    @Test void staleWorkerCannotOverwriteAReplacementWorkersReceipt() {
        service.register(request("lease", "target", Goal.STOP, Mode.COLD));
        var old = store.acquire("lease", false).orElseThrow(); store.markSent(old);
        gateway.receipts.put(old.command().commandId(), gateway.result(old.command(), Status.SUCCEEDED));
        clock.set(NOW.plusSeconds(31)); restart().advance("lease");
        store.received(old, gateway.result(old.command(), Status.FAILED));
        assertThat(store.status("lease").phase()).isEqualTo(Phase.STOP);
        assertThat(store.events("lease", 0, 100).events()).noneMatch(event -> event.type().equals("CORE_FAILED"));
    }

    @Test void concurrentAdvancesHaveOneDispatchOwnerAndDoNotHoldTheDatabaseLockDuringIo() throws Exception {
        service.register(request("concurrent", "target", Goal.STOP, Mode.COLD));
        service.register(request("other", "other-target", Goal.STOP, Mode.COLD));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        gateway.onSubmit = command -> {
            if (command.operationId().equals("concurrent")) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            }
            return gateway.result(command, Status.SUCCEEDED);
        };
        try (var pool = Executors.newFixedThreadPool(3)) {
            var first = pool.submit(() -> service.advance("concurrent"));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(() -> service.advance("concurrent")).get(2, TimeUnit.SECONDS).state()).isEqualTo(LifecycleStore.JobState.RUNNING);
            assertThat(pool.submit(() -> service.advance("other")).get(2, TimeUnit.SECONDS).phase()).isEqualTo(Phase.STOP);
            release.countDown(); first.get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(gateway.posts.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    @Test void blockedNeedsExplicitSameCommandPostForCoreReobservationNotANewCommand() {
        service.register(request("blocked", "target", Goal.STOP, Mode.COLD));
        gateway.onSubmit = command -> gateway.result(command, Status.BLOCKED);
        var blocked = service.advance("blocked");
        restart().advance("blocked"); assertThat(gateway.polls).hasValue(0);
        gateway.receipts.put(blocked.command().commandId(), gateway.result(blocked.command(), Status.SUCCEEDED));
        gateway.onSubmit = command -> gateway.result(command, Status.SUCCEEDED);
        assertThat(restart().retry("blocked").phase()).isEqualTo(Phase.STOP);
        assertThat(gateway.posts()).isEqualTo(2); assertThat(gateway.polls).hasValue(0);
        assertThat(gateway.posts).containsOnlyKeys(blocked.command().commandId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"commandId", "operationId", "grantId", "phase", "schemaVersion", "observedAt", "code", "blockers"})
    void coreScopeOrMalformedReceiptCannotAdvanceTheWorkflow(String field) {
        service.register(request("mismatch", "target", Goal.STOP, Mode.COLD));
        gateway.onSubmit = command -> {
            ObjectNode forged = mapper.valueToTree(gateway.result(command, Status.SUCCEEDED));
            switch (field) {
                case "phase" -> forged.put(field, "DELETE");
                case "schemaVersion" -> forged.put(field, "2");
                case "observedAt" -> forged.put(field, NOW.plusSeconds(31).toString());
                case "code" -> forged.put(field, "untrusted details");
                case "blockers" -> forged.putArray(field).add("x".repeat(257));
                default -> forged.put(field, "foreign");
            }
            return mapper.convertValue(forged, Receipt.class);
        };
        var result = service.advance("mismatch");
        assertThat(result.state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
        assertThat(result.phase()).isEqualTo(Phase.CLAIM);
        assertThat(result.receipt()).isNull();
    }

    @Test void uncertainBlockedReplayIsPolledBeforeCancellationDespiteTheOlderBlockedReceipt() {
        service.register(request("retry-lost", "target", Goal.STOP, Mode.GRACEFUL));
        service.advance("retry-lost");
        gateway.onSubmit = command -> gateway.result(command, Status.BLOCKED);
        Command original = service.advance("retry-lost").command();
        assertThat(original.phase()).isEqualTo(Phase.BEGIN_DRAIN);
        gateway.onSubmit = command -> { throw new HttpLifecycleGateway.Unavailable(); };
        assertThat(service.retry("retry-lost").state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
        gateway.onObserve = command -> { throw new HttpLifecycleGateway.Unavailable(); };
        var result = restart().cancel("retry-lost");
        assertThat(result.command()).isEqualTo(original);
        assertThat(result.state()).isEqualTo(LifecycleStore.JobState.UNKNOWN);
        assertThat(gateway.posts()).isEqualTo(3);
        assertThat(gateway.polls).hasValue(1);
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command", String.class))
            .containsExactlyInAnyOrder("CLAIM", "BEGIN_DRAIN");
    }

    @ParameterizedTest @EnumSource(value = Phase.class, names = {"CLAIM", "OBSERVE_DRAIN", "OBSERVE"})
    void pendingOrUnknownLocalAndReadOnlyCommandsDoNotDelayAcknowledgedCancellation(Phase phase) throws Exception {
        for (Status status : List.of(Status.PENDING, Status.UNKNOWN)) {
            String id = phase + "-" + status;
            service.register(request(id, id, phase == Phase.OBSERVE ? Goal.RECONCILE : Goal.STOP,
                phase == Phase.OBSERVE_DRAIN ? Mode.GRACEFUL : Mode.COLD));
            advanceTo(id, phase);
            gateway.onSubmit = command -> gateway.result(command, command.phase() == phase ? status : Status.SUCCEEDED);
            var pending = service.advance(id);
            assertThat(pending.phase()).isEqualTo(phase);
            assertThat(pending.receipt().status()).isEqualTo(status);
            gateway.onObserve = command -> { throw new AssertionError("Cancellation must not wait for read-only/local work"); };
            var cancelled = restart().cancel(id);
            assertThat(cancelled.state()).isEqualTo(LifecycleStore.JobState.CANCELLED);
            assertThat(cancelled.phase()).isEqualTo(Phase.CANCEL);
            assertThat(cancelled.irreversible()).isFalse();
            assertThat(restart().cancel(id)).isEqualTo(cancelled);
            assertThat(gateway.posts.get(pending.command().commandId())).hasValue(1);
            assertThat(gateway.posts.get(cancelled.command().commandId())).hasValue(1);
            assertThat(mapper.readValue(jdbc.queryForObject("SELECT receipt_json FROM deployment_lifecycle_command WHERE command_id=?",
                String.class, pending.command().commandId()), Receipt.class)).isEqualTo(pending.receipt());
            assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command WHERE operation_id=?", String.class, id))
                .doesNotContain("FENCE", "STOP", "DELETE", "RECOVER", "RELEASE");
        }
        assertThat(gateway.polls).hasValue(0);
    }

    @ParameterizedTest @EnumSource(value = Phase.class, names = {"CLAIM", "OBSERVE_DRAIN", "OBSERVE"})
    void unobservedLocalAndReadOnlyIntentsCanCancelAfterDeadlineWithoutAcceptingLateReceipts(Phase phase) {
        String id = "cancel-late-" + phase;
        service.register(request(id, id, phase == Phase.OBSERVE ? Goal.RECONCILE : Goal.STOP,
            phase == Phase.OBSERVE_DRAIN ? Mode.GRACEFUL : Mode.COLD));
        advanceTo(id, phase);
        var old = store.acquire(id, false).orElseThrow();
        assertThat(store.markSent(old)).isTrue();
        clock.set(NOW.plusSeconds(3601));
        gateway.onSubmit = command -> {
            assertThat(command.phase()).isEqualTo(Phase.CANCEL);
            return gateway.result(command, Status.PENDING);
        };
        var cancelling = restart().cancel(id);
        assertThat(cancelling.state()).isEqualTo(LifecycleStore.JobState.CANCEL_PENDING);
        assertThat(cancelling.phase()).isEqualTo(Phase.CANCEL);
        assertThat(jdbc.queryForObject("SELECT operation_id FROM deployment_lifecycle_target WHERE target_profile_id=?", String.class, id))
            .isEqualTo(id);
        store.received(old, gateway.result(old.command(), Status.SUCCEEDED));
        assertThat(store.status(id)).isEqualTo(cancelling);
        assertThat(jdbc.queryForObject("SELECT receipt_json FROM deployment_lifecycle_command WHERE command_id=?",
            String.class, old.command().commandId())).isNull();
        gateway.receipts.put(cancelling.command().commandId(), gateway.result(cancelling.command(), Status.SUCCEEDED));
        gateway.onObserve = command -> {
            assertThat(command).isEqualTo(cancelling.command());
            return gateway.receipts.get(command.commandId());
        };
        var cancelled = restart().advance(id);
        assertThat(cancelled.state()).isEqualTo(LifecycleStore.JobState.CANCELLED);
        assertThat(cancelled.irreversible()).isFalse();
        assertThat(gateway.posts).doesNotContainKey(old.command().commandId());
        assertThat(gateway.posts.get(cancelling.command().commandId())).hasValue(1);
        assertThat(gateway.polls).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT operation_id FROM deployment_lifecycle_target WHERE target_profile_id=?", String.class, id))
            .isNull();
    }

    @Test void explicitBlockedCancelRetryUsesTheSameCommandEvenAfterDeadlineAndDoesNotAutoRetry() {
        service.register(request("cancel-blocked", "target", Goal.STOP, Mode.COLD));
        gateway.onSubmit = command -> gateway.result(command, Status.BLOCKED);
        Command cancel = service.cancel("cancel-blocked").command();
        assertThat(service.advance("cancel-blocked").state()).isEqualTo(LifecycleStore.JobState.BLOCKED);
        assertThat(gateway.posts()).isEqualTo(1); assertThat(gateway.polls).hasValue(0);
        clock.set(NOW.plusSeconds(3601));
        gateway.onSubmit = command -> gateway.result(command, Status.SUCCEEDED);
        assertThat(service.retry("cancel-blocked").state()).isEqualTo(LifecycleStore.JobState.CANCELLED);
        assertThat(gateway.posts).containsOnlyKeys(cancel.commandId());
        assertThat(gateway.posts.get(cancel.commandId())).hasValue(2);
    }

    @Test void expiredBlockedCommandRetryIsReadbackOnlyAndCannotRenewItsGrant() {
        service.register(request("expired-blocked", "target", Goal.STOP, Mode.COLD));
        gateway.onSubmit = command -> gateway.result(command, Status.BLOCKED);
        Command original = service.advance("expired-blocked").command();
        clock.set(NOW.plusSeconds(3601)); service.retry("expired-blocked");
        assertThat(gateway.posts()).isEqualTo(1); assertThat(gateway.polls).hasValue(1);
        assertThat(store.status("expired-blocked").command()).isEqualTo(original);
        assertThat(store.status("expired-blocked").request().deadlineAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test void deadlineBlocksNewEffectsButAcknowledgedCancellationAllowsFreshAuthorizedResubmission() {
        service.register(request("deadline", "target", Goal.RETIRE, Mode.GRACEFUL));
        clock.set(NOW.plusSeconds(3601));
        assertThat(service.advance("deadline").code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(gateway.posts()).isZero();
        gateway.onSubmit = command -> gateway.result(command, Status.PENDING);
        var cancelling = service.cancel("deadline");
        assertThat(cancelling.state()).isEqualTo(LifecycleStore.JobState.CANCEL_PENDING);
        assertThatThrownBy(() -> service.register(request("new", "target", Goal.STOP, Mode.COLD))).isInstanceOf(OperationsException.class);
        gateway.receipts.put(cancelling.command().commandId(), gateway.result(cancelling.command(), Status.SUCCEEDED));
        assertThat(restart().advance("deadline").state()).isEqualTo(LifecycleStore.JobState.CANCELLED);
        clock.set(NOW); assertThat(service.register(request("new", "target", Goal.STOP, Mode.COLD)).state()).isEqualTo(LifecycleStore.JobState.SCHEDULED);
        assertThat(store.status("deadline").request().grantId()).isEqualTo("grant-deadline");
    }

    @Test void unknownDrainIsObservedBeforeCancellationAndNeverDiscarded() {
        service.register(request("cancel", "target", Goal.STOP, Mode.GRACEFUL)); service.advance("cancel");
        gateway.onSubmit = command -> gateway.result(command, Status.PENDING);
        var draining = service.advance("cancel");
        assertThat(draining.phase()).isEqualTo(Phase.BEGIN_DRAIN);
        service.cancel("cancel"); assertThat(gateway.posts()).isEqualTo(2);
        assertThat(store.status("cancel").command()).isEqualTo(draining.command());
        gateway.receipts.put(draining.command().commandId(), gateway.result(draining.command(), Status.SUCCEEDED));
        service.advance("cancel");
        gateway.onSubmit = command -> gateway.result(command, Status.SUCCEEDED);
        assertThat(service.advance("cancel").state()).isEqualTo(LifecycleStore.JobState.CANCELLED);
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command", String.class)).containsExactlyInAnyOrder("CLAIM", "BEGIN_DRAIN", "CANCEL");
    }

    @Test void irreversibleUnknownStopSurvivesDeadlineAndCannotCancelOrDelete() {
        service.register(request("stopping", "target", Goal.RETIRE, Mode.COLD)); service.advance("stopping");
        gateway.onSubmit = command -> { throw new HttpLifecycleGateway.Unavailable(); };
        var stopping = service.advance("stopping"); assertThat(stopping.irreversible()).isTrue();
        clock.set(NOW.plusSeconds(3601));
        assertThatThrownBy(() -> service.cancel("stopping")).isInstanceOf(OperationsException.class);
        service.advance("stopping"); assertThat(gateway.posts()).isEqualTo(2);
        gateway.receipts.put(stopping.command().commandId(), gateway.result(stopping.command(), Status.SUCCEEDED));
        service.advance("stopping");
        assertThat(service.advance("stopping").code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command", String.class)).doesNotContain("DELETE", "CANCEL", "RELEASE");
    }

    @ParameterizedTest @EnumSource(value = Goal.class, names = {"STOP", "RETIRE"})
    void acceptedNativeCompletionAfterDeadlineAllowsOnlyMatchingVerificationAndRelease(Goal goal) {
        var job = request("late-native", "target", goal, Mode.COLD); service.register(job);
        Phase lastNative = goal == Goal.RETIRE ? Phase.DELETE : Phase.STOP;
        advanceTo(job.operationId(), lastNative);
        gateway.onSubmit = command -> gateway.result(command, Status.PENDING);
        var pending = service.advance(job.operationId());
        Command nativeCommand = pending.command(); int posts = gateway.posts();
        clock.set(job.deadlineAt().plusSeconds(1));
        gateway.onObserve = command -> {
            assertThat(command).isEqualTo(nativeCommand);
            return gateway.result(command, Status.SUCCEEDED);
        };
        assertThat(restart().advance(job.operationId()).phase()).isEqualTo(Phase.VERIFY);
        assertThat(gateway.posts()).isEqualTo(posts);
        assertThat(gateway.polls).hasValue(1);
        gateway.onSubmit = command -> {
            assertThat(command.phase()).isIn(Phase.VERIFY, Phase.RELEASE);
            assertThat(command.operationId()).isEqualTo(job.operationId());
            assertThat(command.grantId()).isEqualTo(job.grantId());
            assertThat(command.scopeHash()).isEqualTo(job.scopeHash());
            return gateway.result(command, Status.SUCCEEDED);
        };
        assertThat(restart().advance(job.operationId()).phase()).isEqualTo(Phase.RELEASE);
        assertThat(restart().advance(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
        assertThat(store.status(job.operationId()).request()).isEqualTo(job);
        assertThat(gateway.posts()).isEqualTo(posts + 2);
        assertThat(gateway.posts.get(nativeCommand.commandId())).hasValue(1);
    }

    @ParameterizedTest @EnumSource(value = Status.class, names = {"PENDING", "UNKNOWN"})
    void unresolvedDeleteAfterDeadlineCannotPrepareVerificationOrSettleCapacity(Status status) {
        var job = request("unresolved-delete", "target", Goal.RETIRE, Mode.COLD);
        var assessment = capacityRequest(job, "capacity-unresolved"); activateCapacity(assessment);
        var before = transactions.allocation(job.deploymentId(), job.targetProfileId()).orElseThrow();
        service.register(job); advanceTo(job.operationId(), Phase.DELETE);
        gateway.onSubmit = command -> gateway.result(command, status);
        var pending = service.advance(job.operationId()); int posts = gateway.posts();
        clock.set(job.deadlineAt().plusSeconds(1));
        for (int index = 0; index < 2; index++) {
            assertThat(restart().advance(job.operationId()).command()).isEqualTo(pending.command());
        }
        assertThat(gateway.posts()).isEqualTo(posts);
        assertThat(gateway.polls).hasValue(2);
        assertThat(transactions.allocation(job.deploymentId(), job.targetProfileId())).contains(before);
        assertThat(store.events(job.operationId(), 0, 100).events()).noneMatch(event -> event.type().equals("CAPACITY_SETTLED"));
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command WHERE operation_id=?", String.class, job.operationId()))
            .doesNotContain("VERIFY", "RELEASE");
    }

    @ParameterizedTest @EnumSource(value = Phase.class, names = {"DELETE", "FENCE", "RECOVER"})
    void newMutationsRemainBlockedAfterDeadline(Phase phase) {
        var job = request("late-new-mutation", "target", phase == Phase.RECOVER ? Goal.RECOVER : phase == Phase.DELETE ? Goal.RETIRE : Goal.STOP,
            phase == Phase.FENCE ? Mode.GRACEFUL : Mode.COLD);
        service.register(job); advanceTo(job.operationId(), phase);
        int posts = gateway.posts(); clock.set(job.deadlineAt());
        assertThat(service.advance(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(restart().retry(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(gateway.posts()).isEqualTo(posts);
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command WHERE operation_id=?", String.class, job.operationId()))
            .doesNotContain(phase.name());
        assertThat(store.status(job.operationId()).request()).isEqualTo(job);
    }

    @Test void preparedMutationCannotCrossTheDeadlineBetweenAcquisitionAndDispatch() {
        var job = request("prepared-at-deadline", "target", Goal.RETIRE, Mode.COLD);
        service.register(job); advanceTo(job.operationId(), Phase.DELETE);
        clock.set(job.deadlineAt().minusSeconds(1));
        var prepared = store.acquire(job.operationId(), false).orElseThrow();
        assertThat(prepared.submit()).isTrue();
        clock.set(job.deadlineAt());
        assertThat(store.markSent(prepared)).isFalse();
        assertThat(store.status(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT delivery FROM deployment_lifecycle_command WHERE command_id=?", String.class,
            prepared.command().commandId())).isEqualTo("READY");
        assertThat(gateway.posts).doesNotContainKey(prepared.command().commandId());
    }

    @Test void newRecoveryVerificationRemainsBlockedAfterDeadline() {
        var job = request("late-recovery-verification", "target", Goal.RECOVER, Mode.COLD);
        service.register(job); advanceTo(job.operationId(), Phase.VERIFY);
        int posts = gateway.posts(); clock.set(job.deadlineAt().plusSeconds(1));
        assertThat(service.advance(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(gateway.posts()).isEqualTo(posts);
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command WHERE operation_id=?", String.class, job.operationId()))
            .doesNotContain("VERIFY", "RELEASE");
    }

    @ParameterizedTest @EnumSource(value = Goal.class, names = {"STOP", "RECONCILE"})
    void lateReleaseRequiresMatchingVerifyOrObserveProofAndBlockedJobsStillNeedExplicitRetry(Goal goal) throws Exception {
        var job = request("late-proof", "target", goal, Mode.COLD); service.register(job); advanceTo(job.operationId(), Phase.RELEASE);
        Phase proofPhase = goal == Goal.RECONCILE ? Phase.OBSERVE : Phase.VERIFY;
        String proof = jdbc.queryForObject("SELECT receipt_json FROM deployment_lifecycle_command WHERE operation_id=? AND phase=?",
            String.class, job.operationId(), proofPhase.name());
        ObjectNode foreign = (ObjectNode) mapper.readTree(proof); foreign.put("grantId", "foreign-grant");
        jdbc.update("UPDATE deployment_lifecycle_command SET receipt_json=? WHERE operation_id=? AND phase=?",
            mapper.writeValueAsString(foreign), job.operationId(), proofPhase.name());
        int posts = gateway.posts(); clock.set(job.deadlineAt().plusSeconds(1));
        assertThat(service.advance(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(gateway.posts()).isEqualTo(posts);
        jdbc.update("UPDATE deployment_lifecycle_command SET receipt_json=? WHERE operation_id=? AND phase=?", proof, job.operationId(), proofPhase.name());
        assertThat(restart().advance(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.BLOCKED);
        assertThat(gateway.posts()).isEqualTo(posts);
        assertThat(restart().retry(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
        assertThat(gateway.posts()).isEqualTo(posts + 1);
        assertThat(store.status(job.operationId()).request()).isEqualTo(job);
    }

    @Test void lateRetirementVerificationRejectsForeignScopeAndUnsettledDeleteProof() throws Exception {
        var job = request("late-delete-proof", "target", Goal.RETIRE, Mode.COLD);
        service.register(job); advanceTo(job.operationId(), Phase.VERIFY);
        String command = jdbc.queryForObject("SELECT command_json FROM deployment_lifecycle_command WHERE operation_id=? AND phase='DELETE'",
            String.class, job.operationId());
        ObjectNode foreign = (ObjectNode) mapper.readTree(command); foreign.put("scopeHash", "f".repeat(64));
        jdbc.update("UPDATE deployment_lifecycle_command SET command_json=? WHERE operation_id=? AND phase='DELETE'",
            mapper.writeValueAsString(foreign), job.operationId());
        int posts = gateway.posts(); clock.set(job.deadlineAt().plusSeconds(1));
        assertThat(service.advance(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        jdbc.update("UPDATE deployment_lifecycle_command SET command_json=?,observation_pending=TRUE WHERE operation_id=? AND phase='DELETE'",
            command, job.operationId());
        assertThat(service.retry(job.operationId()).code()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(gateway.posts()).isEqualTo(posts);
        jdbc.update("UPDATE deployment_lifecycle_command SET observation_pending=FALSE WHERE operation_id=? AND phase='DELETE'", job.operationId());
        assertThat(service.retry(job.operationId()).phase()).isEqualTo(Phase.RELEASE);
        assertThat(service.advance(job.operationId()).state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
    }

    @ParameterizedTest @EnumSource(value = Phase.class, names = {"VERIFY", "RELEASE"})
    void acceptedBlockedCompletionCommandsRemainGetOnlyAfterDeadline(Phase phase) {
        var job = request("late-accepted-completion", "target", Goal.STOP, Mode.COLD);
        service.register(job); advanceTo(job.operationId(), phase);
        gateway.onSubmit = command -> gateway.result(command, Status.BLOCKED);
        Command accepted = service.advance(job.operationId()).command(); int posts = gateway.posts();
        clock.set(job.deadlineAt().plusSeconds(1));
        gateway.onObserve = command -> gateway.result(command, Status.SUCCEEDED);
        var result = restart().retry(job.operationId());
        assertThat(result.phase()).isEqualTo(Phase.RELEASE);
        assertThat(gateway.posts()).isEqualTo(posts);
        assertThat(gateway.posts.get(accepted.commandId())).hasValue(1);
        assertThat(gateway.polls).hasValue(1);
        assertThat(result.request()).isEqualTo(job);
    }

    @ParameterizedTest @EnumSource(value = Phase.class, names = {"FENCE", "DELETE", "RECOVER"})
    void unknownIrreversibleMutationsStillRejectCancellationAndOnlyPollTheirOriginalCommand(Phase phase) {
        String id = "irreversible-" + phase;
        service.register(request(id, id, phase == Phase.DELETE ? Goal.RETIRE : phase == Phase.RECOVER ? Goal.RECOVER : Goal.STOP,
            phase == Phase.FENCE ? Mode.GRACEFUL : Mode.COLD));
        advanceTo(id, phase);
        gateway.onSubmit = command -> { throw new HttpLifecycleGateway.Unavailable(); };
        var unknown = service.advance(id);
        assertThat(unknown.irreversible()).isTrue();
        assertThatThrownBy(() -> restart().cancel(id))
            .isInstanceOfSatisfying(OperationsException.class, error -> assertThat(error.code()).isEqualTo("LIFECYCLE_CANNOT_CANCEL"));
        gateway.onObserve = command -> gateway.result(command, Status.UNKNOWN);
        assertThat(restart().advance(id).command()).isEqualTo(unknown.command());
        assertThat(gateway.posts.get(unknown.command().commandId())).hasValue(1);
        assertThat(gateway.polls).hasValue(1);
        assertThat(jdbc.queryForList("SELECT phase FROM deployment_lifecycle_command WHERE operation_id=?", String.class, id))
            .doesNotContain("CANCEL");
    }

    @Test void targetOwnershipAndBusyJobsAreIsolatedWithoutAServiceWideExecutionLock() {
        service.register(request("first", "target", Goal.STOP, Mode.COLD));
        assertThatThrownBy(() -> service.register(request("second", "target", Goal.RETIRE, Mode.COLD))).isInstanceOf(OperationsException.class);
        JobRequest original = request("foreign", "target", Goal.STOP, Mode.COLD);
        var foreign = new JobRequest("1", original.operationId(), original.grantId(), "foreign-customer", "tenant", "deployment", "target",
            "release", original.scopeHash(), original.goal(), original.mode(), original.policyRevision(), original.dueAt(), original.deadlineAt(), original.requestedAt());
        assertThatThrownBy(() -> service.register(foreign)).isInstanceOf(OperationsException.class);
        assertThat(service.register(request("other", "other-target", Goal.RETIRE, Mode.COLD))).isNotNull();
    }

    @Test void schedulingIsDisabledByDefaultAndNoticesAndDueEventsAreDurableAndUnique() {
        service.register(request("schedule", "target", Goal.STOP, Mode.COLD));
        service.scheduledTick(); assertThat(gateway.posts()).isZero();
        store.notices(); store.notices(); restart(); store.notices();
        assertThat(store.events("schedule", 0, 100).events()).filteredOn(event -> event.type().equals("JOB_DUE")).hasSize(1);
        assertThat(store.events("schedule", 0, 100).events()).filteredOn(event -> event.type().equals("NOTICE_DUE")).hasSize(1);
        assertThat(store.events("schedule", 0, 1).nextCursor()).isNotNull();
        assertThatThrownBy(() -> store.list(10001, 10)).isInstanceOf(OperationsException.class);
    }

    @Test void outboxCrashReusesTheEventIdentityWithBoundedAttemptsAndNoCoreCommands() {
        var noticeProperties = new LifecycleProperties(false, "", "", false, Duration.ofSeconds(2), Duration.ofSeconds(30),
            Duration.ofHours(24), "https://trusted.example/notices", "trusted-only-notice-key-0000000000000");
        var noticeStore = new LifecycleStore(jdbc, transactions, mapper, noticeProperties, clock);
        var noticeClient = mock(HttpLifecycleGateway.class);
        var notices = new LifecycleService(noticeStore, gateway, noticeClient, noticeProperties, bounds, validator, clock);
        notices.register(request("notice", "target", Goal.STOP, Mode.COLD));
        var first = noticeStore.claimDelivery().orElseThrow();
        clock.set(NOW.plusSeconds(31));
        assertThat(notices.dispatchNotices()).isEqualTo(1);
        verify(noticeClient).deliver(first.event(), first.event().eventId());
        assertThat(jdbc.queryForObject("SELECT attempts FROM deployment_lifecycle_event WHERE event_id=?", Integer.class, first.event().eventId())).isEqualTo(2);
        assertThat(notices.dispatchNotices()).isZero(); assertThat(gateway.posts()).isZero();
    }

    @Test void enabledSchedulerOnlyAdvancesExplicitDueJobsAndContinuesThemAfterRestart() {
        var enabled = new LifecycleProperties(true, "", "", false, Duration.ofSeconds(2), Duration.ofSeconds(30),
            Duration.ofHours(24), "", "");
        var scheduledStore = new LifecycleStore(jdbc, transactions, mapper, enabled, clock);
        var scheduler = new LifecycleService(scheduledStore, gateway, webhook, enabled, bounds, validator, clock);
        var original = request("due", "target", Goal.STOP, Mode.COLD);
        var future = new JobRequest("1", original.operationId(), original.grantId(), original.customerId(), original.tenantId(),
            original.deploymentId(), original.targetProfileId(), original.releaseId(), original.scopeHash(), original.goal(),
            original.mode(), original.policyRevision(), NOW.plusSeconds(60), original.deadlineAt(), original.requestedAt());
        scheduler.register(future); scheduler.scheduledTick(); assertThat(gateway.posts()).isZero();
        clock.set(NOW.plusSeconds(61)); scheduler.scheduledTick();
        assertThat(scheduledStore.status("due").phase()).isEqualTo(Phase.STOP);
        var restarted = new LifecycleService(scheduledStore, gateway, webhook, enabled, bounds, validator, clock);
        for (int index = 0; index < 3; index++) restarted.scheduledTick();
        assertThat(scheduledStore.status("due").state()).isEqualTo(LifecycleStore.JobState.SUCCEEDED);
        assertThat(scheduledStore.status("due").request()).isEqualTo(future);
        assertThat(scheduledStore.events("due", 0, 100).events()).filteredOn(event -> event.type().equals("JOB_DUE")).hasSize(1);
    }

    @Test void optionalWebhookDeliveryStopsAfterEightAttemptsWithoutMutatingTheJob() {
        var noticeProperties = new LifecycleProperties(false, "", "", false, Duration.ofSeconds(2), Duration.ofSeconds(30),
            Duration.ofHours(24), "https://trusted.example/notices", "trusted-only-notice-key-0000000000000");
        var noticeStore = new LifecycleStore(jdbc, transactions, mapper, noticeProperties, clock);
        var noticeClient = mock(HttpLifecycleGateway.class);
        doThrow(new HttpLifecycleGateway.Unavailable()).when(noticeClient).deliver(any(), anyString());
        var notices = new LifecycleService(noticeStore, gateway, noticeClient, noticeProperties, bounds, validator, clock);
        notices.register(request("notice-exhausted", "target", Goal.STOP, Mode.COLD));
        for (int attempt = 0; attempt < 10; attempt++) {
            notices.dispatchNotices(); clock.set(clock.instant().plusSeconds(1000));
        }
        verify(noticeClient, times(8)).deliver(any(), anyString());
        assertThat(jdbc.queryForObject("SELECT delivery FROM deployment_lifecycle_event", String.class)).isEqualTo("EXHAUSTED");
        assertThat(store.status("notice-exhausted").state()).isEqualTo(LifecycleStore.JobState.SCHEDULED);
        assertThat(gateway.posts()).isZero();
    }
}
