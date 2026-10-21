package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentProvider;
import com.ai.fabric.platform.backend.deployment.service.RuntimeArtifactRepairInboxOperations;
import com.ai.fabric.platform.backend.deployment.service.RuntimeColdStoreReadback;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Bounded provider effects, reached only after a durable intent. Observations never repeat a command. */
@Component
public class RuntimeArtifactRepairOperations {
    private final CoolifyDeploymentProvider provider;
    private final ProviderUsageRuntimeClient budgets;
    private final RuntimeGenerationTransitionClient runtime;
    private final DeploymentRuntimeAuthorityService authority;
    private final RuntimeArtifactRepairInboxOperations inbox;
    private final RuntimeArtifactRepairJournal journal;
    private final AdmissionJson json;
    private final Clock clock;
    private RuntimeColdStoreReadback coldStores;
    @Autowired
    void setColdStores(RuntimeColdStoreReadback coldStores) { this.coldStores = coldStores; }
    @Autowired
    public RuntimeArtifactRepairOperations(CoolifyDeploymentProvider provider, ProviderUsageRuntimeClient budgets,
        RuntimeGenerationTransitionClient runtime, DeploymentRuntimeAuthorityService authority,
        RuntimeArtifactRepairInboxOperations inbox,
        RuntimeArtifactRepairJournal journal, AdmissionJson json) {
        this(provider, budgets, runtime, authority, inbox, journal, json, Clock.systemUTC());
    }
    RuntimeArtifactRepairOperations(CoolifyDeploymentProvider provider, ProviderUsageRuntimeClient budgets,
        RuntimeGenerationTransitionClient runtime, DeploymentRuntimeAuthorityService authority,
        RuntimeArtifactRepairInboxOperations inbox,
        RuntimeArtifactRepairJournal journal, AdmissionJson json, Clock clock) {
        this.provider = provider; this.budgets = budgets; this.runtime = runtime; this.authority = authority;
        this.inbox = inbox; this.journal = journal; this.json = json; this.clock = clock;
    }
    NativeEvidence observe(String previewId, LocalEvidence local) {
        outsideTransaction();
        var inventory = provider.runtimeArtifactRepairInventory(local.scope());
        if (!healthy(inventory.runtimeStatus()) || !healthy(inventory.connectorStatus())
            || !local.original().repository().equals(inventory.runtimeRepository())
            || !local.original().gitCommit().equals(inventory.fingerprints().get("runtimeSourceCommit"))
            || !tag(local.original()).equals(inventory.runtimeTag()) || inventory.runtimeJobs().isEmpty()
            || inventory.connectorJobs().isEmpty() || !terminal(inventory.runtimeJobs()) || !terminal(inventory.connectorJobs())) fail("INITIAL_NATIVE_STATE_UNPROVEN");
        var headers = authority.issueRuntimeArtifactRepairHeaders(previewId, false);
        var wallet = budgets.read(local.scope().runtimeBaseUrl(), headers);
        requireWalletScope(local.scope(), wallet);
        if (!Objects.equals(local.fingerprints().get("walletApprovalId"), wallet.approvalId())
            || !Objects.equals(local.fingerprints().get("walletStoreUuid"), wallet.storeUuid() == null ? null : wallet.storeUuid().toString())
            || !Objects.equals(local.fingerprints().get("walletRuntimeFingerprint"), wallet.runtimeFingerprint())) fail("ALLOCATED_WALLET_CHANGED");
        RuntimeArtifactRepairWallet.requireUnchanged(wallet, wallet);
        var target = runtime.status(local.scope().runtimeBaseUrl(), target(local.scope()), headers);
        validateTarget(local.scope(), target);
        if (target.state() != RuntimeGenerationTransitionContracts.State.ACTIVE || target.authorityRevoked() || target.reclaimStarted()) fail("INITIAL_RUNTIME_NOT_ACTIVE");
        var inboxInventory = inbox.observe(local); var inboxJobs = inbox.jobs(local);
        if (!healthy(inboxInventory.get("applicationStatus")) || !terminal(inboxJobs) || inboxJobs.isEmpty()) fail("INITIAL_INBOX_STATE_UNPROVEN");
        var identities = new TreeMap<String, String>();
        identities.put("inventory", json.write(inventory)); identities.put("inbox", json.write(inboxInventory));
        identities.put("inboxJobs", json.write(inboxJobs));
        if (local.request().mode() == Mode.IDLE_CHECKPOINT_COLD_RESTART) {
            if (coldStores == null) fail("EMPTY_JDBC_READBACK_UNAVAILABLE");
            var owned = provider.runtimeArtifactRepairDatabase(local.scope());
            identities.put("emptyJdbcPreflight", json.write(coldStores.requireEmpty(owned.intent(),
                owned.connection().config().serverUuid(), owned.databaseUuid())));
        }
        return new NativeEvidence(identities, wallet, target, clock.instant());
    }
    Map<String, String> perform(Plan plan, Effect effect, String actor) {
        outsideTransaction(); journal.requireEffectAuthority(plan, effect, actor);
        var scope = plan.local().scope();
        var current = provider.runtimeArtifactRepairInventory(scope);
        requireStable(plan, current); requireColdWritersStopped(plan, effect, current);
        journal.requireEffectAuthority(plan, effect, actor);
        switch (effect) {
            case QUIESCE_CONNECTOR -> {
                provider.stopRuntimeArtifactRepairApplication(scope, true);
                journal.dispatched(plan, effect, Map.of("applicationUuid", scope.connectorApplicationUuid()), actor);
            }
            case QUIESCE_INBOX -> {
                inbox.stop(plan, actor);
                journal.dispatched(plan, effect, Map.of("applicationUuid", inboxApplication(plan)), actor);
            }
            case BEGIN_DRAIN -> {
                var binding = drainBinding(plan);
                var command = new RuntimeGenerationTransitionContracts.Command(binding, plan.nativeEvidence().runtime().fenceEpoch(),
                    RuntimeGenerationTransitionContracts.CommandType.BEGIN_DRAIN, plan.local().executionDeadline(), null);
                var response = runtime.command(scope.runtimeBaseUrl(), command, authority.issueRuntimeArtifactRepairHeaders(plan.id(), true));
                journal.dispatched(plan, effect, Map.of("response", json.write(response)), actor);
            }
            case STOP_RUNTIME -> {
                requireDrained(plan);
                provider.stopRuntimeArtifactRepairApplication(scope, false);
                journal.dispatched(plan, effect, Map.of("applicationUuid", scope.runtimeApplicationUuid()), actor);
            }
            case PATCH_IMAGE -> {
                var before = provider.runtimeArtifactRepairInventory(scope);
                if (!stopped(before.runtimeStatus())) fail("RUNTIME_STOP_NOT_CONFIRMED");
                provider.patchRuntimeArtifactRepairImage(scope, plan.local().replacement());
                journal.dispatched(plan, effect, Map.of("applicationUuid", scope.runtimeApplicationUuid(),
                    "repository", plan.local().replacement().repository(), "tag", tag(plan.local().replacement())), actor);
            }
            case DEPLOY_RUNTIME -> {
                var before = provider.runtimeArtifactRepairInventory(scope);
                if (!stopped(before.runtimeStatus()) || !replacement(plan, before)
                    || !plan.local().replacement().gitCommit().equals(before.fingerprints().get("runtimeSourceCommit"))) fail("REVIEWED_IMAGE_NOT_PATCHED");
                String job = provider.deployRuntimeArtifactRepairApplication(scope, false);
                journal.dispatched(plan, effect, Map.of("applicationUuid", scope.runtimeApplicationUuid(), "jobUuid", job), actor);
            }
            case PATCH_PROVENANCE -> {
                var before = provider.runtimeArtifactRepairInventory(scope);
                if (!stopped(before.runtimeStatus()) || !replacement(plan, before)) fail("REVIEWED_IMAGE_NOT_PATCHED");
                provider.patchRuntimeArtifactRepairProvenance(scope, plan.local().original().gitCommit(), plan.local().replacement().gitCommit());
                journal.dispatched(plan, effect, Map.of("applicationUuid", scope.runtimeApplicationUuid(),
                    "sourceCommit", plan.local().replacement().gitCommit()), actor);
            }
            case CANCEL_DRAIN -> {
                var inventory = provider.runtimeArtifactRepairInventory(scope);
                requireCompletedDeploy(plan, inventory);
                var headers = authority.issueRuntimeArtifactRepairHeaders(plan.id(), false);
                var wallet = budgets.read(scope.runtimeBaseUrl(), headers);
                RuntimeArtifactRepairWallet.requireUnchanged(plan.nativeEvidence().wallet(), wallet);
                var drain = runtime.status(scope.runtimeBaseUrl(), target(scope), headers);
                requireDraining(plan, drain);
                requireDrained(plan);
                var command = new RuntimeGenerationTransitionContracts.Command(new RuntimeGenerationTransitionContracts.Binding(
                    target(scope), drain.fenceEpoch() + 1, operation(plan), journal.fingerprint(plan)), drain.fenceEpoch(),
                    RuntimeGenerationTransitionContracts.CommandType.CANCEL_DRAIN, null, null);
                var response = runtime.command(scope.runtimeBaseUrl(), command, authority.issueRuntimeArtifactRepairHeaders(plan.id(), true));
                journal.dispatched(plan, effect, Map.of("response", json.write(response)), actor);
            }
            case RESUME_INBOX -> {
                String job = inbox.start(plan, actor);
                journal.dispatched(plan, effect, Map.of("applicationUuid", inboxApplication(plan), "jobUuid", job), actor);
            }
            case RESUME_CONNECTOR -> {
                String job = provider.deployRuntimeArtifactRepairApplication(scope, true);
                journal.dispatched(plan, effect, Map.of("applicationUuid", scope.connectorApplicationUuid(), "jobUuid", job), actor);
            }
        }
        return waitFor(plan, effect);
    }
    Map<String, String> reconcile(Plan plan, Effect effect) {
        outsideTransaction();
        return check(plan, effect, provider.runtimeArtifactRepairInventory(plan.local().scope()));
    }
    private Map<String, String> waitFor(Plan plan, Effect effect) {
        while (clock.instant().isBefore(journal.executionDeadline(plan))) {
            try { return reconcile(plan, effect); }
            catch (ObservationPending pending) {
                try { Thread.sleep(2000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("RUNTIME_ARTIFACT_REPAIR_INTERRUPTED", interrupted); }
            }
        }
        fail("EXECUTION_DEADLINE_ELAPSED"); return Map.of();
    }
    private Map<String, String> check(Plan plan, Effect effect, NativeInventory inventory) {
        requireStable(plan, inventory); requireColdWritersStopped(plan, effect, inventory); var scope = plan.local().scope();
        var result = new TreeMap<String, String>();
        result.put("inventory", json.write(inventory));
        switch (effect) {
            case QUIESCE_CONNECTOR -> {
                if (!stopped(inventory.connectorStatus())) throw new ObservationPending();
                result.put("CONNECTOR", json.fingerprint(inventory));
            }
            case QUIESCE_INBOX -> {
                var observed = inbox.observe(plan.local());
                if (!stopped(observed.get("applicationStatus"))) throw new ObservationPending();
                result.put("HUMAN_INBOX", json.fingerprint(observed)); result.put("inbox", json.write(observed));
            }
            case BEGIN_DRAIN -> {
                var queue = requireDrained(plan); result.put("drainSnapshot", json.write(snapshot(plan)));
                if (queue != null) result.put("idleQueue", json.write(queue));
            }
            case STOP_RUNTIME -> { if (!stopped(inventory.runtimeStatus())) throw new ObservationPending(); }
            case PATCH_IMAGE -> {
                if (!stopped(inventory.runtimeStatus()) || !replacement(plan, inventory)) fail("IMAGE_PATCH_UNPROVEN");
            }
            case PATCH_PROVENANCE -> {
                if (!stopped(inventory.runtimeStatus()) || !plan.local().replacement().gitCommit().equals(inventory.fingerprints().get("runtimeSourceCommit")))
                    fail("SOURCE_PROVENANCE_PATCH_UNPROVEN");
            }
            case DEPLOY_RUNTIME -> {
                var job = requireCompletedDeploy(plan, inventory);
                var headers = authority.issueRuntimeArtifactRepairHeaders(plan.id(), false);
                requireDraining(plan, runtime.status(scope.runtimeBaseUrl(), target(scope), headers));
                var wallet = budgets.read(scope.runtimeBaseUrl(), headers);
                RuntimeArtifactRepairWallet.requireUnchanged(plan.nativeEvidence().wallet(), wallet);
                requireDrained(plan);
                result.put("applicationUuid", scope.runtimeApplicationUuid()); result.put("jobUuid", job.uuid());
                result.put("createdAt", job.createdAt().toString()); result.put("finishedAt", job.finishedAt().toString());
                result.put("repository", inventory.runtimeRepository()); result.put("tag", inventory.runtimeTag());
                result.put("wallet", json.write(wallet));
            }
            case CANCEL_DRAIN -> {
                var readback = runtime.status(scope.runtimeBaseUrl(), target(scope), authority.issueRuntimeArtifactRepairHeaders(plan.id(), false));
                validateTarget(scope, readback);
                if (readback.state() != RuntimeGenerationTransitionContracts.State.ACTIVE || readback.authorityRevoked() || readback.reclaimStarted()
                    || !operation(plan).equals(readback.operationId()) || !journal.fingerprint(plan).equals(readback.requestFingerprint())
                    || readback.fenceEpoch() != plan.nativeEvidence().runtime().fenceEpoch() + 2) fail("CANCEL_DRAIN_UNPROVEN");
                result.put("readback", json.write(readback));
            }
            case RESUME_INBOX -> {
                var observed = inbox.observe(plan.local());
                var dispatch = journal.dispatch(plan, effect); if (dispatch == null) fail("EXACT_INBOX_JOB_REQUIRED");
                var job = exactJob(inbox.jobs(plan.local()), dispatch.get("jobUuid"), observed.get("applicationUuid"), plan);
                if (!healthy(observed.get("applicationStatus"))) throw new ObservationPending();
                result.put("inboxJob", json.write(job)); result.put("inbox", json.write(observed));
            }
            case RESUME_CONNECTOR -> {
                var dispatch = journal.dispatch(plan, effect); if (dispatch == null) fail("EXACT_CONNECTOR_JOB_REQUIRED");
                var job = exactJob(inventory.connectorJobs(), dispatch.get("jobUuid"), scope.connectorApplicationUuid(), plan);
                if (!healthy(inventory.connectorStatus())) throw new ObservationPending();
                result.put("connectorJob", json.write(job));
            }
        }
        return Map.copyOf(result);
    }
    private void requireColdWritersStopped(Plan plan, Effect effect, NativeInventory inventory) {
        if (plan.local().request().mode() == Mode.IDLE_CHECKPOINT_COLD_RESTART
            && effect.ordinal() >= Effect.BEGIN_DRAIN.ordinal() && effect.ordinal() <= Effect.CANCEL_DRAIN.ordinal()
            && journal.confirmed(plan, Effect.CANCEL_DRAIN) == null
            && (!stopped(inventory.connectorStatus()) || !stopped(inbox.observe(plan.local()).get("applicationStatus"))))
            fail("COLD_REMOTE_WRITERS_NOT_STOPPED");
    }
    VerifiedRepair finalProof(Plan plan, LocalEvidence finalLocal) {
        var scope = plan.local().scope(); var inventory = provider.runtimeArtifactRepairInventory(scope);
        requireStable(plan, inventory); var job = requireCompletedDeploy(plan, inventory);
        check(plan, Effect.CANCEL_DRAIN, inventory); check(plan, Effect.RESUME_INBOX, inventory); check(plan, Effect.RESUME_CONNECTOR, inventory);
        var wallet = budgets.read(scope.runtimeBaseUrl(), authority.issueRuntimeArtifactRepairHeaders(plan.id(), false));
        RuntimeArtifactRepairWallet.requireUnchanged(plan.nativeEvidence().wallet(), wallet);
        return new VerifiedRepair(plan.id(), journal.fingerprint(plan), scope, plan.local().original(), plan.local().replacement(),
            scope.runtimeApplicationUuid(), job.uuid(), job.createdAt(), job.finishedAt(),
            json.read(journal.confirmed(plan, Effect.RESUME_CONNECTOR).evidence().get("connectorJob"), NativeJob.class), wallet,
            finalLocal.fingerprints(), Map.of("inventory", json.write(inventory),
                "dependencyGraph", plan.local().fingerprints().get("graph"),
                "inbox", journal.confirmed(plan, Effect.RESUME_INBOX).evidence().get("inbox"),
                "inboxJob", journal.confirmed(plan, Effect.RESUME_INBOX).evidence().get("inboxJob")), clock.instant());
    }
    private NativeJob requireCompletedDeploy(Plan plan, NativeInventory inventory) {
        if (!healthy(inventory.runtimeStatus())) throw new ObservationPending();
        if (!replacement(plan, inventory)) fail("REVIEWED_IMAGE_UNPROVEN");
        var dispatch = journal.dispatch(plan, Effect.DEPLOY_RUNTIME); if (dispatch == null) fail("EXACT_DEPLOY_JOB_REQUIRED");
        return exactJob(inventory.runtimeJobs(), dispatch.get("jobUuid"), plan.local().scope().runtimeApplicationUuid(), plan);
    }
    private NativeJob exactJob(List<NativeJob> jobs, String uuid, String app, Plan plan) {
        var matches = jobs.stream().filter(j -> Objects.equals(uuid, j.uuid()) && app.equals(j.applicationUuid())).toList();
        if (uuid == null || matches.size() != 1) fail("EXACT_DEPLOY_JOB_REQUIRED");
        var job = matches.getFirst();
        if (!"finished".equals(job.status())) {
            if (List.of("queued", "in_progress", "pending", "running").contains(job.status())) throw new ObservationPending();
            fail("NATIVE_DEPLOY_FAILED");
        }
        if (job.createdAt() == null || job.finishedAt() == null || job.createdAt().isBefore(plan.createdAt())
            || job.finishedAt().isBefore(job.createdAt()) || job.finishedAt().isAfter(clock.instant())) fail("JOB_CLOCK_CHANGED");
        return job;
    }
    private void requireStable(Plan plan, NativeInventory inventory) {
        if (inventory.observedAt() == null || inventory.observedAt().isAfter(clock.instant())
            || inventory.observedAt().isBefore(clock.instant().minusSeconds(60))) fail("NATIVE_EVIDENCE_STALE");
        var before = json.read(plan.nativeEvidence().identities().get("inventory"), NativeInventory.class);
        var beforeStable = new TreeMap<>(before.fingerprints()); var afterStable = new TreeMap<>(inventory.fingerprints());
        beforeStable.remove("runtimeSourceCommit"); afterStable.remove("runtimeSourceCommit");
        if (!beforeStable.equals(afterStable)) fail("NATIVE_CONFIGURATION_OR_STORAGE_CHANGED");
        String commit = inventory.fingerprints().get("runtimeSourceCommit");
        if (!plan.local().original().gitCommit().equals(commit) && !plan.local().replacement().gitCommit().equals(commit))
            fail("UNREVIEWED_SOURCE_PROVENANCE");
        requireJobs(before.runtimeJobs(), inventory.runtimeJobs(), journal.dispatch(plan, Effect.DEPLOY_RUNTIME));
        requireJobs(before.connectorJobs(), inventory.connectorJobs(), journal.dispatch(plan, Effect.RESUME_CONNECTOR));
        var originalInbox = json.read(plan.nativeEvidence().identities().get("inbox"), com.fasterxml.jackson.databind.JsonNode.class).deepCopy();
        var currentInbox = json.read(json.write(inbox.observe(plan.local())), com.fasterxml.jackson.databind.JsonNode.class);
        ((com.fasterxml.jackson.databind.node.ObjectNode) originalInbox).remove("applicationStatus");
        ((com.fasterxml.jackson.databind.node.ObjectNode) currentInbox).remove("applicationStatus");
        if (!originalInbox.equals(currentInbox)) fail("INBOX_CONFIGURATION_OR_STORAGE_CHANGED");
        requireJobs(List.of(json.read(plan.nativeEvidence().identities().get("inboxJobs"), NativeJob[].class)),
            inbox.jobs(plan.local()), journal.dispatch(plan, Effect.RESUME_INBOX));
        if (!replacement(plan, inventory) && !(plan.local().original().repository().equals(inventory.runtimeRepository())
            && tag(plan.local().original()).equals(inventory.runtimeTag()))) fail("UNREVIEWED_IMAGE");
    }
    private void requireJobs(List<NativeJob> before, List<NativeJob> after, Map<String, String> dispatch) {
        if (!after.containsAll(before) || after.stream().map(NativeJob::uuid).distinct().count() != after.size()) fail("NATIVE_JOB_HISTORY_CHANGED");
        var old = new HashSet<>(before.stream().map(NativeJob::uuid).toList());
        for (var job : after) if (!old.contains(job.uuid()) && (dispatch == null || !job.uuid().equals(dispatch.get("jobUuid")))) fail("UNATTRIBUTED_NATIVE_JOB");
    }
    private RuntimeGenerationTransitionContracts.Binding drainBinding(Plan plan) {
        return new RuntimeGenerationTransitionContracts.Binding(target(plan.local().scope()), plan.nativeEvidence().runtime().fenceEpoch() + 1,
            operation(plan), journal.fingerprint(plan));
    }
    private RuntimeGenerationTransitionContracts.Snapshot snapshot(Plan plan) {
        return runtime.snapshot(plan.local().scope().runtimeBaseUrl(), drainBinding(plan), authority.issueRuntimeArtifactRepairHeaders(plan.id(), false));
    }
    private RuntimeArtifactRepairIdleCheckpoint.Queue requireDrained(Plan plan) {
        var value = snapshot(plan); requireDraining(plan, value.target());
        if (value.observedAt() == null || value.observedAt().isAfter(clock.instant()) || value.observedAt().isBefore(clock.instant().minusSeconds(60))
            || value.inFlightRequests() != 0 || value.blockers() == null || value.remoteObligations() == null || value.topologyFingerprint() == null
            || value.evidenceReference() == null) throw new ObservationPending();
        var expected = value.remoteObligations().stream().map(RuntimeGenerationTransitionContracts.RemoteObligation::dependencyKind).collect(java.util.stream.Collectors.toSet());
        var connector = journal.confirmed(plan, Effect.QUIESCE_CONNECTOR); var human = journal.confirmed(plan, Effect.QUIESCE_INBOX);
        if (connector == null || human == null || !expected.equals(java.util.Set.of("CONNECTOR", "HUMAN_INBOX"))
            || connector.evidence().get("CONNECTOR") == null || human.evidence().get("HUMAN_INBOX") == null) fail("REMOTE_DRAIN_PROOF_REQUIRED");
        if (plan.local().request().mode() == Mode.IDLE_CHECKPOINT_COLD_RESTART) {
            var overview = budgets.indexingOverview(plan.local().scope().runtimeBaseUrl(), authority.issueRuntimeArtifactRepairHeaders(plan.id(), false));
            RuntimeArtifactRepairIdleCheckpoint.Queue queue;
            if (!value.inventoryComplete() && journal.emptyJdbcColdRestartAccepted(plan)) {
                if (coldStores == null) fail("EMPTY_JDBC_READBACK_UNAVAILABLE");
                var owned = provider.runtimeArtifactRepairDatabase(plan.local().scope());
                var empty = coldStores.requireEmpty(owned.intent(), owned.connection().config().serverUuid(), owned.databaseUuid());
                if (!plan.local().scope().databaseUuid().equals(empty.databaseUuid()) || empty.observedAt() == null
                    || empty.observedAt().isAfter(clock.instant()) || empty.observedAt().isBefore(clock.instant().minusSeconds(60)))
                    fail("EMPTY_JDBC_READBACK_STALE");
                queue = RuntimeArtifactRepairIdleCheckpoint.requireWithEmptyJdbcStores(value, overview, empty);
                journal.recordColdStoreEvidence(plan, empty);
            } else queue = RuntimeArtifactRepairIdleCheckpoint.require(value, overview);
            var proof = journal.confirmed(plan, Effect.BEGIN_DRAIN);
            if (proof != null && !queue.equals(json.read(proof.evidence().get("idleQueue"), RuntimeArtifactRepairIdleCheckpoint.Queue.class)))
                fail("IDLE_QUEUE_CHANGED");
            return queue;
        }
        if (!value.inventoryComplete() || !value.workerFencingComplete() || !value.localDrainComplete() || !value.blockers().isEmpty())
            throw new ObservationPending();
        return null;
    }
    private void requireDraining(Plan plan, RuntimeGenerationTransitionContracts.Readback value) {
        validateTarget(plan.local().scope(), value);
        if (value.state() != RuntimeGenerationTransitionContracts.State.DRAINING || value.authorityRevoked() || value.reclaimStarted()
            || value.fenceEpoch() != drainBinding(plan).fenceEpoch() || !operation(plan).equals(value.operationId())
            || !journal.fingerprint(plan).equals(value.requestFingerprint())
            || !samePersistedDeadline(plan.local().executionDeadline(), value.drainDeadline())) fail("DRAIN_BINDING_CHANGED");
    }
    private static boolean samePersistedDeadline(Instant expected, Instant observed) {
        // PostgreSQL/JDBC rounds TIMESTAMPTZ to microseconds; this is not a clock-skew allowance.
        return expected.equals(observed)
            || expected.plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS).equals(observed);
    }
    private static void validateTarget(Scope scope, RuntimeGenerationTransitionContracts.Readback value) {
        if (value == null || !target(scope).equals(value.target()) || !scope.tenantId().equals(value.tenantId())
            || value.fenceEpoch() < 0 || value.updatedAt() == null) fail("RUNTIME_IDENTITY_CHANGED");
    }
    private static void requireWalletScope(Scope scope, ProviderUsageRuntimeClient.Readback wallet) {
        if (wallet == null || !scope.customerId().equals(wallet.customerId()) || !scope.deploymentId().equals(wallet.deploymentId())
            || !scope.targetProfileId().equals(wallet.targetProfileId())) fail("WALLET_SCOPE_CHANGED");
    }
    private static boolean replacement(Plan plan, NativeInventory inventory) {
        return plan.local().replacement().repository().equals(inventory.runtimeRepository()) && tag(plan.local().replacement()).equals(inventory.runtimeTag());
    }
    private static RuntimeGenerationTransitionContracts.Target target(Scope scope) {
        return new RuntimeGenerationTransitionContracts.Target(scope.deploymentId(), scope.targetProfileId(), scope.releaseId());
    }
    private static String operation(Plan plan) { return "artifact-repair:" + plan.id(); }
    private String inboxApplication(Plan plan) {
        return json.read(plan.nativeEvidence().identities().get("inbox"), com.fasterxml.jackson.databind.JsonNode.class).path("applicationUuid").asText();
    }
    private static String tag(ArtifactPin pin) { return "sha256-" + pin.digest().substring(7); }
    private static boolean healthy(String value) { return "running:healthy".equalsIgnoreCase(value); }
    private static boolean stopped(String value) { return List.of("stopped", "exited", "exited:unhealthy").contains(value); }
    private static boolean terminal(List<NativeJob> jobs) { return jobs.stream().allMatch(j -> List.of("finished", "failed", "cancelled").contains(j.status()) && j.finishedAt() != null); }
    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("RUNTIME_ARTIFACT_REPAIR_REMOTE_WORK_IN_TRANSACTION");
    }
    private static void fail(String code) { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_" + code); }
    private static final class ObservationPending extends IllegalStateException { }
}
