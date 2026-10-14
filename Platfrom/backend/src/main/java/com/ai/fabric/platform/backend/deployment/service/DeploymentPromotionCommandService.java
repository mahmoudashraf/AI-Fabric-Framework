package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentPromotionCommandEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromotionCommandRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

@Service
public class DeploymentPromotionCommandService {
    private final DeploymentPromotionCommandRepository commands;
    private final PlatformConsumerRepository consumers;
    private final ObjectMapper mapper;

    public DeploymentPromotionCommandService(DeploymentPromotionCommandRepository commands,
                                             PlatformConsumerRepository consumers, ObjectMapper mapper) {
        this.commands = commands; this.consumers = consumers; this.mapper = mapper;
    }

    @Transactional
    public DeploymentPracticalPromotionSummary execute(String customerId, String deploymentId, String consumerId,
                                                       String kind, String key, Long expectedRevision, Object payload,
                                                       Function<PlatformConsumerEntity, DeploymentPracticalPromotionSummary> action) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}") || expectedRevision == null || expectedRevision < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion requires a bounded operationKey and expectedBindingRevision.");
        }
        PlatformConsumerEntity consumer = consumers.findBindingForUpdate(customerId, consumerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer consumer not found."));
        var principal = PlatformSecurityContext.currentPrincipal();
        var request = mapper.createObjectNode();
        request.put("kind", kind); request.put("deploymentId", deploymentId);
        request.put("actor", principal == null ? "system" : principal.actorId());
        request.put("role", principal == null ? "SYSTEM" : principal.role().name());
        request.set("payload", mapper.valueToTree(payload));
        String fingerprint = DeploymentEvidenceFingerprint.hash(mapper, request);
        var previous = commands.findByConsumerEntityIdAndOperationKey(consumer.getId(), key);
        if (previous.isPresent()) {
            if (!kind.equals(previous.get().getCommandType()) || !fingerprint.equals(previous.get().getRequestFingerprint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion operationKey was used for another command.");
            }
            try {
                return mapper.readValue(previous.get().getResultJson(), DeploymentPracticalPromotionSummary.class);
            } catch (Exception error) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Persisted promotion result requires reconciliation.");
            }
        }
        if (!"ACTIVE".equals(consumer.getStatus()) || consumer.getBindingRevision() != expectedRevision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Consumer binding is inactive or changed; refresh the promotion plan.");
        }
        var result = action.apply(consumer);
        var operation = new DeploymentPromotionCommandEntity();
        operation.setId("dpc-" + UUID.randomUUID());
        operation.setCustomerId(customerId); operation.setConsumerEntityId(consumer.getId());
        operation.setOperationKey(key); operation.setCommandType(kind); operation.setRequestFingerprint(fingerprint);
        operation.setCreatedAt(Instant.now());
        try { operation.setResultJson(mapper.writeValueAsString(result)); }
        catch (Exception error) { throw new IllegalStateException("Cannot persist promotion result.", error); }
        commands.save(operation);
        return result;
    }
}
