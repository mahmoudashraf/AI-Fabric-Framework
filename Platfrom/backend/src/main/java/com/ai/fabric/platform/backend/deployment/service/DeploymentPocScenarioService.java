package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocScenarioEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocScenarioSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentPocScenarioRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocScenarioRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentPocScenarioService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentPocScenarioRepository deploymentPocScenarioRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentPocScenarioService(DeploymentRepository deploymentRepository,
                                        DeploymentDraftRepository deploymentDraftRepository,
                                        DeploymentVersionRepository deploymentVersionRepository,
                                        DeploymentPocScenarioRepository deploymentPocScenarioRepository,
                                        DeploymentAccessService deploymentAccessService,
                                        PlatformAuditService platformAuditService,
                                        ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentDraftRepository = deploymentDraftRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentPocScenarioRepository = deploymentPocScenarioRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public List<DeploymentPocScenarioSummary> listScenarios(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        List<DeploymentPocScenarioSummary> scenarios = new ArrayList<>(builtInScenarios(deployment));
        deploymentPocScenarioRepository.findByDeploymentIdOrderByCreatedAtAsc(deployment.getId())
            .stream()
            .map(this::toSummary)
            .forEach(scenarios::add);
        return scenarios;
    }

    public DeploymentPocScenarioSummary createScenario(String deploymentId, UpsertDeploymentPocScenarioRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        String title = required(request == null ? null : request.title(), "title");
        String prompt = required(request == null ? null : request.prompt(), "prompt");
        String category = hasText(request.category()) ? request.category().trim() : "Custom";
        String expectedOutcome = hasText(request.expectedOutcome()) ? request.expectedOutcome().trim() : null;
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        Instant now = Instant.now();

        DeploymentPocScenarioEntity entity = new DeploymentPocScenarioEntity();
        entity.setId(generateId("psc"));
        entity.setDeploymentId(deployment.getId());
        entity.setTitle(title);
        entity.setCategory(category);
        entity.setPromptText(prompt);
        entity.setExpectedOutcome(expectedOutcome);
        entity.setCreatedByActorId(principal == null ? "system" : principal.actorId());
        entity.setCreatedByDisplayName(principal == null ? null : principal.displayName());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        deploymentPocScenarioRepository.save(entity);

        platformAuditService.record(
            "DEPLOYMENT_POC_SCENARIO_CREATED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("scenarioId", entity.getId(), "title", title, "category", category)
        );
        return toSummary(entity);
    }

    public DeploymentPocScenarioSummary updateScenario(String deploymentId,
                                                       String scenarioId,
                                                       UpsertDeploymentPocScenarioRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentPocScenarioEntity entity = deploymentPocScenarioRepository.findById(scenarioId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "POC scenario not found: " + scenarioId));
        if (!deployment.getId().equals(entity.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "POC scenario not found: " + scenarioId);
        }

        entity.setTitle(required(request == null ? null : request.title(), "title"));
        entity.setPromptText(required(request == null ? null : request.prompt(), "prompt"));
        entity.setCategory(hasText(request.category()) ? request.category().trim() : "Custom");
        entity.setExpectedOutcome(hasText(request.expectedOutcome()) ? request.expectedOutcome().trim() : null);
        entity.setUpdatedAt(Instant.now());
        deploymentPocScenarioRepository.save(entity);

        platformAuditService.record(
            "DEPLOYMENT_POC_SCENARIO_UPDATED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("scenarioId", entity.getId(), "title", entity.getTitle())
        );
        return toSummary(entity);
    }

    public void deleteScenario(String deploymentId, String scenarioId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentPocScenarioEntity entity = deploymentPocScenarioRepository.findById(scenarioId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "POC scenario not found: " + scenarioId));
        if (!deployment.getId().equals(entity.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "POC scenario not found: " + scenarioId);
        }
        deploymentPocScenarioRepository.delete(entity);
        platformAuditService.record(
            "DEPLOYMENT_POC_SCENARIO_DELETED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("scenarioId", entity.getId(), "title", entity.getTitle())
        );
    }

    private List<DeploymentPocScenarioSummary> builtInScenarios(DeploymentEntity deployment) {
        List<String> entityTypes = entityTypesFor(deployment);
        List<String> normalized = entityTypes.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();

        if (normalized.containsAll(List.of("product", "review", "policy"))) {
            return List.of(
                builtIn(
                    "builtin-grounded-catalog",
                    "Grounded catalog summary",
                    "Grounded Answer",
                    "Summarize the current catalog, available policies, and what information you can cite confidently.",
                    "Assistant answers from indexed products, reviews, and policy content."
                ),
                builtIn(
                    "builtin-safe-actions",
                    "Safe action inventory",
                    "Action Safety",
                    "Which live actions can you execute safely for this deployment, and which ones need explicit confirmation?",
                    "Assistant names available actions and clearly separates safe reads from guarded writes."
                ),
                builtIn(
                    "builtin-rollout-readiness",
                    "Rollout readiness check",
                    "Operator",
                    "What should an operator validate before enabling this deployment for external users?",
                    "Assistant gives a deployment-readiness checklist grounded in current knowledge, actions, and indexing state."
                ),
                builtIn(
                    "builtin-policy-question",
                    "Policy retrieval smoke test",
                    "Policy",
                    "Answer a realistic return or refund question using the current policy knowledge only.",
                    "Assistant cites and explains policy answers without inventing unsupported details."
                )
            );
        }

        return List.of(
            builtIn(
                "builtin-knowledge-sources",
                "Knowledge source summary",
                "Grounded Answer",
                "Summarize the knowledge and live data sources available to you for this deployment.",
                "Assistant explains what it can access and where answers will come from."
            ),
            builtIn(
                "builtin-operator-readiness",
                "Operator validation checklist",
                "Operator",
                "What should an operator validate before customer-facing rollout of this deployment?",
                "Assistant provides an operational checklist for testing and release readiness."
            )
        );
    }

    private DeploymentPocScenarioSummary builtIn(String id,
                                                 String title,
                                                 String category,
                                                 String prompt,
                                                 String expectedOutcome) {
        return new DeploymentPocScenarioSummary(id, "builtin", title, category, prompt, expectedOutcome, false, null);
    }

    private List<String> entityTypesFor(DeploymentEntity deployment) {
        JsonNode entityConfig = objectMapper.createObjectNode();
        if (hasText(deployment.getActiveVersionId())) {
            DeploymentVersionEntity version = deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
            if (version != null) {
                entityConfig = readJson(version.getEntityConfigJson());
            }
        } else if (hasText(deployment.getActiveDraftId())) {
            DeploymentDraftEntity draft = deploymentDraftRepository.findById(deployment.getActiveDraftId()).orElse(null);
            if (draft != null) {
                entityConfig = readJson(draft.getEntityConfigJson());
            }
        }

        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (!aiEntities.isObject()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        Iterator<String> fieldNames = aiEntities.fieldNames();
        while (fieldNames.hasNext()) {
            entityTypes.add(fieldNames.next());
        }
        return entityTypes;
    }

    private JsonNode readJson(String value) {
        if (!hasText(value)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAccess(deployment);
    }

    private DeploymentPocScenarioSummary toSummary(DeploymentPocScenarioEntity entity) {
        return new DeploymentPocScenarioSummary(
            entity.getId(),
            "custom",
            entity.getTitle(),
            entity.getCategory(),
            entity.getPromptText(),
            entity.getExpectedOutcome(),
            true,
            entity.getCreatedAt()
        );
    }

    private String required(String value, String fieldName) {
        if (!hasText(value)) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required.");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String generateId(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
