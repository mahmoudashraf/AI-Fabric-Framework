package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(TargetRetirementPersistenceTest.Config.class)
class TargetRetirementPersistenceTest {
    @Autowired TargetRetirementService service;
    @Autowired TargetRetirementStore store;
    @Autowired TargetRetirementJson json;
    @Autowired TargetRetirementOperationRepository operations;
    @Autowired TargetRetirementGateRepository gates;
    @Autowired TargetRetirementGateService gateService;
    @Autowired TargetRetirementInventory inventory;
    @Autowired TargetRetirementProviderResources provider;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentAccessService access;
    @Autowired TargetRetirementParticipation participation;
    @Autowired TargetRetirementWorker worker;
    @Autowired FakeInterlock interlock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired List<TargetRetirementParticipant> participants;
    private boolean absent;
    private Snapshot snapshot;

    @BeforeEach void setup() {
        operations.deleteAll();
        gates.deleteAll();
        reset(inventory, provider, deployments, access);
        interlock.reset();
        participants.forEach(p -> ((FakeParticipant) p).reset());
        absent = false;
        var deployment = new DeploymentEntity();
        deployment.setId("deployment");
        deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        var owned = new ResourceGeneration("runtime", "staging-release", com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY,
            "APPLICATION", "staging-app", "staging-server", "resource-generation", false);
        var source = new ResourceGeneration("source", null, com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY,
            "EXTERNAL_DOCUMENT_STORAGE_BINDING", "customer-bucket", "staging-server", "source-generation", true);
        snapshot = new Snapshot("customer", "tenant", "deployment", "staging", "target-generation", 0,
            List.of(new ReleaseGeneration("staging-release", "version", "release-generation")), List.of(owned, source),
            List.of(new ConsumerExpectation("consumer", 7)));
        when(inventory.capture(eq("deployment"), eq("staging"), any(), anyLong())).thenAnswer(call ->
            new Snapshot(snapshot.customerId(), snapshot.tenantId(), snapshot.deploymentId(), snapshot.targetProfileId(), snapshot.targetGeneration(),
                call.getArgument(3), snapshot.releases(), snapshot.resources(), snapshot.consumers()));
        when(provider.readback(any(), any())).thenAnswer(call -> new EffectResult(absent, "provider-readback", Instant.now()));
        when(provider.delete(any(), any())).thenAnswer(call -> { absent = true; return new EffectResult(true, "provider-absent", Instant.now()); });
        var principal = new PlatformPrincipal("customer-owner", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_ADMIN"))));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void durableHappyPathRetiresOnlyOwnedTargetAndKeepsSourceAndHistory() {
        Summary op = execute();
        var restarted = new TargetRetirementWorker(store, participation, provider);
        for (int i = 0; i < 8; i++) restarted.runNext(op.id());
        Summary result = status(op);
        assertThat(result.state()).isEqualTo(State.COMPLETE);
        assertThat(result.consumesCapacity()).isFalse();
        assertThat(result.snapshot()).isEqualTo(snapshot);
        assertThat(result.result().evidence().get(ParticipantKind.RUNTIME_DRAIN).humanWork()).isZero();
        assertThat(result.result().evidence().get(ParticipantKind.DEPENDENCIES).complete()).isTrue();
        assertThat(result.result().interlockEvidence().complete()).isTrue();
        assertThat(gates.findAll().get(0).getState()).isEqualTo("RETIRED");
        verify(provider, times(1)).delete(any(), argThat(resource -> resource.handleId().equals("runtime")));
        verify(provider, never()).delete(any(), argThat(ResourceGeneration::preserved));
        verify(deployments, never()).delete(any());
        assertThat(operations.count()).isEqualTo(1);
    }

    @Test void duplicateCommandsReturnPersistedIdentityButDifferentPayloadConflicts() {
        var request = request("same-key", Instant.now().plusSeconds(120));
        Summary first = service.plan("deployment", "staging", request);
        assertThat(service.plan("deployment", "staging", request).id()).isEqualTo(first.id());
        var changed = new PlanRequest(request.operationKey(), request.releaseIds(), request.consumers(), request.drainDeadline(), request.retention(), "different");
        assertThatThrownBy(() -> service.plan("deployment", "staging", changed)).hasMessageContaining("OPERATION_KEY_REUSED");
        var command = new Command(first.requestFingerprint());
        assertThat(service.execute("deployment", "staging", first.id(), command).attempt()).isEqualTo(1);
        assertThat(service.execute("deployment", "staging", first.id(), command).attempt()).isEqualTo(1);
        assertThatThrownBy(() -> service.execute("deployment", "staging", first.id(), new Command("wrong")))
            .hasMessageContaining("REQUEST_FINGERPRINT_CHANGED");
    }

    @Test void missingParticipantAndInterlockFailClosedWithoutProviderCalls() {
        Summary op = execute();
        var missing = new TargetRetirementParticipation(List.of(), List.of());
        new TargetRetirementWorker(store, missing, provider).runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("RETIREMENT_INTERLOCK_MISSING");
        assertThat(status(op).state()).isEqualTo(State.BLOCKED);
        assertThat(status(op).consumesCapacity()).isTrue();
        verifyNoInteractions(provider);
    }

    @Test void unknownDrainInventoryNeverBecomesZeroWork() {
        runtime().unknownWork = true;
        Summary op = execute();
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("DRAIN_WORK_UNKNOWN");
        // BEGIN_DRAIN is the reversible operation that makes authoritative runtime inventory
        // available. Unknown inventory still blocks every destructive successor.
        assertThat(runtime().applied).containsExactly(TargetRetirementWorker.effectKey(
            store.context(store.require(op.id())), Step.DRAIN, "target"));
        assertThat(status(op).result().evidence().get(ParticipantKind.RUNTIME_DRAIN).humanWork()).isNull();
        verifyNoInteractions(provider);
    }

    @Test void humanWorkAndDrainTimeoutRemainVisibleAndNeverReclaim() {
        runtime().humanWork = 2L;
        var planned = service.plan("deployment", "staging", request("human", Instant.now().minusSeconds(1)));
        var op = service.execute("deployment", "staging", planned.id(), new Command(planned.requestFingerprint()));
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("DRAIN_DEADLINE_EXCEEDED");
        assertThat(status(op).result().evidence().get(ParticipantKind.RUNTIME_DRAIN).humanWork()).isEqualTo(2);
        assertThat(status(op).consumesCapacity()).isTrue();
        assertThat(runtime().blocked).isTrue();
        verify(provider, never()).delete(any(), any());
    }

    @Test void generationReplacementBetweenStepsInvalidatesTheOldSnapshot() {
        Summary op = execute();
        worker.runNext(op.id());
        doThrow(new TargetRetirementFailure("SNAPSHOT_GENERATION_CHANGED")).when(inventory).recheck(any(), any());
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("SNAPSHOT_GENERATION_CHANGED");
        assertThat(status(op).consumesCapacity()).isTrue();
        verify(provider, never()).delete(any(), any());
    }

    @Test void mutationEpochInvalidatesPlanAndActiveRetirementRejectsReactivation() {
        Summary op = service.plan("deployment", "staging", request("epoch", Instant.now().plusSeconds(120)));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> gateService.requireAvailableForMutation("deployment", "staging"));
        assertThatThrownBy(() -> service.execute("deployment", "staging", op.id(), new Command(op.requestFingerprint())))
            .hasMessageContaining("TARGET_GENERATION_CHANGED");
        var fresh = execute();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(
            tx -> gateService.requireAvailableForMutation("deployment", "staging"))).hasMessageContaining("TARGET_RETIREMENT_FENCED");
        assertThat(status(fresh).state()).isEqualTo(State.QUEUED);
    }

    @Test void staleWorkersCannotAdvanceAfterTimeoutOrRecovery() {
        Summary op = execute();
        Context old = store.claim(op.id());
        store.prepareEffect(old, "drain-effect", false);
        update(op, entity -> entity.setLeaseExpiresAt(Instant.now().minusSeconds(1)));
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("WORKER_OUTCOME_UNKNOWN");
        assertThatThrownBy(() -> store.finishEffect(old, "drain-effect", new EffectResult(true, "late", Instant.now()), Step.AUTHORITY))
            .hasMessageContaining("STALE_WORKER");
        update(op, entity -> entity.setNextRetryAt(Instant.now().minusSeconds(1)));
        interlock.settled = false;
        interlock.resumeSafe = false;
        assertThatThrownBy(() -> service.retry("deployment", "staging", op.id(), new Command(op.requestFingerprint())))
            .hasMessageContaining("RECOVERY_EVIDENCE_REQUIRED");
        interlock.resumeSafe = true;
        service.retry("deployment", "staging", op.id(), new Command(op.requestFingerprint()));
        Context replacement = store.claim(op.id());
        assertThat(replacement.workerToken()).isNotEqualTo(old.workerToken());
        assertThatThrownBy(() -> store.prepareEffect(old, "late-delete", true)).hasMessageContaining("STALE_WORKER");
        assertThat(store.require(op.id()).getWorkerToken()).isEqualTo(replacement.workerToken());
    }

    @Test void uncertainDeleteRequiresQuiescenceAndReadbackBeforeRetry() {
        Summary op = execute();
        for (int i = 0; i < 3; i++) worker.runNext(op.id());
        doAnswer(call -> { absent = true; throw new IllegalStateException("secret-bearing-provider-error"); }).when(provider).delete(any(), any());
        worker.runNext(op.id());
        assertThat(status(op).state()).isEqualTo(State.RETRYABLE_FAILURE);
        assertThat(status(op).errorCode()).isEqualTo("REMOTE_OUTCOME_UNKNOWN");
        assertThat(status(op).inFlightEffect()).contains("RESOURCES");
        assertThat(status(op).consumesCapacity()).isTrue();
        assertThat(store.require(op.id()).getResultJson()).doesNotContain("secret-bearing");
        assertThatThrownBy(() -> service.retry("deployment", "staging", op.id(), new Command(op.requestFingerprint())))
            .hasMessageContaining("RETIREMENT_BACKOFF_ACTIVE");
        update(op, entity -> entity.setNextRetryAt(Instant.now().minusSeconds(1)));
        service.retry("deployment", "staging", op.id(), new Command(op.requestFingerprint()));
        for (int i = 0; i < 5; i++) worker.runNext(op.id());
        assertThat(status(op).state()).isEqualTo(State.COMPLETE);
        verify(provider, times(1)).delete(any(), any());
    }

    @Test void partialCleanupPollsAndDoesNotReleaseCapacityOrRepeatDelete() {
        Summary op = execute();
        for (int i = 0; i < 3; i++) worker.runNext(op.id());
        doReturn(new EffectResult(false, "accepted", Instant.now())).when(provider).delete(any(), any());
        worker.runNext(op.id());
        assertThat(status(op).state()).isEqualTo(State.WAITING);
        assertThat(status(op).consumesCapacity()).isTrue();
        update(op, entity -> entity.setNextRetryAt(Instant.now().minusSeconds(1)));
        worker.runNext(op.id());
        verify(provider, times(1)).delete(any(), any());
        assertThat(status(op).state()).isEqualTo(State.WAITING);
        update(op, entity -> { entity.setNextRetryAt(Instant.now().minusSeconds(1)); entity.setStepStartedAt(Instant.now().minusSeconds(901)); });
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("RECLAMATION_READBACK_TIMEOUT");
        assertThat(status(op).consumesCapacity()).isTrue();
    }

    @Test void finalReadbackMustProveDependenciesAndActualProviderAbsence() {
        Summary op = execute();
        for (int i = 0; i < 6; i++) worker.runNext(op.id());
        assertThat(status(op).step()).isEqualTo(Step.VERIFY);
        absent = false;
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("PROVIDER_RESOURCE_STILL_PRESENT");
        assertThat(status(op).consumesCapacity()).isTrue();
        assertThat(gates.findAll().get(0).getState()).isEqualTo("RETIRING");
    }

    @Test void finalVerificationRejectsParticipantEvidenceOlderThanItsDestructiveReceipt() {
        Summary op = execute();
        for (int i = 0; i < 6; i++) worker.runNext(op.id());
        assertThat(status(op).step()).isEqualTo(Step.VERIFY);
        update(op, entity -> {
            Progress progress = store.progress(entity);
            var evidence = new java.util.EnumMap<ParticipantKind, Evidence>(ParticipantKind.class);
            evidence.putAll(progress.evidence());
            Evidence current = evidence.get(ParticipantKind.RUNTIME_DRAIN);
            Instant effectTime = progress.effects().get(TargetRetirementWorker.effectKey(
                store.context(entity), Step.DRAIN, "target")).observedAt();
            evidence.put(ParticipantKind.RUNTIME_DRAIN, new Evidence(
                current.requestFingerprint(), current.fenceEpoch(), effectTime.minusSeconds(1), current.reference(),
                current.complete(), current.ingressBlocked(), current.inventoryComplete(), current.retentionApproved(),
                current.conversations(), current.confirmations(), current.humanWork(), current.backgroundWork(),
                true, current.blockers()
            ));
            entity.setResultJson(json.write(new Progress(
                progress.effects(), Map.copyOf(evidence), progress.interlockEvidence(), progress.blockers()
            )));
        });

        worker.runNext(op.id());

        assertThat(status(op).errorCode()).isEqualTo("PARTICIPANT_EVIDENCE_STALE_RUNTIME_DRAIN");
        assertThat(status(op).consumesCapacity()).isTrue();
        assertThat(gates.findAll().getFirst().getState()).isEqualTo("RETIRING");
    }

    @Test void retentionExpiryResumesTheSameSnapshotAndNotTheLatestRelease() throws Exception {
        var expiry = Instant.now().plusSeconds(2);
        var request = request("retained", expiry.plusSeconds(120));
        request = new PlanRequest(request.operationKey(), request.releaseIds(), request.consumers(), request.drainDeadline(),
            new Retention(RetentionDecision.RETAIN_UNTIL, expiry, "entitlement-1", "backup-policy-1"), request.reason());
        Summary op = service.scheduleAfterPromotion("deployment", "staging", request);
        assertThat(op.state()).isEqualTo(State.RETAINED);
        worker.runNext(op.id());
        verifyNoInteractions(provider);
        update(op, entity -> entity.setNextRetryAt(Instant.now().minusSeconds(1)));
        worker.runNext(op.id());
        assertThat(status(op).state()).isEqualTo(State.RETAINED);
        verifyNoInteractions(provider);
        Thread.sleep(Math.max(1, java.time.Duration.between(Instant.now(), expiry).toMillis() + 10));
        worker.runNext(op.id());
        assertThat(status(op).step()).isEqualTo(Step.AUTHORITY);
        assertThat(status(op).snapshot().releases()).containsExactly(new ReleaseGeneration("staging-release", "version", "release-generation"));
    }

    @Test void safeCancellationRestoresIngressButReclamationCannotBeCancelled() {
        Summary op = execute();
        worker.runNext(op.id());
        service.cancel("deployment", "staging", op.id(), new Command(op.requestFingerprint()));
        worker.runNext(op.id());
        assertThat(status(op).state()).isEqualTo(State.CANCELLED);
        assertThat(runtime().blocked).isFalse();
        assertThat(gates.findAll().get(0).getState()).isEqualTo("AVAILABLE");
        var reclaim = execute();
        worker.runNext(reclaim.id());
        worker.runNext(reclaim.id());
        assertThatThrownBy(() -> service.cancel("deployment", "staging", reclaim.id(), new Command(reclaim.requestFingerprint())))
            .hasMessageContaining("RETIREMENT_CANCEL_UNSAFE");
    }

    @Test void wrongTargetAndDeniedOwnerCannotReadOrOperateAndAnonymousCannotPlan() {
        Summary op = execute();
        assertThatThrownBy(() -> service.status("deployment", "production", op.id())).hasMessageContaining("404");
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))
            .when(access).requireDeploymentAccess(any());
        assertThatThrownBy(() -> service.status("deployment", "staging", op.id())).hasMessageContaining("404");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(this::execute).isInstanceOfAny(org.springframework.security.access.AccessDeniedException.class,
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    @Test void missingIndexAdapterBlocksOwnerCleanupAndPersistsTheExactBlocker() {
        Summary op = execute();
        var incomplete = new TargetRetirementParticipation(participants.stream().filter(p -> p.kind() != ParticipantKind.INDEX).toList(), List.of(interlock));
        new TargetRetirementWorker(store, incomplete, provider).runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("PARTICIPANT_MISSING_INDEX");
        assertThat(status(op).consumesCapacity()).isTrue();
        verifyNoInteractions(provider);
    }

    @Test void referencesAreRecheckedInsideTheFenceImmediatelyBeforeEachEffect() {
        Summary op = execute();
        worker.runNext(op.id());
        interlock.beforeEffect = () -> doThrow(new TargetRetirementFailure("CONSUMER_REVISION_CHANGED")).when(inventory).recheck(any(), any());
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("CONSUMER_REVISION_CHANGED");
        assertThat(((FakeParticipant) participation.participant(ParticipantKind.AUTHORITY)).applied).isEmpty();
        verify(provider, never()).delete(any(), any());
    }

    @Test void changedProductionReferenceBeforeAReclamationStepBlocksIt() {
        Summary op = execute();
        worker.runNext(op.id());
        interlock.blocker = "TARGET_STILL_ASSIGNED";
        worker.runNext(op.id());
        assertThat(status(op).errorCode()).isEqualTo("TARGET_STILL_ASSIGNED");
        assertThat(((FakeParticipant) participation.participant(ParticipantKind.AUTHORITY)).applied).isEmpty();
        verify(provider, never()).delete(any(), any());
    }

    @Test void emptyProviderResponseNeedsAnAuthoritativeAbsenceReceipt() {
        Summary op = execute();
        for (int i = 0; i < 3; i++) worker.runNext(op.id());
        interlock.absenceProven = false;
        worker.runNext(op.id());
        assertThat(status(op).state()).isEqualTo(State.WAITING);
        assertThat(status(op).consumesCapacity()).isTrue();
        TargetRetirementInventory rawInventory = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(inventory);
        verify(rawInventory, never()).recordReclaimed(any(), any());
    }

    @Test void concurrentClaimHasOnlyOneWorkerAndRetryBoundIsPersisted() throws Exception {
        Summary op = execute();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var claims = pool.invokeAll(List.<java.util.concurrent.Callable<Context>>of(() -> store.claim(op.id()), () -> store.claim(op.id())));
            assertThat(claims.stream().map(f -> { try { return f.get(); } catch (Exception ex) { throw new RuntimeException(ex); } })
                .filter(java.util.Objects::nonNull).count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        update(op, entity -> { entity.setState(State.BLOCKED); entity.setAttempt(8); entity.setWorkerToken(null); });
        assertThatThrownBy(() -> service.retry("deployment", "staging", op.id(), new Command(op.requestFingerprint())))
            .hasMessageContaining("RETIREMENT_ATTEMPTS_EXHAUSTED");
    }

    private Summary execute() {
        Summary planned = service.plan("deployment", "staging", request(java.util.UUID.randomUUID().toString(), Instant.now().plusSeconds(120)));
        return service.execute("deployment", "staging", planned.id(), new Command(planned.requestFingerprint()));
    }
    private Summary status(Summary op) { return service.status("deployment", "staging", op.id()); }
    private PlanRequest request(String key, Instant deadline) {
        return new PlanRequest(key, List.of("staging-release"), List.of(new ConsumerExpectation("consumer", 7)), deadline,
            new Retention(RetentionDecision.RECLAIM, null, null, "backup-policy-1"), "post-promotion");
    }
    private FakeParticipant runtime() { return (FakeParticipant) participation.participant(ParticipantKind.RUNTIME_DRAIN); }
    private void update(Summary op, Consumer<TargetRetirementOperationEntity> mutation) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var entity = operations.findForUpdate(op.id()).orElseThrow();
            mutation.accept(entity);
            operations.saveAndFlush(entity);
        });
    }

    static class FakeParticipant implements TargetRetirementParticipant {
        final ParticipantKind kind;
        final Set<String> applied = new HashSet<>();
        boolean blocked;
        boolean unknownWork;
        Long humanWork = 0L;
        FakeParticipant(ParticipantKind kind) { this.kind = kind; }
        void reset() { applied.clear(); blocked = false; unknownWork = false; humanWork = 0L; }
        public ParticipantKind kind() { return kind; }
        public Evidence inspect(Context context) {
            return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(), "participant-proof-" + kind,
                kind != ParticipantKind.RUNTIME_DRAIN || blocked && humanWork == 0, blocked, true, true,
                0L, 0L, unknownWork ? null : humanWork, 0L,
                kind != ParticipantKind.RUNTIME_DRAIN || blocked && humanWork == 0, List.of());
        }
        public EffectResult apply(Context context, Step step, String key) {
            applied.add(key);
            if (step == Step.DRAIN) blocked = true;
            if (step == Step.CANCEL_DRAIN) blocked = false;
            return readback(context, step, key);
        }
        public EffectResult readback(Context context, Step step, String key) {
            return new EffectResult(applied.contains(key), "effect-proof-" + key, Instant.now());
        }
    }
    static class FakeInterlock implements TargetRetirementInterlock {
        boolean settled = true;
        boolean resumeSafe = true;
        boolean absenceProven = true;
        String blocker;
        Runnable beforeEffect;
        void reset() { settled = true; resumeSafe = true; absenceProven = true; blocker = null; beforeEffect = null; }
        public Evidence inspect(Context context) {
            return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(), "authority-proof", true,
                true, true, true, 0L, 0L, 0L, 0L, false,
                blocker == null ? List.of() : List.of(blocker));
        }
        public EffectResult underFence(Context context, String key, Supplier<EffectResult> effect) {
            if (beforeEffect != null) beforeEffect.run();
            return effect.get();
        }
        public RecoveryEvidence recover(Context context, String oldWorker, String effectKey) {
            return new RecoveryEvidence(context.operationId(), oldWorker, effectKey, Instant.now(),
                "quiescence-proof", true, settled, resumeSafe);
        }
        public EffectResult verifyResourceAbsent(Context context, ResourceGeneration resource) {
            return new EffectResult(absenceProven, "authoritative-absence-" + resource.handleId(), Instant.now());
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableMethodSecurity
    @EnableJpaRepositories(basePackageClasses = TargetRetirementOperationRepository.class)
    @Import({TargetRetirementStore.class, TargetRetirementGateService.class, TargetRetirementJson.class,
        TargetRetirementService.class, TargetRetirementWorker.class, TargetRetirementParticipation.class})
    static class Config {
        @Bean DataSource dataSource() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:retirement;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("db/test-migration/V120_9__target_retirement_operations_test.sql")).execute(source);
            return source;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan(TargetRetirementOperationEntity.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean DeploymentRepository deployments() { return mock(DeploymentRepository.class); }
        @Bean DeploymentAccessService access() { return mock(DeploymentAccessService.class); }
        @Bean com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary offerBoundary() {
            return mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);
        }
        @Bean TargetRetirementInventory inventory() { return mock(TargetRetirementInventory.class); }
        @Bean TargetRetirementProviderResources provider() { return mock(TargetRetirementProviderResources.class); }
        @Bean PlatformAuditService audit() { return mock(PlatformAuditService.class); }
        @Bean FakeInterlock interlock() { return new FakeInterlock(); }
        @Bean TargetRetirementParticipant drain() { return new FakeParticipant(ParticipantKind.RUNTIME_DRAIN); }
        @Bean TargetRetirementParticipant authority() { return new FakeParticipant(ParticipantKind.AUTHORITY); }
        @Bean TargetRetirementParticipant index() { return new FakeParticipant(ParticipantKind.INDEX); }
        @Bean TargetRetirementParticipant dependencies() { return new FakeParticipant(ParticipantKind.DEPENDENCIES); }
    }
}
