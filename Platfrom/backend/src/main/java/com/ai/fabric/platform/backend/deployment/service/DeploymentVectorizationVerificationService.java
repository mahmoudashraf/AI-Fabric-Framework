package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeploymentVectorizationVerificationService {

    private final VectorizationPlanRepository planRepository;
    private final VectorizationPlanRevisionRepository revisionRepository;
    private final VectorizationSourceConnectionRepository connectionRepository;
    private final VectorizationRunnerRegistrationRepository registrationRepository;
    private final VectorizationRunnerSessionRepository sessionRepository;
    private final VectorizationService vectorizationService;

    public DeploymentVectorizationVerificationService(VectorizationPlanRepository planRepository,
                                                      VectorizationPlanRevisionRepository revisionRepository,
                                                      VectorizationSourceConnectionRepository connectionRepository,
                                                      VectorizationRunnerRegistrationRepository registrationRepository,
                                                      VectorizationRunnerSessionRepository sessionRepository,
                                                      VectorizationService vectorizationService) {
        this.planRepository = planRepository;
        this.revisionRepository = revisionRepository;
        this.connectionRepository = connectionRepository;
        this.registrationRepository = registrationRepository;
        this.sessionRepository = sessionRepository;
        this.vectorizationService = vectorizationService;
    }

    public DeploymentVectorizationVerificationSummary build(DeploymentEntity deployment, JsonNode entityConfig) {
        VectorizationPlanEntity planEntity = planRepository.findByDeploymentId(deployment.getId()).orElse(null);
        VectorizationPlanRevisionEntity revisionEntity = activeRevision(planEntity);
        VectorizationSourceConnectionEntity connectionEntity = resolveConnection(deployment.getId(), planEntity, revisionEntity);
        VectorizationRunnerRegistrationEntity registrationEntity = registrationRepository.findByDeploymentId(deployment.getId()).orElse(null);
        VectorizationRunnerSessionEntity sessionEntity = latestSession(registrationEntity);

        VectorizationPlanSummary plan = vectorizationService.summarizePlan(planEntity, revisionEntity);
        VectorizationSourceConnectionSummary sourceConnection = vectorizationService.summarizeConnection(connectionEntity);
        VectorizationRunnerSummary runner = vectorizationService.summarizeRunner(registrationEntity, sessionEntity);

        List<String> availableEntityTypes = configuredEntityTypes(entityConfig);
        List<String> entityScope = resolveEntityScope(plan, availableEntityTypes);

        boolean planPresent = plan != null;
        boolean activeRevisionPresent = plan != null && plan.activeRevision() != null;
        boolean sourceConnectionPresent = sourceConnection != null;
        boolean sourceLinked = sourceConnectionPresent && sourceConnectionReferencesMatch(sourceConnection.id(), planEntity, revisionEntity);
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

    private VectorizationPlanRevisionEntity activeRevision(VectorizationPlanEntity planEntity) {
        if (planEntity == null || !StringUtils.hasText(planEntity.getActiveRevisionId())) {
            return null;
        }
        return revisionRepository.findById(planEntity.getActiveRevisionId()).orElse(null);
    }

    private VectorizationSourceConnectionEntity resolveConnection(String deploymentId,
                                                                 VectorizationPlanEntity planEntity,
                                                                 VectorizationPlanRevisionEntity revisionEntity) {
        VectorizationSourceConnectionEntity entity = connectionRepository.findByDeploymentId(deploymentId).orElse(null);
        if (entity != null) {
            return entity;
        }
        String sourceConnectionId = trimToNull(revisionEntity == null ? null : revisionEntity.getSourceConnectionId());
        if (sourceConnectionId == null) {
            sourceConnectionId = trimToNull(planEntity == null ? null : planEntity.getSourceConnectionId());
        }
        if (sourceConnectionId == null) {
            return null;
        }
        return connectionRepository.findById(sourceConnectionId).orElse(null);
    }

    private VectorizationRunnerSessionEntity latestSession(VectorizationRunnerRegistrationEntity registrationEntity) {
        if (registrationEntity == null) {
            return null;
        }
        return sessionRepository.findByRegistrationIdOrderByUpdatedAtDesc(registrationEntity.getId()).stream()
            .findFirst()
            .orElse(null);
    }

    private boolean sourceConnectionReferencesMatch(String connectionId,
                                                    VectorizationPlanEntity planEntity,
                                                    VectorizationPlanRevisionEntity revisionEntity) {
        String planSourceConnectionId = trimToNull(planEntity == null ? null : planEntity.getSourceConnectionId());
        String revisionSourceConnectionId = trimToNull(revisionEntity == null ? null : revisionEntity.getSourceConnectionId());
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
