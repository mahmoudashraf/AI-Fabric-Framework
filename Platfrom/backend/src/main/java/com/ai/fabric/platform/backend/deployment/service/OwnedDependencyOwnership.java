package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Graph metadata locates resources; only creation receipts and live placement authorize their use. */
@Service
public class OwnedDependencyOwnership {
    static final String APPLICATION = "DEPENDENCY_APPLICATION";
    static final String DATABASE = "RUNTIME_POSTGRES_DATABASE";
    static final String APP_VOLUME = "DEPENDENCY_APP_VOLUME";
    static final String DB_STORAGE = "DEPENDENCY_DATABASE_STORAGE";

    record Context(CoolifyConnection connection, String graphId, String slot, String serviceRef,
                   ManagedResourceOwnershipService.Scope scope, CoolifyResourceOwnership.Location location,
                   ManagedDependencySpec.Service spec) {
        String marker() { return "ManagedDependency:" + graphId + ":" + slot; }
        String name(String kind) { return DATABASE.equals(kind) || DB_STORAGE.equals(kind) ? serviceRef + "-db" : serviceRef; }
    }
    record Volume(String uuid, String name, String mountPath) { }
    record Storage(String ownerUuid, List<Volume> volumes) { }
    record CreatedDatabase(CoolifyDatabaseSummary database, Storage storage) { }

    private final ManagedDependencyGraphStore graphs;
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository targets;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient api;
    private final CoolifyResourceOwnership coolify;
    private final ManagedResourceOwnershipService ownership;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OwnedDependencyOwnership(ManagedDependencyGraphStore graphs, DeploymentRepository deployments,
            DeploymentTargetProfileRepository targets, CoolifyTargetProfileResolver resolver, CoolifyApiClient api,
            CoolifyResourceOwnership coolify, ManagedResourceOwnershipService ownership, JdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.graphs = graphs;
        this.deployments = deployments;
        this.targets = targets;
        this.resolver = resolver;
        this.api = api;
        this.coolify = coolify;
        this.ownership = ownership;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    Context context(CoolifyConnection connection, String graphId, String serviceRef, String slot) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        var graph = graphs.read(required(graphId));
        if (!graphId.equals(graph.getId())) throw denied();
        var deployment = deployments.findById(graph.getDeploymentId()).orElseThrow(OwnedDependencyOwnership::denied);
        var scope = new ManagedResourceOwnershipService.Scope(graph.getCustomerId(), graph.getTenantId(),
            graph.getDeploymentId(), graph.getTargetProfileId());
        if (!scope.equals(ownership.scope(deployment, graph.getTargetProfileId()))) throw denied();
        var target = targets.findById(scope.targetProfileId()).orElseThrow(OwnedDependencyOwnership::denied);
        if (target.getProviderType() != DeploymentProviderType.COOLIFY
            || !Objects.equals(connection, resolver.requireConnection(target))) throw denied();
        var specs = graphs.spec(graph).services().stream().filter(value -> required(slot).equals(value.slot())).toList();
        var nodes = graphs.state(graph).nodes.stream().filter(value -> slot.equals(value.slot)).toList();
        if (specs.size() != 1 || nodes.size() != 1 || !required(serviceRef).equals(nodes.getFirst().serviceRef)
            || graphs.state(graph).nodes.stream().filter(value -> serviceRef.equals(value.serviceRef)).count() != 1) throw denied();
        return new Context(connection, graphId, slot, serviceRef, scope,
            coolify.location(connection, connection.config().projectUuid(), connection.config().environmentUuid()), specs.getFirst());
    }

    private ManagedDependencyState.Node node(Context context) {
        Context current = context(context.connection(), context.graphId(), context.serviceRef(), context.slot());
        if (!current.equals(context)) throw denied();
        return graphs.state(graphs.read(context.graphId())).nodes.stream()
            .filter(value -> context.slot().equals(value.slot)).findFirst().orElseThrow(OwnedDependencyOwnership::denied);
    }

    void requireSpec(Context context, ManagedDependencySpec.Service spec) {
        node(context);
        if (!context.spec().equals(spec)) throw denied();
    }

    void requireResources(Context context, PlatformManagedProductProvisioningService.OwnedDependencyResources resources) {
        var node = node(context);
        var recorded = new PlatformManagedProductProvisioningService.OwnedDependencyResources(node.applicationUuid,
            node.databaseUuid, node.applicationCreateIntent, node.databaseCreateIntent, node.volumes,
            node.volumeReclamationPlan, Set.copyOf(node.reclaimedVolumeUuids));
        if (!recorded.equals(resources)) throw denied();
        if (node.applicationUuid == null && hasReceipt(context, APPLICATION)
            || node.databaseUuid == null && (hasReceipt(context, DATABASE) || hasReceipt(context, DB_STORAGE)))
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
    }

    void requireReadyResources(Context context, String applicationUuid, String databaseUuid, Map<String, String> volumes) {
        var node = node(context);
        if (!Objects.equals(node.applicationUuid, applicationUuid) || !Objects.equals(node.databaseUuid, databaseUuid)
            || !node.volumes.equals(volumes == null ? Map.of() : volumes)) throw denied();
    }

    void requireNoApplication(Context context) {
        var node = node(context);
        if (node.applicationUuid != null || node.applicationCreateIntent || hasReceipt(context, APPLICATION)) throw denied();
    }

    void requireReclaimedResources(Context context,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyResource resource) {
        requireReadyResources(context, resource.applicationUuid(), resource.databaseUuid(), resource.volumes());
        var node = node(context);
        requireResources(context, new PlatformManagedProductProvisioningService.OwnedDependencyResources(node.applicationUuid,
            node.databaseUuid, node.applicationCreateIntent, node.databaseCreateIntent, node.volumes,
            node.volumeReclamationPlan, Set.copyOf(node.reclaimedVolumeUuids)));
        if (!node.volumeReclamationPlan.equals(resource.volumeReclamationPlan())
            || !Set.copyOf(node.reclaimedVolumeUuids).equals(resource.reclaimedVolumeUuids())
            || node.applicationCreateIntent && node.applicationUuid == null
            || node.databaseCreateIntent && node.databaseUuid == null) throw denied();
    }

    void requireReclamationInventory(String graphId,
            List<com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyResource> resources) {
        var graph = graphs.read(required(graphId));
        var nodes = graphs.state(graph).nodes;
        if (!graphId.equals(graph.getId()) || resources == null || resources.isEmpty() || resources.size() != nodes.size()
            || resources.stream().anyMatch(Objects::isNull)
            || resources.stream().map(value -> required(value.slot())).distinct().count() != resources.size()
            || !resources.stream().map(value -> value.slot()).collect(java.util.stream.Collectors.toSet())
                .equals(nodes.stream().map(value -> value.slot).collect(java.util.stream.Collectors.toSet()))) throw denied();
    }

    private String parent(Context context) {
        // The physical parent and graph generation are immutable parts of every receipt's location key.
        return json(List.of(coolify.parent(context.location()), context.graphId(), context.slot()));
    }

    CoolifyApplicationSummary createApplication(Context context, Supplier<String> effect) {
        if (node(context).applicationUuid != null || node(context).applicationCreateIntent && hasReceipt(context, APPLICATION)) throw denied();
        return ownership.create(context.scope(), "coolify", APPLICATION, parent(context), context.serviceRef(), () -> {
            String uuid = required(effect.get());
            var application = api.getApplication(context.connection(), uuid).orElseThrow(OwnedDependencyOwnership::denied);
            live(context, APPLICATION, uuid, application.uuid(), application.name(), application.raw());
            if (!api.listApplicationStorages(context.connection(), uuid).isEmpty()) throw volumeDenied();
            return application;
        }, value -> new ManagedResourceOwnershipService.Identity(required(value.uuid()), context.location().endpoint()));
    }

    CoolifyDatabaseSummary createDatabase(Context context, Supplier<String> effect) {
        if (node(context).databaseUuid != null || node(context).databaseCreateIntent && hasReceipt(context, DATABASE)) throw denied();
        // The outer intent covers automatic database storage before the database create can have any effect.
        var created = ownership.create(context.scope(), "coolify", DB_STORAGE, parent(context), context.name(DATABASE),
            () -> ownership.create(context.scope(), "coolify", DATABASE, parent(context), context.name(DATABASE), () -> {
                String uuid = required(effect.get());
                var database = api.getDatabase(context.connection(), uuid).orElseThrow(OwnedDependencyOwnership::denied);
                live(context, DATABASE, uuid, database.uuid(), database.name(), database.raw());
                var storage = storage(uuid, api.listDatabaseStorages(context.connection(), uuid));
                if (storage.volumes().isEmpty()) throw volumeDenied();
                return new CreatedDatabase(database, storage);
            }, value -> new ManagedResourceOwnershipService.Identity(value.database().uuid(), context.location().endpoint())),
            value -> new ManagedResourceOwnershipService.Identity(json(value.storage()), context.location().endpoint()));
        return created.database();
    }

    private boolean hasReceipt(Context context, String kind) {
        return !jdbc.queryForList("""
            SELECT id FROM platform_managed_resource_ownership
             WHERE provider='coolify' AND resource_kind=? AND parent_identity=? AND resource_name=?
            """, String.class, kind, parent(context), context.name(kind)).isEmpty();
    }

    CoolifyApplicationSummary application(Context context, String uuid) {
        return application(context, uuid, false).orElseThrow(OwnedDependencyOwnership::denied);
    }

    Optional<CoolifyApplicationSummary> application(Context context, String uuid, boolean deleting) {
        return application(context, uuid, deleting, null);
    }

    private Optional<CoolifyApplicationSummary> application(Context context, String uuid, boolean deleting, String pendingMount) {
        var node = node(context);
        if (!required(uuid).equals(node.applicationUuid) || uuid.equals(node.databaseUuid) || node.volumes.containsValue(uuid)) throw denied();
        var receipt = resourceReceipt(context, APPLICATION, uuid, deleting);
        trackedVolumes(context, uuid, node.volumes, pendingMount);
        var volumes = applicationVolumes(context, uuid, node.volumes, deleting);
        var observed = api.getApplication(context.connection(), uuid);
        if (observed.isPresent()) {
            if ("DELETED".equals(receipt.state())) throw denied();
            var application = observed.get();
            live(context, APPLICATION, uuid, application.uuid(), application.name(), application.raw());
            requireLiveVolumes(context, uuid, volumes, false);
        } else absent(context, APPLICATION, uuid, receipt, deleting);
        return observed;
    }

    CoolifyDatabaseSummary database(Context context, String uuid) {
        return database(context, uuid, false).orElseThrow(OwnedDependencyOwnership::denied);
    }

    Optional<CoolifyDatabaseSummary> database(Context context, String uuid, boolean deleting) {
        var node = node(context);
        if (!required(uuid).equals(node.databaseUuid) || uuid.equals(node.applicationUuid) || node.volumes.containsValue(uuid)) throw denied();
        var receipt = resourceReceipt(context, DATABASE, uuid, deleting);
        var volumes = databaseVolumes(context, uuid, deleting);
        var observed = api.getDatabase(context.connection(), uuid);
        if (observed.isPresent()) {
            if ("DELETED".equals(receipt.state())) throw denied();
            var database = observed.get();
            live(context, DATABASE, uuid, database.uuid(), database.name(), database.raw());
            requireLiveVolumes(context, uuid, volumes, true);
        } else absent(context, DATABASE, uuid, receipt, deleting);
        return observed;
    }

    private void live(Context context, String kind, String uuid, String observedUuid, String name, JsonNode raw) {
        if (raw == null || !raw.isObject() || !context.marker().equals(raw.path("description").asText(null))) throw denied();
        var projects = api.listProjects(context.connection()).stream()
            .filter(value -> context.location().project().equals(value.uuid())).toList();
        var environment = api.getEnvironment(context.connection(), context.location().project(), context.location().environment())
            .orElseThrow(OwnedDependencyOwnership::denied);
        if (projects.size() != 1) throw denied();
        coolify.requireLive(context.location(), kind, context.name(kind), uuid, observedUuid, name, raw,
            projects.getFirst().raw(), environment.raw());
        unreferenced(context, uuid);
    }

    private void absent(Context context, String kind, String uuid, ManagedResourceOwnershipService.Receipt receipt,
                        boolean deleting) {
        if (!deleting || "OWNED".equals(receipt.state())) throw denied();
        var projects = api.listProjects(context.connection()).stream()
            .filter(value -> context.location().project().equals(value.uuid())).toList();
        var environment = api.getEnvironment(context.connection(), context.location().project(), context.location().environment())
            .orElseThrow(OwnedDependencyOwnership::denied);
        if (projects.size() != 1) throw denied();
        coolify.requireAbsent(context.location(), kind, uuid, projects.getFirst().raw(), environment.raw());
    }

    private ManagedResourceOwnershipService.Receipt resourceReceipt(Context context, String kind, String uuid, boolean deleting) {
        unreferenced(context, required(uuid));
        if (!deleting) return ownership.requireOwnedAt(context.scope(), "coolify", kind, parent(context),
            context.name(kind), uuid, context.location().endpoint());
        var receipt = locationReceipt(context, kind, parent(context), context.name(kind), true);
        if (!uuid.equals(receipt.resourceId())) throw denied();
        return receipt;
    }

    private ManagedResourceOwnershipService.Receipt locationReceipt(Context context, String kind, String parent,
                                                                    String name, boolean deleting) {
        var ids = jdbc.queryForList("""
            SELECT id FROM platform_managed_resource_ownership WHERE provider='coolify'
             AND resource_kind=? AND parent_identity=? AND resource_name=?
            """, String.class, kind, parent, name);
        if (ids.size() != 1) throw denied();
        var receipt = ownership.receipt(ids.getFirst());
        return ownership.requireReceipt(context.scope(), receipt.id(), "coolify", kind,
            required(receipt.resourceId()), context.location().endpoint(), deleting);
    }

    private String volumeParent(Context context, String appUuid, String mount) {
        return json(List.of(parent(context), required(appUuid), required(mount)));
    }

    private String volumeName(Context context, String mount) {
        return context.serviceRef() + "-" + Integer.toHexString(mount.hashCode());
    }

    CoolifyApplicationStorageSummary volume(Context context, String appUuid, String mount, String recordedUuid) {
        application(context, appUuid);
        var existing = api.listApplicationStorages(context.connection(), appUuid);
        String parent = volumeParent(context, appUuid, mount);
        String name = volumeName(context, mount);
        if (recordedUuid != null) {
            if (!recordedUuid.equals(node(context).volumes.get(mount))) throw volumeDenied();
            var receipt = locationReceipt(context, APP_VOLUME, parent, name, false);
            Storage storage = readStorage(receipt, appUuid);
            if (storage.volumes().size() != 1 || !recordedUuid.equals(storage.volumes().getFirst().uuid())) throw volumeDenied();
            return requireStorage(existing, storage.volumes().getFirst());
        }
        if (node(context).volumes.containsKey(mount) || existing.stream().anyMatch(value -> mount.equals(value.mountPath())
            || name.equals(value.name()) || (appUuid + "-" + name).equals(value.name()))) throw volumeDenied();
        Storage created = ownership.create(context.scope(), "coolify", APP_VOLUME, parent, name, () -> {
            application(context, appUuid, false, mount);
            if (api.listApplicationStorages(context.connection(), appUuid).stream().anyMatch(value -> mount.equals(value.mountPath())
                || name.equals(value.name()) || (appUuid + "-" + name).equals(value.name()))) throw volumeDenied();
            JsonNode result = postVolume(context.connection(), appUuid, name, mount);
            Volume volume = new Volume(required(result.path("uuid").asText(null)), required(result.path("name").asText(null)), mount);
            if (!mount.equals(result.path("mount_path").asText(null)) || hasHostPath(result)
                || !(appUuid + "-" + name).equals(volume.name())
                || volume.uuid().equals(appUuid) || volume.uuid().equals(node(context).databaseUuid)) throw volumeDenied();
            requireStorage(api.listApplicationStorages(context.connection(), appUuid), volume);
            unreferenced(context, volume.uuid());
            return new Storage(appUuid, List.of(volume));
        }, value -> new ManagedResourceOwnershipService.Identity(json(value), context.location().endpoint()));
        return requireStorage(api.listApplicationStorages(context.connection(), appUuid), created.volumes().getFirst());
    }

    private void trackedVolumes(Context context, String appUuid, Map<String, String> recorded, String pendingMount) {
        for (var row : jdbc.queryForList("""
            SELECT parent_identity,resource_name,state FROM platform_managed_resource_ownership
             WHERE provider='coolify' AND resource_kind=? AND deployment_id=? AND target_profile_id=?
            """, APP_VOLUME, context.scope().deploymentId(), context.scope().targetProfileId())) {
            JsonNode parent;
            try { parent = mapper.readTree((String) row.get("parent_identity")); }
            catch (IOException error) { throw volumeDenied(); }
            if (!parent.isArray() || parent.size() != 3) throw volumeDenied();
            if (!parent(context).equals(parent.get(0).asText(null)) || !appUuid.equals(parent.get(1).asText(null))) continue;
            String mount = parent.get(2).asText(null);
            if (mount == null || !volumeName(context, mount).equals(row.get("resource_name"))) throw volumeDenied();
            if (mount.equals(pendingMount) && "CREATE_PENDING".equals(row.get("state"))) continue;
            if (!recorded.containsKey(mount)) throw volumeDenied();
        }
    }

    // Unlike the shared reconciler, this path never returns an existing mount and never retries a POST.
    JsonNode postVolume(CoolifyConnection connection, String appUuid, String name, String mount) {
        String base = connection.baseUrl().replaceAll("/+$", "");
        if (!base.endsWith("/api/v1")) base += "/api/v1";
        var body = mapper.createObjectNode().put("type", "persistent").put("name", name).put("mount_path", mount);
        var request = HttpRequest.newBuilder(URI.create(base + "/applications/"
                + URLEncoder.encode(required(appUuid), StandardCharsets.UTF_8).replace("+", "%20") + "/storages"))
            .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + connection.token())
            .header("Accept", "application/json").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) throw volumeDenied();
            return mapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw volumeDenied();
        } catch (IOException error) {
            throw volumeDenied();
        }
    }

    Map<String, String> applicationVolumes(Context context, String appUuid, Map<String, String> recorded, boolean deleting) {
        var node = node(context);
        if (!Objects.equals(node.applicationUuid, appUuid) || !node.volumes.equals(recorded)) throw volumeDenied();
        var volumes = new ArrayList<Volume>();
        for (var entry : recorded.entrySet()) {
            var receipt = locationReceipt(context, APP_VOLUME, volumeParent(context, appUuid, entry.getKey()),
                volumeName(context, entry.getKey()), deleting);
            Storage storage = readStorage(receipt, appUuid);
            if (storage.volumes().size() != 1 || !entry.getValue().equals(storage.volumes().getFirst().uuid())
                || !entry.getKey().equals(storage.volumes().getFirst().mountPath())) throw volumeDenied();
            volumes.add(storage.volumes().getFirst());
        }
        return names(context, volumes);
    }

    Map<String, String> databaseVolumes(Context context, String dbUuid, boolean deleting) {
        if (!required(dbUuid).equals(node(context).databaseUuid)) throw volumeDenied();
        var receipt = locationReceipt(context, DB_STORAGE, parent(context), context.name(DATABASE), deleting);
        Storage storage = readStorage(receipt, dbUuid);
        if (storage.volumes().isEmpty()) throw volumeDenied();
        return names(context, storage.volumes());
    }

    void requireLiveVolumes(Context context, String uuid, Map<String, String> names, boolean database) {
        var storages = database ? api.listDatabaseStorages(context.connection(), uuid) : api.listApplicationStorages(context.connection(), uuid);
        Storage observed = storage(uuid, storages);
        if (!names.equals(names(context, observed.volumes()))) throw volumeDenied();
        for (var value : observed.volumes()) {
            if (database) {
                var expected = readStorage(locationReceipt(context, DB_STORAGE, parent(context), context.name(DATABASE), true), uuid);
                if (!expected.volumes().contains(value)) throw volumeDenied();
            } else {
                var expected = readStorage(locationReceipt(context, APP_VOLUME, volumeParent(context, uuid, value.mountPath()),
                    volumeName(context, value.mountPath()), true), uuid);
                if (!expected.volumes().equals(List.of(value))) throw volumeDenied();
            }
        }
    }

    private Storage storage(String uuid, List<CoolifyApplicationStorageSummary> storages) {
        List<Volume> result = storages.stream().map(value -> {
            if (!"persistent".equals(value.type()) || hasText(value.hostPath()) || hasHostPath(value.raw())
                || !required(value.uuid()).equals(value.raw().path("uuid").asText(null))
                || !required(value.name()).equals(value.raw().path("name").asText(null))
                || !required(value.mountPath()).equals(value.raw().path("mount_path").asText(null))) throw volumeDenied();
            return new Volume(value.uuid(), value.name(), value.mountPath());
        }).sorted(java.util.Comparator.comparing(Volume::uuid)).toList();
        if (result.stream().map(Volume::uuid).distinct().count() != result.size()
            || result.stream().map(Volume::mountPath).distinct().count() != result.size()
            || result.stream().map(Volume::name).distinct().count() != result.size()) throw volumeDenied();
        return new Storage(required(uuid), result);
    }

    private CoolifyApplicationStorageSummary requireStorage(List<CoolifyApplicationStorageSummary> storages, Volume expected) {
        Storage observed = storage("inventory", storages);
        if (observed.volumes().stream().filter(expected::equals).count() != 1) throw volumeDenied();
        return storages.stream().filter(value -> expected.uuid().equals(value.uuid())).findFirst().orElseThrow(OwnedDependencyOwnership::volumeDenied);
    }

    private Storage readStorage(ManagedResourceOwnershipService.Receipt receipt, String ownerUuid) {
        try {
            Storage result = mapper.readValue(receipt.resourceId(), Storage.class);
            if (!ownerUuid.equals(result.ownerUuid()) || result.volumes() == null) throw volumeDenied();
            for (var volume : result.volumes()) { required(volume.uuid()); required(volume.name()); required(volume.mountPath()); }
            return result;
        } catch (IOException | IllegalArgumentException error) { throw volumeDenied(); }
    }

    private Map<String, String> names(Context context, List<Volume> volumes) {
        try {
            for (var volume : volumes) {
                unreferenced(context, volume.uuid());
                unreferenced(context, volume.name());
            }
            return volumes.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Volume::uuid, Volume::name));
        } catch (IllegalStateException error) { throw volumeDenied(); }
    }

    void beginDeletion(Context context, String kind, String uuid) {
        node(context);
        ownership.beginDeletion(resourceReceipt(context, kind, uuid, true));
    }

    ManagedResourceOwnershipService.DeletionPreparation lifecycleDeletionPreparation(Context context, String kind, String uuid) {
        var node = node(context);
        var resource = resourceReceipt(context, kind, uuid, true);
        var volumes = new ArrayList<ManagedResourceOwnershipService.Receipt>();
        if (DATABASE.equals(kind)) {
            if (!uuid.equals(node.databaseUuid)) throw denied();
            databaseVolumes(context, uuid, true);
            volumes.add(locationReceipt(context, DB_STORAGE, parent(context), context.name(DATABASE), true));
        } else {
            if (!APPLICATION.equals(kind) || !uuid.equals(node.applicationUuid)) throw denied();
            applicationVolumes(context, uuid, node.volumes, true);
            for (String mount : node.volumes.keySet()) volumes.add(locationReceipt(context, APP_VOLUME,
                volumeParent(context, uuid, mount), volumeName(context, mount), true));
        }
        return new ManagedResourceOwnershipService.DeletionPreparation(resource, volumes);
    }

    boolean prepareLifecycleDeletion(ManagedResourceOwnershipService.DeletionPreparation preparation, Runnable guard) {
        return ownership.prepareDeletion(preparation, null, guard);
    }

    JsonNode lifecycleDeletionReadback(Context context, String kind, String uuid) {
        var receipt = resourceReceipt(context, kind, uuid, true);
        if (!List.of("DELETE_PENDING", "DELETED").contains(receipt.state())) throw denied();
        JsonNode raw;
        String name;
        if (DATABASE.equals(kind)) {
            var observed = api.getDatabase(context.connection(), uuid);
            raw = observed.map(CoolifyDatabaseSummary::raw).orElse(null);
            name = observed.map(CoolifyDatabaseSummary::name).orElse(null);
        } else {
            if (!APPLICATION.equals(kind)) throw denied();
            var observed = api.getApplication(context.connection(), uuid);
            raw = observed.map(CoolifyApplicationSummary::raw).orElse(null);
            name = observed.map(CoolifyApplicationSummary::name).orElse(null);
        }
        if (raw == null) absent(context, kind, uuid, receipt, true);
        else {
            if ("DELETED".equals(receipt.state())) throw denied();
            live(context, kind, uuid, raw.path("uuid").asText(null), name, raw);
        }
        return raw;
    }

    void beginVolumeDeletion(Context context, String appUuid, Map<String, String> volumes, String dbUuid) {
        applicationVolumes(context, appUuid, volumes, true);
        for (String mount : volumes.keySet()) ownership.beginDeletion(locationReceipt(context, APP_VOLUME,
            volumeParent(context, appUuid, mount), volumeName(context, mount), true));
        if (dbUuid != null) {
            databaseVolumes(context, dbUuid, true);
            ownership.beginDeletion(locationReceipt(context, DB_STORAGE, parent(context), context.name(DATABASE), true));
        }
    }

    void deleted(Context context, String kind, String uuid) {
        var receipt = resourceReceipt(context, kind, uuid, true);
        absent(context, kind, uuid, receipt, true);
        ownership.deleted(receipt.id());
    }

    void volumesDeleted(Context context, String appUuid, Map<String, String> volumes, String dbUuid) {
        for (String mount : volumes.keySet()) ownership.deleted(locationReceipt(context, APP_VOLUME,
            volumeParent(context, appUuid, mount), volumeName(context, mount), true).id());
        if (dbUuid != null) ownership.deleted(locationReceipt(context, DB_STORAGE, parent(context), context.name(DATABASE), true).id());
    }

    void requireDeleted(Context context, String kind, String uuid) {
        if (!"DELETED".equals(resourceReceipt(context, kind, uuid, true).state())) throw denied();
    }

    void requireVolumesDeleted(Context context, String appUuid, Map<String, String> volumes, String dbUuid) {
        applicationVolumes(context, appUuid, volumes, true);
        for (String mount : volumes.keySet()) if (!"DELETED".equals(locationReceipt(context, APP_VOLUME,
            volumeParent(context, appUuid, mount), volumeName(context, mount), true).state())) throw volumeDenied();
        if (dbUuid != null && !"DELETED".equals(locationReceipt(context, DB_STORAGE, parent(context), context.name(DATABASE), true).state())) throw volumeDenied();
    }

    Context databaseContext(CoolifyConnection connection, String uuid) {
        List<Context> matches = new ArrayList<>();
        for (var graph : jdbc.queryForList("SELECT id,state_json FROM deployment_managed_dependency_graphs")) {
            for (var node : document((String) graph.get("state_json")).path("nodes")) {
                if (required(uuid).equals(node.path("databaseUuid").asText(null))) matches.add(context(connection,
                    (String) graph.get("id"), node.path("serviceRef").asText(null), node.path("slot").asText(null)));
            }
        }
        if (matches.size() != 1) throw denied();
        return matches.getFirst();
    }

    private void unreferenced(Context context, String uuid) {
        for (var receipt : jdbc.queryForList("""
            SELECT customer_id,tenant_id,deployment_id,target_profile_id,parent_identity,resource_kind,resource_identity
              FROM platform_managed_resource_ownership WHERE provider='coolify' AND endpoint=?
            """, context.location().endpoint())) {
            String identity = (String) receipt.get("resource_identity");
            String kind = (String) receipt.get("resource_kind");
            boolean references = uuid.equals(identity)
                || List.of(APP_VOLUME, DB_STORAGE).contains(kind) && contains(document(identity), uuid);
            if (!references) continue;
            String receiptParent = (String) receipt.get("parent_identity");
            if (APP_VOLUME.equals(kind)) {
                try { receiptParent = mapper.readTree(receiptParent).path(0).asText(null); }
                catch (IOException error) { throw denied(); }
            }
            if (!context.scope().customerId().equals(receipt.get("customer_id"))
                || !context.scope().tenantId().equals(receipt.get("tenant_id"))
                || !context.scope().deploymentId().equals(receipt.get("deployment_id"))
                || !context.scope().targetProfileId().equals(receipt.get("target_profile_id"))
                || !parent(context).equals(receiptParent)) throw denied();
        }
        for (String json : jdbc.queryForList("SELECT details_json FROM platform_managed_product_services", String.class))
            if (contains(document(json), uuid)) throw denied();
        for (var graph : jdbc.queryForList("SELECT id,state_json FROM deployment_managed_dependency_graphs")) {
            var state = document((String) graph.get("state_json"));
            JsonNode nodes = state.path("nodes");
            if (!nodes.isArray()) throw denied();
            var otherState = ((com.fasterxml.jackson.databind.node.ObjectNode) state).deepCopy();
            otherState.remove("nodes");
            if (contains(otherState, uuid)) throw denied();
            for (JsonNode node : nodes) {
                if (!node.isObject()) throw denied();
                if (context.graphId().equals(graph.get("id")) && context.slot().equals(node.path("slot").asText(null))
                    && context.serviceRef().equals(node.path("serviceRef").asText(null))) continue;
                if (contains(node, uuid) || node.path("volumeReclamationPlan").has(uuid)) throw denied();
            }
        }
        for (var handle : jdbc.queryForList("SELECT provider_resource_uuid,metadata_json FROM deployment_provider_resource_handles WHERE status<>'DELETED'"))
            if (uuid.equals(handle.get("provider_resource_uuid")) || contains(document((String) handle.get("metadata_json")), uuid)) throw denied();
        for (String json : jdbc.queryForList("SELECT provisioning_details_json FROM platform_deployment_releases", String.class))
            if (contains(document(json), uuid)) throw denied();
        for (var resource : jdbc.queryForList("SELECT resource_reference,endpoint FROM platform_deployment_managed_vector_resources"))
            if (uuid.equals(resource.get("resource_reference")) || uuid.equals(resource.get("endpoint"))) throw denied();
    }

    private JsonNode document(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) throw denied();
            return node;
        } catch (IOException error) { throw denied(); }
    }

    private boolean contains(JsonNode node, String value) {
        if (node.isTextual()) return value.equals(node.textValue());
        if (node.isObject() && node.has(value)) return true;
        for (var child : node) if (contains(child, value)) return true;
        return false;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (IOException error) { throw denied(); }
    }
    private static boolean hasHostPath(JsonNode node) {
        return node == null || hasText(node.path("host_path").asText(null)) || hasText(node.path("fs_path").asText(null));
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String required(String value) {
        if (!hasText(value) || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl)) throw denied();
        return value;
    }
    static ResponseStatusException denied() { return new ResponseStatusException(CONFLICT, "DEPENDENCY_PROVIDER_OWNERSHIP_CONFLICT"); }
    private static ResponseStatusException volumeDenied() { return new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT"); }
}
