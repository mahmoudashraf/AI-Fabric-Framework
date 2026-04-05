package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeploymentVectorizationVerificationService {

    private final VectorizationService vectorizationService;

    public DeploymentVectorizationVerificationService(VectorizationService vectorizationService) {
        this.vectorizationService = vectorizationService;
    }

    public DeploymentVectorizationVerificationSummary build(DeploymentEntity deployment, JsonNode entityConfig) {
        VectorizationOverviewSummary overview = vectorizationService.getOverviewForTrustedCaller(deployment);
        VectorizationPlanSummary plan = overview.plan();
        VectorizationSourceConnectionSummary sourceConnection = overview.sourceConnection();
        VectorizationRunnerSummary runner = overview.runner();

        List<String> availableEntityTypes = configuredEntityTypes(entityConfig);
        List<String> entityScope = resolveEntityScope(plan, availableEntityTypes);

        boolean planPresent = plan != null;
        boolean activeRevisionPresent = plan != null && plan.activeRevision() != null;
        boolean sourceConnectionPresent = sourceConnection != null;
        boolean sourceLinked = sourceConnectionPresent && sourceConnectionReferencesMatch(sourceConnection.id(), plan);
        boolean configured = planPresent && activeRevisionPresent && sourceConnectionPresent && sourceLinked;

        String runnerMode = plan != null && StringUtils.hasText(plan.runnerMode())
            ? plan.runnerMode()
            : runner == null ? null : runner.runnerMode();
        boolean platformManagedRunnerExpected = configured && "PLATFORM_MANAGED_AUTO".equalsIgnoreCase(runnerMode);
        boolean runnerRequired = configured && (
            "PLATFORM_MANAGED_AUTO".equalsIgnoreCase(runnerMode)
                || "CUSTOMER_MANAGED_REMOTE".equalsIgnoreCase(runnerMode)
        );

        return new DeploymentVectorizationVerificationSummary(
            deployment.getId(),
            planPresent,
            sourceConnectionPresent,
            activeRevisionPresent,
            configured,
            runner != null,
            runnerRequired,
            platformManagedRunnerExpected,
            availableEntityTypes,
            entityScope,
            sourceConnection,
            plan,
            runner
        );
    }

    private boolean sourceConnectionReferencesMatch(String connectionId, VectorizationPlanSummary plan) {
        if (!StringUtils.hasText(connectionId)) {
            return false;
        }
        String planSourceConnectionId = trimToNull(plan == null ? null : plan.sourceConnectionId());
        String revisionSourceConnectionId = trimToNull(
            plan == null || plan.activeRevision() == null ? null : plan.activeRevision().sourceConnectionId()
        );
        boolean planMatches = planSourceConnectionId == null || planSourceConnectionId.equals(connectionId);
        boolean revisionMatches = revisionSourceConnectionId == null || revisionSourceConnectionId.equals(connectionId);
        return planMatches && revisionMatches;
    }

    private List<String> configuredEntityTypes(JsonNode entityConfig) {
        List<String> entityTypes = new ArrayList<>();
        JsonNode aiEntities = entityConfig == null ? null : entityConfig.path("ai-entities");
        if (aiEntities != null && aiEntities.isObject()) {
            aiEntities.fieldNames().forEachRemaining(name -> {
                if (StringUtils.hasText(name)) {
                    entityTypes.add(name.trim());
                }
            });
        }
        return entityTypes.stream().distinct().sorted().toList();
    }

    private List<String> resolveEntityScope(VectorizationPlanSummary plan, List<String> availableEntityTypes) {
        if (plan == null || plan.activeRevision() == null || plan.activeRevision().entityScope() == null) {
            return availableEntityTypes;
        }
        List<String> entityScope = new ArrayList<>();
        JsonNode entityScopeNode = plan.activeRevision().entityScope();
        if (entityScopeNode.isArray()) {
            entityScopeNode.forEach(item -> {
                String value = trimToNull(item.asText());
                if (value != null) {
                    entityScope.add(value);
                }
            });
        }
        if (entityScope.isEmpty()) {
            return availableEntityTypes;
        }
        return entityScope.stream()
            .distinct()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
