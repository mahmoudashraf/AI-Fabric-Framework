package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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
    private final CoolifyHostVolumeReclamationService volumeReclamation;
    private final List<String> approvedStagingAddresses;
    private final CustomerSelfServiceProperties customerSelfServiceProperties;

    public TargetRetirementProviderResources(TargetRetirementInventory inventory, DeploymentTargetProfileRepository targets,
                                             CoolifyTargetProfileResolver resolver, CoolifyApiClient client,
                                             CoolifyHostVolumeReclamationService volumeReclamation,
                                             @Value("${platform.target-retirement.approved-staging-addresses:}") List<String> addresses,
                                             CustomerSelfServiceProperties customerSelfServiceProperties) {
        this.inventory = inventory;
        this.targets = targets;
        this.resolver = resolver;
        this.client = client;
        this.volumeReclamation = volumeReclamation;
        this.approvedStagingAddresses = List.copyOf(addresses);
        this.customerSelfServiceProperties = customerSelfServiceProperties;
    }

    public EffectResult readback(Context context, ResourceGeneration resource) {
        var connection = connection(context, resource);
        var observed = isApplication(resource)
            ? client.getApplication(connection, resource.providerResourceUuid()).map(app -> app.raw())
            : client.getDatabase(connection, resource.providerResourceUuid()).map(db -> db.raw());
        observed.ifPresent(raw -> inventory.requireProviderIdentity(resource, raw));
        if (observed.isEmpty()) {
            verifyContainerAbsence(resource);
            if (!resource.volumes().isEmpty() && !inventory.volumesReclaimed(resource)) {
                if (!inventory.volumeDeletionIntentRecorded(resource)) {
                    throw new TargetRetirementFailure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
                }
                verifyAndRecordVolumeDisposition(resource);
            }
        }
        return new EffectResult(observed.isEmpty(), "provider-resource:" + resource.handleId() + ":"
            + (observed.isEmpty() ? "absence-candidate" : "present"), Instant.now());
    }

    public EffectResult delete(Context context, ResourceGeneration resource) {
        var before = readback(context, resource);
        if (before.complete()) return before;
        var connection = connection(context, resource);
        if (isApplication(resource)) {
            prepareApplicationVolumeDeletion(connection, resource);
            client.delete(connection, resource.providerResourceUuid(), true, true, false, false);
            var observed = client.getApplication(connection, resource.providerResourceUuid());
            if (observed.isEmpty()) {
                verifyContainerAbsence(resource);
                Instant observedAt = verifyAndRecordVolumeDisposition(resource);
                return new EffectResult(true, "provider-resource:" + resource.handleId() + ":absent", observedAt);
            } else {
                inventory.requireProviderIdentity(resource, observed.get().raw());
            }
        } else {
            requireDatabaseVolumes(connection, resource);
            if (!inventory.volumeDeletionIntentRecorded(resource)) {
                inventory.recordVolumeDeletionIntent(resource, Instant.now());
            }
            client.deleteDatabase(connection, resource.providerResourceUuid(), true, true, false, false);
            var observed = client.getDatabase(connection, resource.providerResourceUuid());
            if (observed.isEmpty()) {
                verifyContainerAbsence(resource);
                Instant observedAt = verifyAndRecordVolumeDisposition(resource);
                return new EffectResult(true, "provider-resource:" + resource.handleId() + ":absent", observedAt);
            } else {
                inventory.requireProviderIdentity(resource, observed.get().raw());
            }
        }
        return readback(context, resource);
    }

    private void requireDatabaseVolumes(CoolifyConnection connection, ResourceGeneration resource) {
        if (resource.volumes().isEmpty() || resource.volumes().stream().anyMatch(value -> value.preserved()
            || value.name() == null || value.name().isBlank())) {
            throw new TargetRetirementFailure("DATABASE_VOLUME_OWNERSHIP_UNPROVEN");
        }
        inventory.requireVolumeInventory(resource,
            client.listDatabaseStorages(connection, resource.providerResourceUuid()), false);
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
        var trusted = customerSelfServiceProperties.trustedServers().get(TargetIntent.CUSTOMER_STAGING);
        JsonNode server = client.getServer(connection, resource.serverUuid());
        if (server == null || !resource.serverUuid().equals(server.path("uuid").asText())
            || trusted == null || !resource.serverUuid().equals(trusted.uuid())
            || trusted.address() == null || !approvedStagingAddresses.contains(trusted.address())
            || trusted.reportedAddress() == null || !trusted.reportedAddress().equals(server.path("ip").asText()))
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

    private void prepareApplicationVolumeDeletion(CoolifyConnection connection, ResourceGeneration resource) {
        if (resource.volumes().isEmpty()) return;
        if (inventory.volumesReclaimed(resource)) {
            throw new TargetRetirementFailure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
        }
        var observed = client.listApplicationStorages(connection, resource.providerResourceUuid());
        boolean intentRecorded = inventory.volumeDeletionIntentRecorded(resource);
        if (intentRecorded) {
            inventory.requireVolumeDeletionProgress(resource, observed);
        } else {
            inventory.requireVolumeInventory(resource, observed, false);
            inventory.recordVolumeDeletionIntent(resource, Instant.now());
        }
        var observedIds = new HashSet<String>();
        for (var volume : observed) observedIds.add(volume.uuid());
        for (var volume : resource.volumes()) {
            if (volume.preserved() && observedIds.contains(volume.storageUuid())) {
                client.deleteApplicationStorage(connection, resource.providerResourceUuid(), volume.storageUuid());
            }
        }
        var after = client.listApplicationStorages(connection, resource.providerResourceUuid());
        inventory.requireApplicationDeletionVolumeInventory(resource, after);
    }

    private Instant verifyAndRecordVolumeDisposition(ResourceGeneration resource) {
        if (resource.volumes().isEmpty()) return Instant.now();
        var receipt = volumeReclamation.reclaimAndVerify(TargetIntent.CUSTOMER_STAGING, resource.serverUuid(),
            resource.handleId(), resource.volumes().stream().map(volume ->
                new CoolifyHostVolumeReclamationService.Volume(volume.storageUuid(), volume.name(),
                    volume.hostPath(), volume.preserved())).toList());
        inventory.recordVolumesReclaimed(resource, receipt.observedAt());
        return receipt.observedAt();
    }

    private Instant verifyContainerAbsence(ResourceGeneration resource) {
        return volumeReclamation.verifyContainersAbsent(TargetIntent.CUSTOMER_STAGING,
            resource.serverUuid(), resource.handleId(), isApplication(resource) ? "application" : "database",
            resource.providerResourceUuid()).observedAt();
    }

    private boolean isApplication(ResourceGeneration resource) {
        return APPLICATION_KINDS.contains(resource.resourceKind());
    }
}
