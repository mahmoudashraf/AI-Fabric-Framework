package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocPromptSessionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocPromptSessionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentPocPromptSessionRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocPromptSessionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentPocPromptSessionService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentPocPromptSessionRepository deploymentPocPromptSessionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentPocPromptSessionService(DeploymentRepository deploymentRepository,
                                             DeploymentPocPromptSessionRepository deploymentPocPromptSessionRepository,
                                             DeploymentAccessService deploymentAccessService,
                                             PlatformSecretService platformSecretService,
                                             PlatformAuditService platformAuditService,
                                             ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentPocPromptSessionRepository = deploymentPocPromptSessionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public DeploymentPocPromptSessionSummary getSession(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        ActorContext actor = currentActor();
        return deploymentPocPromptSessionRepository.findByDeploymentIdAndActorId(deployment.getId(), actor.actorId())
            .map(this::toSummary)
            .orElseGet(() -> inactiveSummary(deployment.getId(), actor));
    }

    public DeploymentPocPromptSessionSummary activateSession(String deploymentId,
                                                             UpdateDeploymentPocPromptSessionRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment runtime URL is not available. Apply the deployment before activating prompt hot apply."
            );
        }
        String adminApiKey = platformSecretService.resolveSecret("APP_ADMIN_API_KEY");
        if (!StringUtils.hasText(adminApiKey)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "APP_ADMIN_API_KEY is required before prompt hot apply can be activated."
            );
        }

        ObjectNode promptPreview = DeploymentPocPromptPreviewSupport.sanitizePromptPreview(
            objectMapper,
            request == null ? null : request.promptPreview()
        );
        if (promptPreview == null) {
            throw new ResponseStatusException(BAD_REQUEST, "promptPreview must contain at least one prompt override.");
        }

        ActorContext actor = currentActor();
        Instant now = Instant.now();
        DeploymentPocPromptSessionEntity entity = deploymentPocPromptSessionRepository
            .findByDeploymentIdAndActorId(deployment.getId(), actor.actorId())
            .orElseGet(DeploymentPocPromptSessionEntity::new);

        if (!StringUtils.hasText(entity.getId())) {
            entity.setId("pps-" + UUID.randomUUID().toString().substring(0, 8));
            entity.setCreatedAt(now);
        }
        entity.setDeploymentId(deployment.getId());
        entity.setActorId(actor.actorId());
        entity.setActorDisplayName(actor.displayName());
        entity.setSessionLabel(normalizeLabel(request == null ? null : request.sessionLabel()));
        entity.setPromptPreviewJson(writeJson(promptPreview));
        entity.setUpdatedAt(now);
        DeploymentPocPromptSessionEntity saved = deploymentPocPromptSessionRepository.save(entity);

        platformAuditService.record(
            "DEPLOYMENT_POC_PROMPT_SESSION_ACTIVATED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "promptSessionId", saved.getId(),
                "promptKeyCount", DeploymentPocPromptPreviewSupport.promptKeys(promptPreview).size(),
                "sessionLabel", saved.getSessionLabel() == null ? "" : saved.getSessionLabel()
            )
        );
        return toSummary(saved);
    }

    public void clearSession(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        ActorContext actor = currentActor();
        deploymentPocPromptSessionRepository.findByDeploymentIdAndActorId(deployment.getId(), actor.actorId())
            .ifPresent(entity -> {
                deploymentPocPromptSessionRepository.delete(entity);
                platformAuditService.record(
                    "DEPLOYMENT_POC_PROMPT_SESSION_CLEARED",
                    "DEPLOYMENT",
                    deployment.getId(),
                    Map.of("promptSessionId", entity.getId())
                );
            });
    }

    ObjectNode effectivePromptPreview(String deploymentId) {
        ActorContext actor = currentActor();
        return deploymentPocPromptSessionRepository.findByDeploymentIdAndActorId(deploymentId, actor.actorId())
            .map(this::readPromptPreview)
            .orElse(null);
    }

    private DeploymentPocPromptSessionSummary toSummary(DeploymentPocPromptSessionEntity entity) {
        ObjectNode promptPreview = readPromptPreview(entity);
        List<String> promptKeys = DeploymentPocPromptPreviewSupport.promptKeys(promptPreview);
        return new DeploymentPocPromptSessionSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getActorId(),
            entity.getActorDisplayName(),
            entity.getSessionLabel(),
            true,
            promptKeys.size(),
            promptKeys,
            entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString()
        );
    }

    private DeploymentPocPromptSessionSummary inactiveSummary(String deploymentId, ActorContext actor) {
        return new DeploymentPocPromptSessionSummary(
            null,
            deploymentId,
            actor.actorId(),
            actor.displayName(),
            null,
            false,
            0,
            List.of(),
            null
        );
    }

    private ObjectNode readPromptPreview(DeploymentPocPromptSessionEntity entity) {
        try {
            JsonNode parsed = objectMapper.readTree(entity.getPromptPreviewJson());
            return DeploymentPocPromptPreviewSupport.sanitizePromptPreview(objectMapper, parsed);
        } catch (Exception ex) {
            return null;
        }
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentAccess(deployment);
        return deployment;
    }

    private ActorContext currentActor() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            return new ActorContext("system", "System");
        }
        return new ActorContext(principal.actorId(), principal.displayName());
    }

    private String normalizeLabel(String label) {
        if (!StringUtils.hasText(label)) {
            return "POC hot apply session";
        }
        String trimmed = label.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String writeJson(ObjectNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to store prompt hot apply session.", ex);
        }
    }

    private record ActorContext(String actorId, String displayName) {
    }
}
