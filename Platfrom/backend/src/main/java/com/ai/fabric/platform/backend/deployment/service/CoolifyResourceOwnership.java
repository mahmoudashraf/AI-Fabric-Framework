package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coolify placement and creation receipts are both required; names and handles cannot confer ownership. */
@Service
public class CoolifyResourceOwnership {
    record Location(String endpoint, String project, String environment, String server, String destination) { }

    private final ManagedResourceOwnershipService ownership;
    private final ObjectMapper mapper;
    private final DeploymentRepository deployments;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate deletionReads;

    public CoolifyResourceOwnership(ManagedResourceOwnershipService ownership, ObjectMapper mapper,
                                    DeploymentRepository deployments, JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.ownership = ownership;
        this.mapper = mapper;
        this.deployments = deployments;
        this.jdbc = jdbc;
        deletionReads = new TransactionTemplate(transactions);
        deletionReads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    Location location(CoolifyConnection connection, String project, String environment) {
        if (connection == null || connection.config() == null) throw reconciliationRequired();
        String endpoint = required(connection.baseUrl());
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        new ManagedResourceOwnershipService.Identity("control-plane", endpoint);
        if (!endpoint.equals(stripTrailingSlash(required(connection.config().baseUrl())))) throw reconciliationRequired();
        return new Location(endpoint, required(project), required(environment),
            required(connection.config().serverUuid()), required(connection.config().destinationUuid()));
    }

    ManagedResourceOwnershipService.Scope scope(DeploymentEntity deployment, String targetId) {
        return ownership.scope(deployment, targetId);
    }

    ManagedResourceOwnershipService.Scope scope(DeploymentProviderResourceHandleEntity handle) {
        var deployment = deployments.findById(required(handle.getDeploymentId())).orElseThrow(CoolifyResourceOwnership::reconciliationRequired);
        return scope(deployment, required(handle.getTargetProfileId()));
    }

    String parent(Location location) {
        return mapper.valueToTree(List.of(location.endpoint(), location.project(), location.environment(),
            location.server(), location.destination())).toString();
    }

    boolean hasCreationIntent(ManagedResourceOwnershipService.Scope scope, Location location, String kind, String name) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_managed_resource_ownership
             WHERE provider='coolify' AND resource_kind=?
               AND ((parent_identity=? AND resource_name=?) OR (deployment_id=? AND target_profile_id=?))
            """, Long.class, kind, parent(location), name, scope.deploymentId(), scope.targetProfileId());
        if (count == null) throw reconciliationRequired();
        return count != 0;
    }

    <T> T create(ManagedResourceOwnershipService.Scope scope, Location location, String kind, String name,
                 Supplier<T> effect, Function<T, String> identity) {
        try {
            return ownership.create(scope, "coolify", kind, parent(location), name, effect,
                result -> new ManagedResourceOwnershipService.Identity(required(identity.apply(result)), location.endpoint()));
        } catch (CoolifyApiException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "COOLIFY_MANAGED_RESOURCE_CREATE_RECONCILIATION_REQUIRED", ex);
        }
    }

    ManagedResourceOwnershipService.Receipt requireOwned(ManagedResourceOwnershipService.Scope scope, Location location,
                                                         String kind, String name, String uuid) {
        return ownership.requireOwnedAt(scope, "coolify", kind, parent(location), required(name), required(uuid), location.endpoint());
    }

    ManagedResourceOwnershipService.Receipt requireHandle(ManagedResourceOwnershipService.Scope scope, Location location,
            String kind, String name, DeploymentProviderResourceHandleEntity handle) {
        return requireHandle(scope, location, kind, name, handle, false);
    }

    ManagedResourceOwnershipService.Receipt requireHandle(ManagedResourceOwnershipService.Scope scope, Location location,
            String kind, String name, DeploymentProviderResourceHandleEntity handle, boolean deleting) {
        if (!Objects.equals(scope.deploymentId(), handle.getDeploymentId())
            || !Objects.equals(scope.targetProfileId(), handle.getTargetProfileId())
            || handle.getProviderType() != DeploymentProviderType.COOLIFY
            || !Objects.equals(kind, handle.getResourceKind()) || !deleting && "DELETED".equals(handle.getStatus())
            || !Objects.equals(location.project(), handle.getProviderProjectUuid())
            || !Objects.equals(location.environment(), handle.getProviderEnvironmentUuid())
            || !Objects.equals(location.server(), handle.getProviderServerUuid())) throw reconciliationRequired();
        JsonNode metadata;
        try {
            metadata = mapper.readTree(handle.getMetadataJson());
            if (metadata == null || !metadata.isObject()) throw reconciliationRequired();
        } catch (Exception ex) {
            throw reconciliationRequired();
        }
        String receiptId = required(metadata.path("ownershipReceiptId").asText(null));
        String uuid = required(handle.getProviderResourceUuid());
        // Release the validation lock before the independently durable deletion journal takes it.
        var receipt = deleting
            ? deletionReads.execute(tx -> ownership.requireReceipt(scope, receiptId, "coolify", kind, uuid, location.endpoint(), true))
            : ownership.requireReceipt(scope, receiptId, "coolify", kind, uuid, location.endpoint(), false);
        if (!Objects.equals(receipt.parent(), parent(location)) || !Objects.equals(receipt.name(), name))
            throw reconciliationRequired();
        return receipt;
    }

    void beginDeletion(ManagedResourceOwnershipService.Receipt receipt, DeploymentProviderResourceHandleEntity handle) {
        ownership.beginDeletion(receipt, handle);
    }

    void deleted(ManagedResourceOwnershipService.Receipt receipt) {
        ownership.deleted(receipt.id());
    }

    void requireNoSharedReferences(String uuid) {
        for (String json : jdbc.queryForList("SELECT details_json FROM platform_managed_product_services", String.class)) {
            JsonNode details = referenceDocument(json);
            if (uuid.equals(details.path("coolifyApplicationUuid").asText(null))
                || uuid.equals(details.path("coolifyDatabaseUuid").asText(null))) throw reconciliationRequired();
        }
        for (String json : jdbc.queryForList("SELECT state_json FROM deployment_managed_dependency_graphs", String.class)) {
            JsonNode nodes = referenceDocument(json).path("nodes");
            if (!nodes.isMissingNode() && !nodes.isArray()) throw reconciliationRequired();
            for (JsonNode node : nodes) {
                if (!node.isObject() || uuid.equals(node.path("applicationUuid").asText(null))
                    || uuid.equals(node.path("databaseUuid").asText(null))) throw reconciliationRequired();
            }
        }
    }

    private JsonNode referenceDocument(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try {
            JsonNode value = mapper.readTree(json);
            if (value == null || !value.isObject()) throw reconciliationRequired();
            return value;
        } catch (Exception ex) {
            throw reconciliationRequired();
        }
    }

    void requireLive(Location location, String kind, String name, String uuid, String observedUuid, String observedName,
                     JsonNode raw, JsonNode project, JsonNode environment) {
        if (raw == null || !Objects.equals(required(uuid), observedUuid)
            || !Objects.equals(uuid, raw.path("uuid").asText(null))
            || !Objects.equals(required(name), observedName) || !Objects.equals(name, raw.path("name").asText(null))
            || !matches(raw, location.project(), false, "/project_uuid", "/projectUuid", "/project/uuid", "/environment/project/uuid")
            || !matches(raw, location.environment(), false, "/environment_uuid", "/environmentUuid", "/environment/uuid")
            || !matches(raw, location.server(), true, "/server_uuid", "/serverUuid", "/destination/server/uuid")
            || !matches(raw, location.destination(), true, "/destination_uuid", "/destinationUuid", "/destination/uuid"))
            throw reconciliationRequired();
        // Parent placement is mandatory through the UUID-addressed inventory; direct GET omits these parent links.
        long environmentId = requireInventory(location, kind, name, uuid, project, environment, true);
        if (raw.hasNonNull("environment_id") && positiveId(raw, "environment_id") != environmentId)
            throw reconciliationRequired();
    }

    void requireAbsent(Location location, String kind, String uuid, JsonNode project, JsonNode environment) {
        requireInventory(location, kind, null, uuid, project, environment, false);
    }

    private long requireInventory(Location location, String kind, String name, String uuid,
                                   JsonNode project, JsonNode environment, boolean present) {
        if (project == null || environment == null || !project.isObject() || !environment.isObject()
            || !location.project().equals(project.path("uuid").asText(null))
            || !location.environment().equals(environment.path("uuid").asText(null))
            || positiveId(project, "id") != positiveId(environment, "project_id")) throw reconciliationRequired();
        long environmentId = positiveId(environment, "id");
        JsonNode inventory = environment.path("RUNTIME_POSTGRES_DATABASE".equals(kind) ? "postgresqls" : "applications");
        if (!inventory.isArray()) throw reconciliationRequired();
        int matches = 0;
        for (JsonNode item : inventory) {
            if (!item.isObject()) throw reconciliationRequired();
            if (!uuid.equals(item.path("uuid").asText(null))) continue;
            if (!present || !name.equals(item.path("name").asText(null)) || positiveId(item, "environment_id") != environmentId)
                throw reconciliationRequired();
            matches++;
        }
        if (matches != (present ? 1 : 0)) throw reconciliationRequired();
        return environmentId;
    }

    private long positiveId(JsonNode raw, String field) {
        JsonNode value = raw.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw reconciliationRequired();
        return value.longValue();
    }

    private boolean matches(JsonNode raw, String expected, boolean required, String... pointers) {
        boolean found = false;
        for (String pointer : pointers) {
            JsonNode value = raw.at(pointer);
            if (value.isMissingNode() || value.isNull()) continue;
            if (!value.isTextual() || !Objects.equals(expected, value.textValue())) return false;
            found = true;
        }
        return found || !required;
    }

    static ResponseStatusException reconciliationRequired() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "COOLIFY_MANAGED_RESOURCE_RECONCILIATION_REQUIRED");
    }

    private static String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl)) throw reconciliationRequired();
        return value;
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
