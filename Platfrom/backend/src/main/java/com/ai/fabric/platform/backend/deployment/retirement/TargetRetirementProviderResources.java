package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/** Isolated provider lifecycle helper: no global admin delegation or deployment-wide cleanup. */
@Service
public class TargetRetirementProviderResources {
    private static final List<String> APPLICATION_KINDS = List.of(
        "APPLICATION", "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION"
    );
    private final TargetRetirementInventory inventory;
    private final DeploymentTargetProfileRepository targets;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient client;
    private final List<String> approvedStagingAddresses;

    public TargetRetirementProviderResources(TargetRetirementInventory inventory, DeploymentTargetProfileRepository targets,
                                             CoolifyTargetProfileResolver resolver, CoolifyApiClient client,
                                             @Value("${platform.target-retirement.approved-staging-addresses:}") List<String> addresses) {
        this.inventory = inventory;
        this.targets = targets;
        this.resolver = resolver;
        this.client = client;
        this.approvedStagingAddresses = List.copyOf(addresses);
    }

    public EffectResult readback(Context context, ResourceGeneration resource) {
        var connection = connection(context, resource);
        var observed = isApplication(resource)
            ? client.getApplication(connection, resource.providerResourceUuid()).map(app -> app.raw())
            : client.getDatabase(connection, resource.providerResourceUuid()).map(db -> db.raw());
        observed.ifPresent(raw -> inventory.requireProviderIdentity(resource, raw));
        if (observed.isEmpty() && isApplication(resource) && !resource.volumes().isEmpty()
            && !inventory.volumesReclaimed(resource)) {
            throw new TargetRetirementFailure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
        }
        return new EffectResult(observed.isEmpty(), "provider-resource:" + resource.handleId() + ":"
            + (observed.isEmpty() ? "absence-candidate" : "present"), Instant.now());
    }

    public EffectResult delete(Context context, ResourceGeneration resource) {
        var before = readback(context, resource);
        if (before.complete()) return before;
        var connection = connection(context, resource);
        // Runtime application storage has its own frozen inventory and provider absence receipt.
        if (isApplication(resource)) {
            retireVolumes(connection, resource);
            client.delete(connection, resource.providerResourceUuid(), true, false, false, false);
        } else {
            client.deleteDatabase(connection, resource.providerResourceUuid(), true, false, false, false);
        }
        return readback(context, resource);
    }

    private CoolifyConnection connection(Context context, ResourceGeneration resource) {
        if (resource.preserved() || resource.providerType() != DeploymentProviderType.COOLIFY
            || !(isApplication(resource) || "RUNTIME_POSTGRES_DATABASE".equals(resource.resourceKind())))
            throw new TargetRetirementFailure("PROVIDER_RESOURCE_UNSUPPORTED");
        inventory.requireResource(resource);
        var profile = targets.findById(context.snapshot().targetProfileId())
            .orElseThrow(() -> new TargetRetirementFailure("TARGET_MISSING"));
        if (!"staging".equals(profile.getEnvironmentName())) throw new TargetRetirementFailure("STAGING_TARGET_REQUIRED");
        var connection = resolver.requireConnection(profile);
        requireFrozenOwnership(context, resource, connection);
        JsonNode server = client.getServer(connection, resource.serverUuid());
        if (server == null || !resource.serverUuid().equals(server.path("uuid").asText())
            || server.path("ip").asText().isBlank() || !approvedStagingAddresses.contains(server.path("ip").asText()))
            throw new TargetRetirementFailure("STAGING_PLACEMENT_NOT_APPROVED");
        return connection;
    }

    private void requireFrozenOwnership(Context context, ResourceGeneration resource, CoolifyConnection connection) {
        if (context.snapshot().resources().stream().noneMatch(resource::equals)
            || resource.releaseId() == null || resource.releaseId().isBlank()
            || resource.generation() == null || resource.generation().isBlank()
            || resource.providerIdentityFingerprint() == null || resource.providerIdentityFingerprint().isBlank()
            || resource.providerName() == null || resource.providerName().isBlank()
            || resource.deploymentMarker() == null || resource.deploymentMarker().isBlank()) {
            throw new TargetRetirementFailure("PROVIDER_RESOURCE_IDENTITY_UNFROZEN");
        }
        var releases = context.snapshot().releases().stream()
            .filter(value -> resource.releaseId().equals(value.releaseId()))
            .toList();
        if (releases.size() != 1 || releases.getFirst().versionId() == null
            || releases.getFirst().versionId().isBlank() || releases.getFirst().generation() == null
            || releases.getFirst().generation().isBlank()) {
            throw new TargetRetirementFailure("PROVIDER_RELEASE_GENERATION_UNFROZEN");
        }
        String expectedMarker = isApplication(resource)
            ? "Managed by AI Fabric deployment " + context.snapshot().deploymentId()
            : "Durable runtime chat database for AI Fabric deployment " + context.snapshot().deploymentId();
        if (!expectedMarker.equals(resource.deploymentMarker())
            || !Objects.equals(resource.serverUuid(), connection.config().serverUuid())
            || !Objects.equals(resource.projectUuid(), connection.config().projectUuid())
            || !Objects.equals(resource.environmentUuid(), connection.config().environmentUuid())
            || !Objects.equals(resource.destinationUuid(), connection.config().destinationUuid())) {
            throw new TargetRetirementFailure("PROVIDER_PLACEMENT_CHANGED");
        }
    }

    private void retireVolumes(CoolifyConnection connection, ResourceGeneration resource) {
        if (resource.volumes().isEmpty()) return;
        boolean dispositionRecorded = inventory.volumesReclaimed(resource);
        List<com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationStorageSummary> observed =
            client.listApplicationStorages(connection, resource.providerResourceUuid());
        inventory.requireVolumeInventory(resource, observed, true);
        Map<String, com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationStorageSummary> byId = observed.stream()
            .filter(value -> "persistent".equals(value.type()))
            .collect(Collectors.toMap(value -> value.uuid(), value -> value));
        if (dispositionRecorded) {
            if (resource.volumes().stream().anyMatch(value -> !value.preserved() && byId.containsKey(value.storageUuid()))) {
                throw new TargetRetirementFailure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
            }
            return;
        }
        for (var volume : resource.volumes()) {
            if (!volume.preserved() && byId.containsKey(volume.storageUuid())) {
                client.deleteApplicationStorage(connection, resource.providerResourceUuid(), volume.storageUuid());
            }
        }
        var after = client.listApplicationStorages(connection, resource.providerResourceUuid());
        inventory.requireVolumeInventory(resource, after, true);
        var remaining = after.stream().filter(value -> "persistent".equals(value.type()))
            .map(value -> value.uuid()).collect(Collectors.toSet());
        if (resource.volumes().stream().anyMatch(value -> !value.preserved() && remaining.contains(value.storageUuid()))) {
            throw new TargetRetirementFailure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
        }
        inventory.recordVolumesReclaimed(resource, Instant.now());
    }

    private boolean isApplication(ResourceGeneration resource) {
        return APPLICATION_KINDS.contains(resource.resourceKind());
    }
}
