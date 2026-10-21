package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionFailure;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionJson;
import com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairDependencyEligibility;
import com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairJournal;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Ownership-only stop/start. The graph may be a verified pre-initialization checkpoint and is never changed. */
@Component
public class RuntimeArtifactRepairInboxOperations {
    private final OwnedDependencyOwnership ownership;
    private final ManagedDependencyGraphStore graphs;
    private final DeploymentTargetProfileRepository targets;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient api;
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final RuntimeArtifactRepairJournal journal;
    public RuntimeArtifactRepairInboxOperations(OwnedDependencyOwnership ownership, ManagedDependencyGraphStore graphs,
        DeploymentTargetProfileRepository targets, CoolifyTargetProfileResolver resolver, CoolifyApiClient api,
        JdbcTemplate jdbc, AdmissionJson json, RuntimeArtifactRepairJournal journal) {
        this.ownership = ownership; this.graphs = graphs; this.targets = targets; this.resolver = resolver;
        this.api = api; this.jdbc = jdbc; this.json = json; this.journal = journal;
    }
    public Map<String, String> observe(LocalEvidence local) {
        var context = context(local); var node = graphs.state(graphs.read(context.graphId())).nodes.getFirst();
        var app = ownership.application(context, node.applicationUuid);
        var database = ownership.database(context, node.databaseUuid);
        if (!"running:healthy".equalsIgnoreCase(database.status()) || app.raw() == null || !app.raw().isObject()) fail();
        var config = RuntimeArtifactRepairApplicationProjection.project(app.raw(), false);
        return Map.of("applicationUuid", app.uuid(), "databaseUuid", database.uuid(), "applicationStatus", app.status(),
            "databaseStatus", database.status(), "configuration", json.fingerprint(config),
            "environment", json.fingerprint(api.runtimeRepairEnvironmentInventory(context.connection(), app.uuid())),
            "appStorage", json.fingerprint(api.listApplicationStorages(context.connection(), app.uuid())),
            "dbStorage", json.fingerprint(api.listDatabaseStorages(context.connection(), database.uuid())));
    }
    public void stop(Plan plan, String actor) {
        var local = plan.local();
        var context = context(local); var node = graphs.state(graphs.read(context.graphId())).nodes.getFirst();
        var app = ownership.application(context, node.applicationUuid);
        if (!"running:healthy".equalsIgnoreCase(app.status())) fail();
        ownership.database(context, node.databaseUuid);
        journal.requireEffectAuthority(plan, Effect.QUIESCE_INBOX, actor);
        api.stop(context.connection(), node.applicationUuid, false);
    }
    public String start(Plan plan, String actor) {
        var local = plan.local();
        var context = context(local); var node = graphs.state(graphs.read(context.graphId())).nodes.getFirst();
        var app = ownership.application(context, node.applicationUuid);
        if (!List.of("stopped", "exited", "exited:unhealthy").contains(app.status())) fail();
        ownership.database(context, node.databaseUuid);
        journal.requireEffectAuthority(plan, Effect.RESUME_INBOX, actor);
        var response = api.start(context.connection(), node.applicationUuid, false, false);
        if (response == null || response.deploymentUuid() == null || response.deploymentUuid().isBlank()) fail();
        return response.deploymentUuid();
    }
    public List<NativeJob> jobs(LocalEvidence local) {
        var context = context(local); var node = graphs.state(graphs.read(context.graphId())).nodes.getFirst();
        ownership.application(context, node.applicationUuid);
        return api.applicationDeploymentHistory(context.connection(), node.applicationUuid).stream().map(item -> {
            var exact = api.getDeployment(context.connection(), item.deploymentUuid()).orElseThrow();
            if (!node.applicationUuid.equals(exact.applicationUuid()) || !item.status().equals(exact.status())) fail();
            return new NativeJob(exact.deploymentUuid(), exact.applicationUuid(), exact.status(), Instant.parse(exact.createdAt()),
                exact.finishedAt() == null || exact.finishedAt().isBlank() ? null : Instant.parse(exact.finishedAt()));
        }).toList();
    }
    private OwnedDependencyOwnership.Context context(LocalEvidence local) {
        var scope = local.scope();
        var records = jdbc.queryForList("SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=?",
            scope.deploymentId(), scope.targetProfileId());
        if (records.size() != 1 || !local.fingerprints().get("graph").equals(json.fingerprint(records.getFirst()))) fail();
        var graph = graphs.find(scope.deploymentId(), scope.targetProfileId()).orElseThrow(); var state = graphs.state(graph);
        if (graph.getWorkerToken() != null || !scope.releaseId().equals(graph.getReleaseId()) || !scope.versionId().equals(graph.getVersionId())
            || !scope.customerId().equals(graph.getCustomerId()) || !scope.tenantId().equals(graph.getTenantId())
            || !scope.authorityFingerprint().equals(graph.getBindingFingerprint())) fail();
        if (local.request().mode() == Mode.COLD_MAINTENANCE_REAPPLY)
            RuntimeArtifactRepairDependencyEligibility.requireColdPrepared(graph.getStatus(), graph.getStep(), graph.getFailureCode(), state);
        else RuntimeArtifactRepairDependencyEligibility.require(graph.getStatus(), graph.getStep(), graph.getFailureCode(), state);
        var spec = graphs.spec(graph);
        if (spec.services().size() != 1 || spec.services().getFirst().contract() != ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1
            || !spec.services().getFirst().slot().equals(state.nodes.getFirst().slot)) fail();
        var profile = targets.findById(scope.targetProfileId()).orElseThrow();
        var context = ownership.context(resolver.requireConnection(profile), graph.getId(), state.nodes.getFirst().serviceRef, state.nodes.getFirst().slot);
        ownership.requireSpec(context, spec.services().getFirst());
        return context;
    }
    private static void fail() { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_OWNED_INBOX_UNPROVEN"); }
}
