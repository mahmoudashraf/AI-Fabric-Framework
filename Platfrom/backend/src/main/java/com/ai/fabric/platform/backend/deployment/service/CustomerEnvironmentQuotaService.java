package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

/** A workspace allowance for dedicated environments, independent of optional capacity policy. */
@Service
public class CustomerEnvironmentQuotaService {
    private static final String OWNED_KIND = "resource_kind NOT IN ('EXTERNAL_DOCUMENT_STORAGE_BINDING', 'EXTERNAL_VECTOR_STORAGE_BINDING', 'EXTERNAL_AI_STATE_STORAGE_BINDING')";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlatformAuditService audit;

    public CustomerEnvironmentQuotaService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformAuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Summary summary(String customerId) {
        return summarize(customerId, limit(customerId, false));
    }

    /** The caller records its queued release or claimed dependency worker in this same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailable(DeploymentEntity deployment, String targetProfileId) {
        if (!StringUtils.hasText(deployment.getCustomerId())) return;
        var summary = summarize(deployment.getCustomerId(), limit(deployment.getCustomerId(), true));
        if (summary.environments().stream().anyMatch(item -> deployment.getId().equals(item.deploymentId())
            && targetProfileId.equals(item.targetProfileId()))) return;
        if (summary.used() >= summary.limit()) {
            throw new ResponseStatusException(CONFLICT, "CUSTOMER_ENVIRONMENT_LIMIT_REACHED: This workspace uses "
                + summary.used() + " of " + summary.limit() + " infrastructure environments. Update an existing environment"
                + " or remove its owned infrastructure before starting another. Drafts and shared Engage apps do not count.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNoRetainedInfrastructure(DeploymentEntity deployment) {
        if (!StringUtils.hasText(deployment.getCustomerId())) return;
        var current = summarize(deployment.getCustomerId(), limit(deployment.getCustomerId(), true));
        if (current.environments().stream().anyMatch(item -> deployment.getId().equals(item.deploymentId()))) {
            throw new ResponseStatusException(CONFLICT, "OWNED_INFRASTRUCTURE_REMAINS: Remove this deployment's owned"
                + " infrastructure before deleting its configuration. Archiving or deleting records does not free an environment slot.");
        }
    }

    @Transactional
    public Summary updateLimit(String customerId, int requested, String reason) {
        if (requested < 0 || requested > 1000 || reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(BAD_REQUEST, "A limit between 0 and 1000 and an audit reason are required.");
        }
        int previous = limit(customerId, true);
        jdbc.update("UPDATE platform_customers SET infrastructure_environment_limit=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", requested, customerId);
        audit.record("CUSTOMER_ENVIRONMENT_LIMIT_CHANGED", "PLATFORM_CUSTOMER", customerId,
            Map.of("previousLimit", previous, "limit", requested, "reason", reason.trim()));
        return summarize(customerId, requested);
    }

    private int limit(String customerId, boolean lock) {
        var rows = jdbc.queryForList("SELECT infrastructure_environment_limit FROM platform_customers WHERE id=?"
            + (lock ? " FOR UPDATE" : ""), Integer.class, customerId);
        if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Customer workspace is unavailable.");
        return rows.getFirst();
    }

    private Summary summarize(String customerId, int limit) {
        var occupied = new LinkedHashMap<EnvironmentKey, Environment>();
        // One statement keeps queued-release and owned-resource observations in one database snapshot.
        jdbc.query("""
            SELECT d.id, d.name, h.target_profile_id, p.environment_name, true, NULL, NULL
            FROM deployment_provider_resource_handles h
            JOIN platform_deployments d ON d.id=h.deployment_id
            LEFT JOIN deployment_target_profiles p ON p.id=h.target_profile_id
            WHERE d.customer_id=? AND h.status<>'DELETED' AND
            """ + OWNED_KIND + """
            UNION ALL
            SELECT d.id, d.name, r.target_profile_id, p.environment_name,
              r.provisioning_status IN ('ACTIVE','STOPPED'), NULL, NULL
            FROM platform_deployment_releases r JOIN platform_deployments d ON d.id=r.deployment_id
            LEFT JOIN deployment_target_profiles p ON p.id=r.target_profile_id
            WHERE d.customer_id=? AND r.target_profile_id IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM platform_deployment_releases newer WHERE newer.deployment_id=r.deployment_id
                AND newer.target_profile_id=r.target_profile_id AND (newer.created_at>r.created_at
                  OR (newer.created_at=r.created_at AND newer.id>r.id)))
              AND (r.status IN ('APPLY_REQUESTED','PRE_APPLY_VERIFYING','PROVISIONING','VERIFYING')
                OR (r.provisioning_status IN ('ACTIVE','STOPPED') AND NOT EXISTS (
                  SELECT 1 FROM deployment_provider_resource_handles h WHERE h.deployment_id=r.deployment_id
                    AND h.target_profile_id=r.target_profile_id AND
            """ + OWNED_KIND + ")))" + """
            UNION ALL
            SELECT d.id, d.name, g.target_profile_id, p.environment_name, false, g.state_json, g.worker_token
            FROM deployment_managed_dependency_graphs g JOIN platform_deployments d ON d.id=g.deployment_id
            LEFT JOIN deployment_target_profiles p ON p.id=g.target_profile_id
            WHERE d.customer_id=? AND g.status<>'RETIRED'
            """, rs -> {
                String dependencyState = rs.getString(6);
                boolean resources = rs.getBoolean(5) || dependencyState != null && ownsDependencyResources(dependencyState);
                if (dependencyState == null || resources || StringUtils.hasText(rs.getString(7))) {
                    add(occupied, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), resources);
                }
            }, customerId, customerId, customerId);
        var environments = List.copyOf(occupied.values());
        return new Summary(customerId, limit, environments.size(), Math.max(0, limit-environments.size()), environments);
    }

    private boolean ownsDependencyResources(String json) {
        try {
            var state = mapper.readValue(json, ManagedDependencyState.class);
            return state.nodes.stream().anyMatch(node -> StringUtils.hasText(node.applicationUuid)
                || StringUtils.hasText(node.databaseUuid) || node.applicationCreateIntent || node.databaseCreateIntent
                || !node.volumes.isEmpty());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot inspect owned deployment dependency resources.", error);
        }
    }

    private void add(Map<EnvironmentKey, Environment> occupied, String deployment, String name, String target,
                     String environment, boolean resources) {
        var key = new EnvironmentKey(deployment, target);
        var previous = occupied.get(key);
        occupied.put(key, new Environment(deployment, name, target, environment,
            resources || previous != null && previous.ownedResourcesPresent()));
    }

    public record Environment(String deploymentId, String deploymentName, String targetProfileId,
                              String environment, boolean ownedResourcesPresent) { }
    public record Summary(String customerId, int limit, int used, int available, List<Environment> environments) { }
    private record EnvironmentKey(String deploymentId, String targetProfileId) { }
}
