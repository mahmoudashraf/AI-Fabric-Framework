package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Projects reviewed AI Workspace origins into the next immutable deployment version. */
@Service
public class AIWorkspaceDeploymentSecurityProjectionService {

    static final String MANAGED_ORIGINS_FIELD = "aiWorkspaceManagedCorsOrigins";
    private static final int MAX_MANAGED_ORIGINS = 20;

    private final AIWorkspaceInstallationRepository installations;
    private final PlatformConsumerRepository consumers;
    private final DeploymentRepository deployments;
    private final DeploymentDraftRepository drafts;
    private final AIWorkspaceConfigurationValidator configurationValidator;
    private final PlatformAuditService audit;
    private final ObjectMapper objectMapper;

    public AIWorkspaceDeploymentSecurityProjectionService(AIWorkspaceInstallationRepository installations,
                                                           PlatformConsumerRepository consumers,
                                                           DeploymentRepository deployments,
                                                           DeploymentDraftRepository drafts,
                                                           AIWorkspaceConfigurationValidator configurationValidator,
                                                           PlatformAuditService audit,
                                                           ObjectMapper objectMapper) {
        this.installations = installations;
        this.consumers = consumers;
        this.deployments = deployments;
        this.drafts = drafts;
        this.configurationValidator = configurationValidator;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void synchronizeForConsumer(PlatformConsumerEntity consumer) {
        if (consumer == null || !StringUtils.hasText(consumer.getBoundDeploymentId())) {
            throw new ResponseStatusException(CONFLICT,
                "AI Workspace installation requires a deployment-bound consumer before configuration.");
        }
        synchronizeDeployment(consumer.getBoundDeploymentId());
    }

    /** Locks deployment authority before reviewed-offer authorization takes the entitlement lock. */
    @Transactional
    public void lockDeploymentsForConsumers(Collection<PlatformConsumerEntity> scopedConsumers) {
        if (scopedConsumers == null || scopedConsumers.isEmpty()) {
            throw new ResponseStatusException(CONFLICT,
                "AI Workspace configuration requires a deployment-bound consumer.");
        }
        TreeSet<String> deploymentIds = new TreeSet<>();
        for (PlatformConsumerEntity consumer : scopedConsumers) {
            if (consumer == null || !StringUtils.hasText(consumer.getBoundDeploymentId())) {
                throw new ResponseStatusException(CONFLICT,
                    "AI Workspace configuration requires a deployment-bound consumer.");
            }
            deploymentIds.add(consumer.getBoundDeploymentId());
        }
        deploymentIds.forEach(deploymentId -> deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId)));
    }

    @Transactional
    public void synchronizeBindingChange(String consumerEntityId,
                                         String previousDeploymentId,
                                         String nextDeploymentId) {
        if (!StringUtils.hasText(consumerEntityId)
            || installations.findByConsumerEntityIdAndStatusIn(consumerEntityId,
                List.of(AIWorkspaceInstallationStatus.DRAFT, AIWorkspaceInstallationStatus.ACTIVE)).isEmpty()) {
            return;
        }
        TreeSet<String> affectedDeployments = new TreeSet<>();
        if (StringUtils.hasText(previousDeploymentId)) affectedDeployments.add(previousDeploymentId);
        if (StringUtils.hasText(nextDeploymentId)) affectedDeployments.add(nextDeploymentId);
        affectedDeployments.forEach(this::synchronizeDeployment);
    }

    @Transactional
    public void synchronizeDeployment(String deploymentId) {
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        if (deployment.getArchivedAt() != null || "ARCHIVED".equalsIgnoreCase(deployment.getStatus())
            || !StringUtils.hasText(deployment.getActiveDraftId())) {
            throw new ResponseStatusException(CONFLICT,
                "AI Workspace origins require an active editable deployment draft.");
        }
        var draft = drafts.findByIdForUpdate(deployment.getActiveDraftId())
            .filter(candidate -> deploymentId.equals(candidate.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(CONFLICT,
                "The active deployment draft requires reconciliation before AI Workspace configuration."));
        if ("PUBLISHED".equalsIgnoreCase(draft.getStatus())) {
            throw new ResponseStatusException(CONFLICT,
                "AI Workspace origins cannot modify a published deployment draft.");
        }

        ObjectNode security = readSecurity(draft.getSecurityConfigJson());
        LinkedHashSet<String> manual = new LinkedHashSet<>(csv(security.path("corsAllowedOrigins").asText("")));
        manual.removeAll(previousManagedOrigins(security));

        List<String> consumerIds = consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc(deploymentId).stream()
            .map(PlatformConsumerEntity::getId)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        var projectedInstallations = consumerIds.isEmpty() ? List.<AIWorkspaceInstallationEntity>of()
            : installations.findByConsumerEntityIdInAndStatusIn(consumerIds,
                List.of(AIWorkspaceInstallationStatus.DRAFT, AIWorkspaceInstallationStatus.ACTIVE));
        TreeSet<String> desired = new TreeSet<>();
        projectedInstallations.forEach(installation -> desired.addAll(readOrigins(installation.getAllowedOriginsJson())));
        if (desired.size() > MAX_MANAGED_ORIGINS) {
            throw new ResponseStatusException(CONFLICT,
                "The deployment exceeds the supported number of exact AI Workspace origins.");
        }
        if (!desired.isEmpty()) {
            var normalized = configurationValidator.normalizeOrigins(new ArrayList<>(desired));
            desired.clear();
            desired.addAll(normalized);
        }

        TreeSet<String> managed = new TreeSet<>(desired);
        managed.removeAll(manual);
        LinkedHashSet<String> combined = new LinkedHashSet<>(manual);
        combined.addAll(desired);
        if (combined.isEmpty()) security.remove("corsAllowedOrigins");
        else security.put("corsAllowedOrigins", String.join(",", combined));
        ArrayNode managedNode = security.putArray(MANAGED_ORIGINS_FIELD);
        managed.forEach(managedNode::add);

        String updated = writeJson(security);
        if (updated.equals(draft.getSecurityConfigJson())) return;
        Instant now = Instant.now();
        draft.setSecurityConfigJson(updated);
        draft.setStatus("MODIFIED");
        draft.setUpdatedAt(now);
        drafts.save(draft);
        deployment.setStatus("DRAFT");
        deployment.setUpdatedAt(now);
        deployments.save(deployment);
        audit.record("AI_WORKSPACE_DEPLOYMENT_ORIGINS_SYNCHRONIZED", "DEPLOYMENT_DRAFT", draft.getId(), Map.of(
            "deploymentId", deploymentId,
            "managedOriginCount", managed.size()
        ));
    }

    private ObjectNode readSecurity(String raw) {
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (parsed != null && parsed.isObject()) return (ObjectNode) parsed.deepCopy();
        } catch (Exception ignored) {
            // Converted to an explicit reconciliation failure below.
        }
        throw new ResponseStatusException(CONFLICT,
            "Deployment security configuration requires reconciliation before AI Workspace configuration.");
    }

    private List<String> previousManagedOrigins(ObjectNode security) {
        JsonNode node = security.path(MANAGED_ORIGINS_FIELD);
        if (node.isMissingNode()) return List.of();
        if (!node.isArray()) {
            throw new ResponseStatusException(CONFLICT,
                "AI Workspace origin projection metadata requires reconciliation.");
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (!value.isTextual() || !StringUtils.hasText(value.asText())) {
                throw new ResponseStatusException(CONFLICT,
                    "AI Workspace origin projection metadata requires reconciliation.");
            }
            values.add(value.asText().trim());
        });
        return values;
    }

    private List<String> readOrigins(String raw) {
        try {
            List<String> values = objectMapper.readValue(raw, new TypeReference<>() { });
            if (values == null || values.stream().anyMatch(value -> !StringUtils.hasText(value))) throw new IllegalArgumentException();
            return values;
        } catch (Exception error) {
            throw new ResponseStatusException(CONFLICT,
                "Stored AI Workspace origins require reconciliation before deployment publication.");
        }
    }

    private List<String> csv(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to serialize deployment security configuration.", error);
        }
    }
}
