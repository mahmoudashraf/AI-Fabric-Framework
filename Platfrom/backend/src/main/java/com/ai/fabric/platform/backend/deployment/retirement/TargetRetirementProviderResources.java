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
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/** Isolated provider lifecycle helper: no global admin delegation or deployment-wide cleanup. */
@Service
public class TargetRetirementProviderResources {
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
        var observed = "APPLICATION".equals(resource.resourceKind())
            ? client.getApplication(connection, resource.providerResourceUuid()).map(app -> app.raw())
            : client.getDatabase(connection, resource.providerResourceUuid()).map(db -> db.raw());
        observed.ifPresent(raw -> requirePlacement(raw, resource));
        return new EffectResult(observed.isEmpty(), "provider-resource:" + resource.handleId() + ":"
            + (observed.isEmpty() ? "absence-candidate" : "present"), Instant.now());
    }

    public EffectResult delete(Context context, ResourceGeneration resource) {
        var before = readback(context, resource);
        if (before.complete()) return before;
        var connection = connection(context, resource);
        // Volumes, secrets and networks need separate, owned dependency evidence; never cascade here.
        if ("APPLICATION".equals(resource.resourceKind())) {
            client.delete(connection, resource.providerResourceUuid(), true, false, false, false);
        } else {
            client.deleteDatabase(connection, resource.providerResourceUuid(), true, false, false, false);
        }
        return readback(context, resource);
    }

    private CoolifyConnection connection(Context context, ResourceGeneration resource) {
        if (resource.preserved() || resource.providerType() != DeploymentProviderType.COOLIFY
            || !("APPLICATION".equals(resource.resourceKind()) || "RUNTIME_POSTGRES_DATABASE".equals(resource.resourceKind())))
            throw new TargetRetirementFailure("PROVIDER_RESOURCE_UNSUPPORTED");
        inventory.requireResource(resource);
        var profile = targets.findById(context.snapshot().targetProfileId())
            .orElseThrow(() -> new TargetRetirementFailure("TARGET_MISSING"));
        if (!"staging".equals(profile.getEnvironmentName())) throw new TargetRetirementFailure("STAGING_TARGET_REQUIRED");
        var connection = resolver.requireConnection(profile);
        if (resource.serverUuid() == null || !resource.serverUuid().equals(connection.config().serverUuid()))
            throw new TargetRetirementFailure("PROVIDER_PLACEMENT_CHANGED");
        JsonNode server = client.getServer(connection, resource.serverUuid());
        if (server == null || !resource.serverUuid().equals(server.path("uuid").asText())
            || server.path("ip").asText().isBlank() || !approvedStagingAddresses.contains(server.path("ip").asText()))
            throw new TargetRetirementFailure("STAGING_PLACEMENT_NOT_APPROVED");
        return connection;
    }

    private void requirePlacement(JsonNode raw, ResourceGeneration resource) {
        if (raw == null || !resource.providerResourceUuid().equals(raw.path("uuid").asText())
            || !resource.serverUuid().equals(raw.path("server_uuid").asText()))
            throw new TargetRetirementFailure("PROVIDER_RESOURCE_PLACEMENT_UNPROVEN");
    }
}
