package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphRepository;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.ai.fabric.platform.backend.deployment.service.ManagedResourceOwnershipFixture.SCOPE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real graph serialization, durable transactions and V197 ledger; provider and repository boundaries are local mocks. */
final class OwnedDependencyOwnershipFixture {
    final ObjectMapper mapper;
    final JdbcTemplate jdbc;
    final ManagedResourceOwnershipService ledger;
    final ManagedDependencyGraphRepository repository = mock(ManagedDependencyGraphRepository.class);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final ManagedDependencyGraphStore graphs;
    final CoolifyResourceOwnership coolify;
    final OwnedDependencyOwnership authority;
    final CoolifyConnection connection;
    final Map<String, ManagedDependencyGraphEntity> graphRows = new LinkedHashMap<>();
    final Map<String, CoolifyApplicationSummary> applications = new LinkedHashMap<>();
    final Map<String, CoolifyDatabaseSummary> databases = new LinkedHashMap<>();
    final Map<String, List<CoolifyApplicationStorageSummary>> appStorages = new LinkedHashMap<>();
    final Map<String, List<CoolifyApplicationStorageSummary>> dbStorages = new LinkedHashMap<>();
    final PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint;
    String graphId = "graph";
    String slot;

    OwnedDependencyOwnershipFixture(CoolifyApiClient api, DeploymentTargetProfileRepository targets,
            CoolifyTargetProfileResolver resolver, ObjectMapper mapper, CoolifyConnection connection) {
        this(api, targets, resolver, mapper, connection, new ManagedResourceOwnershipFixture());
    }

    private OwnedDependencyOwnershipFixture(CoolifyApiClient api, DeploymentTargetProfileRepository targets,
            CoolifyTargetProfileResolver resolver, ObjectMapper mapper, CoolifyConnection connection,
            ManagedResourceOwnershipFixture ledgerFixture) {
        this(api, targets, resolver, mapper, connection, ledgerFixture.jdbc, ledgerFixture.transactions, ledgerFixture.ownership);
    }

    OwnedDependencyOwnershipFixture(CoolifyApiClient api, DeploymentTargetProfileRepository targets,
            CoolifyTargetProfileResolver resolver, ObjectMapper mapper, CoolifyConnection connection,
            JdbcTemplate jdbc, PlatformTransactionManager transactions, ManagedResourceOwnershipService ledger) {
        this.mapper = mapper.findAndRegisterModules();
        this.connection = connection;
        this.jdbc = jdbc;
        this.ledger = ledger;
        graphs = new ManagedDependencyGraphStore(repository, mapper, transactions);
        coolify = new CoolifyResourceOwnership(ledger, mapper, deployments, jdbc, transactions);
        authority = spy(new OwnedDependencyOwnership(graphs, deployments, targets, resolver, api, coolify, ledger, jdbc, mapper));
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_managed_product_services (details_json TEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS deployment_managed_dependency_graphs (id VARCHAR PRIMARY KEY, state_json TEXT)");
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(graphRows.get(call.getArgument(0))));
        when(repository.lockById(anyString())).thenAnswer(call -> Optional.ofNullable(graphRows.get(call.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            var graph = call.getArgument(0, ManagedDependencyGraphEntity.class);
            save(graph);
            return graph;
        });
        var deployment = new com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity();
        deployment.setId(SCOPE.deploymentId()); deployment.setCustomerId(SCOPE.customerId()); deployment.setTenantId(SCOPE.tenantId());
        when(deployments.findById(SCOPE.deploymentId())).thenReturn(Optional.of(deployment));
        var target = new DeploymentTargetProfileEntity();
        target.setId(SCOPE.targetProfileId()); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging");
        when(targets.findById(SCOPE.targetProfileId())).thenReturn(Optional.of(target));
        when(resolver.requireConnection(target)).thenReturn(connection);
        when(api.getApplication(eq(connection), anyString())).thenAnswer(call -> Optional.ofNullable(applications.get(call.getArgument(1))));
        when(api.getDatabase(eq(connection), anyString())).thenAnswer(call -> Optional.ofNullable(databases.get(call.getArgument(1))));
        when(api.listApplicationStorages(eq(connection), anyString())).thenAnswer(call -> List.copyOf(appStorages.getOrDefault(call.getArgument(1), List.of())));
        when(api.listDatabaseStorages(eq(connection), anyString())).thenAnswer(call -> List.copyOf(dbStorages.getOrDefault(call.getArgument(1), List.of())));
        when(api.listProjects(connection)).thenReturn(List.of(new CoolifyProjectSummary("project", "synthetic", null,
            mapper.createObjectNode().put("uuid", "project").put("id", 10))));
        when(api.getEnvironment(connection, "project", "environment")).thenAnswer(call -> {
            var environment = mapper.createObjectNode().put("uuid", "environment").put("id", 20).put("project_id", 10);
            var apps = environment.putArray("applications");
            applications.values().forEach(value -> apps.add(mapper.createObjectNode().put("uuid", value.uuid())
                .put("name", value.name()).put("environment_id", 20)));
            var dbs = environment.putArray("postgresqls");
            databases.values().forEach(value -> dbs.add(mapper.createObjectNode().put("uuid", value.uuid())
                .put("name", value.name()).put("environment_id", 20)));
            return Optional.of(new CoolifyEnvironmentSummary("environment", "staging", "project", null, environment));
        });
        when(api.delete(eq(connection), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean())).thenAnswer(call -> {
            applications.remove(call.getArgument(1));
            return new CoolifyActionResponse("deleted", null, mapper.createObjectNode());
        });
        when(api.deleteDatabase(eq(connection), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean())).thenAnswer(call -> {
            databases.remove(call.getArgument(1));
            return new CoolifyActionResponse("deleted", null, mapper.createObjectNode());
        });
        doAnswer(call -> {
            String app = call.getArgument(1), name = call.getArgument(2), mount = call.getArgument(3);
            var storage = storage("volume", app + "-" + name, mount);
            appStorages.computeIfAbsent(app, ignored -> new ArrayList<>()).add(storage);
            return storage.raw();
        }).when(authority).postVolume(eq(connection), anyString(), anyString(), anyString());
        checkpoint = new PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint() {
            public void guard() { }
            public void creating(String kind) { mutate(node -> { if ("APPLICATION".equals(kind)) node.applicationCreateIntent = true; else node.databaseCreateIntent = true; }); }
            public void created(String kind, String uuid) { mutate(node -> { if ("APPLICATION".equals(kind)) node.applicationUuid = uuid; else node.databaseUuid = uuid; }); }
            public void volume(String mount, String uuid) { mutate(node -> node.volumes.put(mount, uuid)); }
            public void volumeReclamationPlanned(Map<String, String> plan) { mutate(node -> node.volumeReclamationPlan = new LinkedHashMap<>(plan)); }
            public void volumesReclaimed(Set<String> ids) { mutate(node -> node.reclaimedVolumeUuids = new ArrayList<>(ids)); }
        };
    }

    void graph(ManagedDependencySpec.Service spec, String serviceRef,
            PlatformManagedProductProvisioningService.OwnedDependencyResources resources) {
        slot = spec.slot();
        var graph = new ManagedDependencyGraphEntity();
        graph.setId(graphId); graph.setDeploymentId(SCOPE.deploymentId()); graph.setTargetProfileId(SCOPE.targetProfileId());
        graph.setCustomerId(SCOPE.customerId()); graph.setTenantId(SCOPE.tenantId()); graph.setVersionId("version");
        graph.setSpecJson(graphs.json(new ManagedDependencySpec("managed-dependencies/v1", List.of(spec))));
        graph.setStatus("PROVISIONING");
        var state = new ManagedDependencyState();
        var node = new ManagedDependencyState.Node(); node.slot = spec.slot(); node.serviceRef = serviceRef;
        node.applicationUuid = resources.applicationUuid(); node.databaseUuid = resources.databaseUuid();
        node.applicationCreateIntent = resources.applicationCreateIntent(); node.databaseCreateIntent = resources.databaseCreateIntent();
        node.volumes = new LinkedHashMap<>(resources.volumes()); node.volumeReclamationPlan = new LinkedHashMap<>(resources.volumeReclamationPlan());
        node.reclaimedVolumeUuids = new ArrayList<>(resources.reclaimedVolumeUuids()); state.nodes.add(node);
        graph.setStateJson(graphs.json(state)); save(graph);
    }

    void save(ManagedDependencyGraphEntity graph) {
        graphRows.put(graph.getId(), graph);
        jdbc.update("DELETE FROM deployment_managed_dependency_graphs WHERE id=?", graph.getId());
        jdbc.update("INSERT INTO deployment_managed_dependency_graphs(id,state_json) VALUES(?,?)", graph.getId(), graph.getStateJson());
    }

    void mutate(Consumer<ManagedDependencyState.Node> action) {
        graphs.mutate(graphId, null, (graph, state) -> action.accept(state.nodes.getFirst()));
    }

    PlatformManagedProductProvisioningService.OwnedDependencyResources resources() {
        var node = graphs.state(graphs.read(graphId)).nodes.getFirst();
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(node.applicationUuid, node.databaseUuid,
            node.applicationCreateIntent, node.databaseCreateIntent, node.volumes, node.volumeReclamationPlan, Set.copyOf(node.reclaimedVolumeUuids));
    }

    OwnedDependencyOwnership.Context context() {
        var node = graphs.state(graphs.read(graphId)).nodes.getFirst();
        return authority.context(connection, graphId, node.serviceRef, node.slot);
    }

    String parent() { return graphs.json(List.of(coolify.parent(context().location()), graphId, slot)); }

    void receipts() {
        var owner = context(); var resources = resources();
        if (resources.applicationUuid() != null) own(OwnedDependencyOwnership.APPLICATION, parent(), owner.serviceRef(), resources.applicationUuid());
        if (resources.databaseUuid() != null) {
            own(OwnedDependencyOwnership.DATABASE, parent(), owner.serviceRef() + "-db", resources.databaseUuid());
            own(OwnedDependencyOwnership.DB_STORAGE, parent(), owner.serviceRef() + "-db", graphs.json(new OwnedDependencyOwnership.Storage(
                resources.databaseUuid(), dbStorages.getOrDefault(resources.databaseUuid(), List.of()).stream()
                    .map(value -> new OwnedDependencyOwnership.Volume(value.uuid(), value.name(), value.mountPath())).toList())));
        }
        for (var entry : resources.volumes().entrySet()) {
            var volume = appStorages.getOrDefault(resources.applicationUuid(), List.of()).stream()
                .filter(value -> value.uuid().equals(entry.getValue())).findFirst().orElseThrow();
            own(OwnedDependencyOwnership.APP_VOLUME, graphs.json(List.of(parent(), resources.applicationUuid(), entry.getKey())),
                owner.serviceRef() + "-" + Integer.toHexString(entry.getKey().hashCode()),
                graphs.json(new OwnedDependencyOwnership.Storage(resources.applicationUuid(), List.of(
                    new OwnedDependencyOwnership.Volume(volume.uuid(), volume.name(), volume.mountPath())))));
        }
    }

    private void own(String kind, String parent, String name, String uuid) {
        ledger.create(context().scope(), "coolify", kind, parent, name, () -> uuid,
            value -> new ManagedResourceOwnershipService.Identity(value, context().location().endpoint()));
    }

    ObjectNode raw(String uuid, String name, String marker) {
        return mapper.createObjectNode().put("uuid", uuid).put("name", name).put("description", marker)
            .put("environment_id", 20).put("server_uuid", "server").put("destination_uuid", "destination");
    }

    CoolifyApplicationStorageSummary storage(String uuid, String name, String mount) {
        return new CoolifyApplicationStorageSummary(uuid, "persistent", name, mount, null, false,
            mapper.createObjectNode().put("uuid", uuid).put("name", name).put("mount_path", mount));
    }
}
