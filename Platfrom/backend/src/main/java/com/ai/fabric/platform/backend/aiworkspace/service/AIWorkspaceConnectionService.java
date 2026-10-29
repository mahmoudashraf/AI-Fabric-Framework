package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceConnectionRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerBindingHistoryRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Creates a new connection, never a command to move a caller-selected shared assignment. */
@Service
public class AIWorkspaceConnectionService {
    private final DeploymentRepository deployments;
    private final PlatformConsumerRepository consumers;
    private final PlatformConsumerBindingHistoryRepository history;
    private final CustomerSelfServiceOperationBoundary boundary;
    private final PlatformCustomerConsumerService consumerService;
    private final EntityManager entityManager;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final ObjectMapper objectMapper;

    public AIWorkspaceConnectionService(DeploymentRepository deployments, PlatformConsumerRepository consumers,
            PlatformConsumerBindingHistoryRepository history, CustomerSelfServiceOperationBoundary boundary,
            PlatformCustomerConsumerService consumerService, EntityManager entityManager,
            DeploymentReleaseRepository releases, DeploymentVersionRepository versions, ObjectMapper objectMapper) {
        this.deployments = deployments;
        this.consumers = consumers;
        this.history = history;
        this.boundary = boundary;
        this.consumerService = consumerService;
        this.entityManager = entityManager;
        this.releases = releases;
        this.versions = versions;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN')")
    public PlatformConsumerSummary create(String customerId, CreateAIWorkspaceConnectionRequest request) {
        validate(customerId, request);
        String consumerId = "engage-" + UUID.nameUUIDFromBytes(
            ("ai-workspace-connection-v1\n" + customerId + "\n" + request.operationKey()).getBytes(StandardCharsets.UTF_8));
        // Preserve the normal assignment lock order: existing consumer, then deployment.
        var existing = consumers.findBindingForUpdate(customerId, consumerId).orElse(null);
        if (existing != null) entityManager.refresh(existing);
        var deployment = deployments.findByIdForUpdate(request.deploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Owned deployment not found."));
        entityManager.refresh(deployment);
        if (!customerId.equals(deployment.getCustomerId())) {
            throw new ResponseStatusException(NOT_FOUND, "Owned deployment not found.");
        }
        boundary.require(deployment, request.targetProfileId(), request.releaseId(), Operation.APP_CONFIGURE,
            request.operationKey(), Map.of(), null, null, null);

        // Include the display name in the reason so the normal binding fingerprint covers it.
        String reason = "Engage connection: " + request.displayName();
        if (existing != null) {
            requireCurrentOriginalBinding(existing, request, reason);
        } else {
            consumerService.createConsumer(customerId, new CreatePlatformConsumerRequest(
                consumerId, request.displayName(), null, null, null));
        }
        return consumerService.updateBinding(customerId, consumerId, new UpdatePlatformConsumerBindingRequest(
            request.deploymentId(), request.releaseId(), request.targetProfileId(), reason, 0L, request.operationKey()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN')")
    public PreviewCapabilities previewCapabilities(String customerId, String consumerId) {
        if (invalidText(customerId, 64) || invalidText(consumerId, 128)) {
            throw new ResponseStatusException(BAD_REQUEST, "An exact customer and consumer are required.");
        }
        // Reuse the owned consumer read, including the current customer/account access check.
        var consumer = consumerService.listConsumers(customerId).stream()
            .filter(value -> consumerId.equalsIgnoreCase(value.consumerId()))
            .findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Owned consumer not found."));
        if (!"ACTIVE".equals(consumer.status())) throw conflict("Consumer is disabled.");
        if (consumer.boundDeploymentId() != null) {
            var deployment = deployments.findById(consumer.boundDeploymentId())
                .filter(value -> customerId.equals(value.getCustomerId()))
                .orElseThrow(() -> conflict("Consumer deployment ownership changed."));
            boundary.require(deployment, consumer.boundTargetProfileId(), consumer.boundReleaseId(), Operation.READ,
                null, Map.of(), null, null, null);
        }
        if (consumer.boundReleaseId() == null) {
            return previewCapabilities(null, objectMapper.createObjectNode());
        }
        // Only the exact assigned published snapshot can inform this static preview, never a draft or latest release.
        var release = releases.findById(consumer.boundReleaseId())
            .filter(value -> Objects.equals(consumer.boundDeploymentId(), value.getDeploymentId())
                && Objects.equals(consumer.boundTargetProfileId(), value.getTargetProfileId()))
            .orElseThrow(() -> conflict("Consumer release assignment is inconsistent."));
        var version = versions.findById(release.getDeploymentVersionId())
            .filter(value -> Objects.equals(consumer.boundDeploymentId(), value.getDeploymentId())
                && "PUBLISHED".equals(value.getStatus()))
            .orElseThrow(() -> conflict("Assigned published version is unavailable."));
        JsonNode behavior;
        try {
            behavior = objectMapper.readTree(version.getBehaviorConfigJson());
        } catch (Exception error) {
            throw conflict("Assigned release capability configuration is invalid.");
        }
        if (behavior == null || !behavior.isObject()) throw conflict("Assigned release capability configuration is invalid.");
        return previewCapabilities(true, behavior);
    }

    private PreviewCapabilities previewCapabilities(Boolean runtimeConfigured, JsonNode behavior) {
        var handover = AIWorkspacePublicHandoverProjection.project(objectMapper, behavior);
        return new PreviewCapabilities(runtimeConfigured, new PreviewHandover(
            handover.path("enabled").asBoolean(false), handover.path("userCanCancelPending").asBoolean(false),
            handover.path("userCanResumeAi").asBoolean(false), handover.get("intake")));
    }

    public record PreviewCapabilities(@JsonInclude(JsonInclude.Include.ALWAYS) Boolean runtimeConfigured,
                                      PreviewHandover handover) { }

    public record PreviewHandover(boolean enabled, boolean userCanCancelPending, boolean userCanResumeAi,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode intake) { }

    private void requireCurrentOriginalBinding(PlatformConsumerEntity consumer,
            CreateAIWorkspaceConnectionRequest request, String reason) {
        var original = history.findByConsumerEntityIdAndOperationKey(consumer.getId(), request.operationKey())
            .orElseThrow(() -> conflict("Connection identifier already exists without its original binding command."));
        // A historical result must not present a moved/shared assignment as the requested current connection.
        if (!Objects.equals(original.getFromBindingRevision(), 0L)
            || !Objects.equals(original.getToBindingRevision(), 1L)
            || original.getFromDeploymentId() != null || original.getFromTargetProfileId() != null
            || original.getFromReleaseId() != null || !reason.equals(original.getReason())
            || !request.deploymentId().equals(original.getToDeploymentId())
            || !request.targetProfileId().equals(original.getToTargetProfileId())
            || !Objects.equals(request.releaseId(), original.getToReleaseId())
            || consumer.getBindingRevision() != original.getToBindingRevision()
            || !"ACTIVE".equals(consumer.getStatus()) || !request.displayName().equals(consumer.getDisplayName())
            || !request.deploymentId().equals(consumer.getBoundDeploymentId())
            || !request.targetProfileId().equals(consumer.getBoundTargetProfileId())
            || !Objects.equals(request.releaseId(), consumer.getBoundReleaseId())) {
            throw conflict("Connection command or current assignment changed; read the current connection before continuing.");
        }
        // updateBinding checks the authoritative fingerprint and returns its persisted result without mutation.
    }

    private void validate(String customerId, CreateAIWorkspaceConnectionRequest request) {
        // These bounds protect exact resource selection, durable command identity and consumer storage limits.
        if (invalidText(customerId, 64) || request == null
            || invalidText(request.deploymentId(), 64) || invalidText(request.targetProfileId(), 64)
            || (request.releaseId() != null && invalidText(request.releaseId(), 64))
            || invalidText(request.displayName(), 255) || request.displayName().length() < 2
            || request.operationKey() == null || !request.operationKey().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ResponseStatusException(BAD_REQUEST, "An exact deployment, target, display name and bounded operationKey are required.");
        }
    }

    private boolean invalidText(String value, int max) {
        return value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(CONFLICT, message);
    }
}
