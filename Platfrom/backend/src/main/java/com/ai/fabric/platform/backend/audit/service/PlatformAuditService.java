package com.ai.fabric.platform.backend.audit.service;

import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformAuditService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuditService.class);

    private final PlatformAuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public PlatformAuditService(PlatformAuditEventRepository repository,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<PlatformAuditEventSummary> listRecentEvents() {
        return repository.findTop100ByOrderByCreatedAtDesc().stream()
            .map(this::toSummary)
            .toList();
    }

    public List<PlatformAuditEventSummary> listRecentEventsForDeployment(String deploymentId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        return repository.findTop500ByOrderByCreatedAtDesc().stream()
            .map(this::toSummary)
            .filter(event -> matchesDeployment(event, deploymentId))
            .limit(normalizedLimit)
            .toList();
    }

    @Transactional
    public void record(String action, String targetType, String targetId, Map<String, ?> details) {
        PlatformAuditEventEntity entity = new PlatformAuditEventEntity();
        entity.setId("aud-" + UUID.randomUUID().toString().substring(0, 8));
        entity.setActorId(PlatformSecurityContext.actorIdOrSystem());
        entity.setActorRole(PlatformSecurityContext.actorRoleOrSystem());
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetailsJson(writeJson(details));
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
        log.info(
            "AUDIT action={} actor={} role={} targetType={} targetId={}",
            action,
            entity.getActorId(),
            entity.getActorRole(),
            targetType,
            targetId
        );
    }

    private String writeJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize audit details.", ex);
        }
    }

    private PlatformAuditEventSummary toSummary(PlatformAuditEventEntity entity) {
        try {
            JsonNode details = objectMapper.readTree(entity.getDetailsJson());
            return new PlatformAuditEventSummary(
                entity.getId(),
                entity.getActorId(),
                entity.getActorRole(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                details,
                entity.getCreatedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read audit event details.", ex);
        }
    }

    private boolean matchesDeployment(PlatformAuditEventSummary event, String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return false;
        }
        if (deploymentId.equals(event.targetId())) {
            return true;
        }

        JsonNode details = event.details();
        if (details == null || details.isNull()) {
            return false;
        }

        JsonNode directDeploymentId = details.path("deploymentId");
        if (deploymentId.equals(directDeploymentId.asText(null))) {
            return true;
        }

        JsonNode deploymentIds = details.path("deploymentIds");
        if (deploymentIds.isArray()) {
            for (JsonNode deploymentIdNode : deploymentIds) {
                if (deploymentId.equals(deploymentIdNode.asText())) {
                    return true;
                }
            }
        }

        return false;
    }
}
