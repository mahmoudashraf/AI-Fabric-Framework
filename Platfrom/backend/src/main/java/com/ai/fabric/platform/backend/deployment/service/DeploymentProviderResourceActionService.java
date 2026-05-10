package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceHandleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLogsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentProviderResourceActionService {

    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final DeploymentProviderRegistry providerRegistry;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentProviderResourceActionService(DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                                   DeploymentTargetProfileRepository targetProfileRepository,
                                                   DeploymentProviderRegistry providerRegistry,
                                                   PlatformAuditService platformAuditService,
                                                   ObjectMapper objectMapper) {
        this.resourceHandleRepository = resourceHandleRepository;
        this.targetProfileRepository = targetProfileRepository;
        this.providerRegistry = providerRegistry;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<DeploymentProviderResourceHandleSummary> listResources(DeploymentProviderType providerType,
                                                                       String deploymentId,
                                                                       String targetProfileId) {
        List<DeploymentProviderResourceHandleEntity> handles;
        if (StringUtils.hasText(deploymentId)) {
            handles = resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId.trim());
        } else if (StringUtils.hasText(targetProfileId)) {
            handles = resourceHandleRepository.findByTargetProfileIdOrderByUpdatedAtDesc(targetProfileId.trim());
        } else if (providerType != null) {
            handles = resourceHandleRepository.findByProviderTypeOrderByUpdatedAtDesc(providerType);
        } else {
            handles = resourceHandleRepository.findAll().stream()
                .sorted(Comparator.comparing(DeploymentProviderResourceHandleEntity::getUpdatedAt).reversed())
                .toList();
        }
        return handles.stream().map(this::toSummary).toList();
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderPreflightSummary preflight(String targetProfileId) {
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment target profile not found: " + targetProfileId
            ));
        return providerRegistry.require(profile.getProviderType()).preflight(profile);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary start(String handleId, DeploymentProviderResourceActionRequest request) {
        return recordAction(handleId, "START", request, handle -> providerRegistry.require(handle.getProviderType())
            .start(handle, reason(request)));
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary stop(String handleId, DeploymentProviderResourceActionRequest request) {
        return recordAction(handleId, "STOP", request, handle -> providerRegistry.require(handle.getProviderType())
            .stop(handle, reason(request)));
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary restart(String handleId, DeploymentProviderResourceActionRequest request) {
        return recordAction(handleId, "RESTART", request, handle -> providerRegistry.require(handle.getProviderType())
            .restart(handle, reason(request)));
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceActionSummary delete(String handleId, DeploymentProviderResourceActionRequest request) {
        return recordAction(handleId, "DELETE", request, handle -> providerRegistry.require(handle.getProviderType())
            .delete(handle, reason(request)));
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceStatusSummary status(String handleId) {
        DeploymentProviderResourceHandleEntity handle = requireHandle(handleId);
        DeploymentProviderResourceStatusSummary status = providerRegistry.require(handle.getProviderType()).status(handle);
        handle.setStatus(status.status());
        handle.setLastObservedStatus(status.observedStatus());
        handle.setLastObservedAt(status.observedAt());
        handle.setFqdn(status.fqdn());
        handle.setUpdatedAt(Instant.now());
        resourceHandleRepository.save(handle);
        return status;
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentProviderResourceLogsSummary logs(String handleId, int lines) {
        DeploymentProviderResourceHandleEntity handle = requireHandle(handleId);
        DeploymentProviderResourceLogsSummary logs = providerRegistry.require(handle.getProviderType()).logs(handle, lines);
        platformAuditService.record(
            "DEPLOYMENT_PROVIDER_RESOURCE_LOGS_VIEWED",
            "DEPLOYMENT_PROVIDER_RESOURCE_HANDLE",
            handle.getId(),
            Map.of("deploymentId", handle.getDeploymentId(), "lines", logs.lines())
        );
        return logs;
    }

    public DeploymentProviderResourceHandleSummary toSummary(DeploymentProviderResourceHandleEntity handle) {
        return new DeploymentProviderResourceHandleSummary(
            handle.getId(),
            handle.getDeploymentId(),
            handle.getReleaseId(),
            handle.getTargetProfileId(),
            handle.getProviderType(),
            handle.getResourceKind(),
            handle.getProviderResourceUuid(),
            handle.getProviderProjectUuid(),
            handle.getProviderEnvironmentUuid(),
            handle.getProviderServerUuid(),
            handle.getFqdn(),
            handle.getStatus(),
            handle.getLastObservedStatus(),
            handle.getLastObservedAt(),
            readJson(handle.getMetadataJson()),
            handle.getCreatedAt(),
            handle.getUpdatedAt()
        );
    }

    private DeploymentProviderResourceActionSummary recordAction(String handleId,
                                                                 String action,
                                                                 DeploymentProviderResourceActionRequest request,
                                                                 ResourceActionInvoker invoker) {
        DeploymentProviderResourceHandleEntity handle = requireHandle(handleId);
        DeploymentProviderResourceActionSummary result = invoker.invoke(handle);
        String normalizedStatus = "DELETE".equals(action) ? "DELETE_REQUESTED" : action + "_REQUESTED";
        handle.setStatus(normalizedStatus);
        handle.setUpdatedAt(Instant.now());
        resourceHandleRepository.save(handle);
        platformAuditService.record(
            "DEPLOYMENT_PROVIDER_RESOURCE_" + action,
            "DEPLOYMENT_PROVIDER_RESOURCE_HANDLE",
            handle.getId(),
            Map.of(
                "deploymentId", handle.getDeploymentId(),
                "providerType", handle.getProviderType().name(),
                "reason", reason(request)
            )
        );
        return result;
    }

    private DeploymentProviderResourceHandleEntity requireHandle(String handleId) {
        if (!StringUtils.hasText(handleId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment provider resource handle id is required.");
        }
        return resourceHandleRepository.findById(handleId.trim())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment provider resource handle not found: " + handleId
            ));
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read provider resource handle metadata.", ex);
        }
    }

    private String reason(DeploymentProviderResourceActionRequest request) {
        return request == null || !StringUtils.hasText(request.reason()) ? "operator_request" : request.reason().trim();
    }

    @FunctionalInterface
    private interface ResourceActionInvoker {
        DeploymentProviderResourceActionSummary invoke(DeploymentProviderResourceHandleEntity handle);
    }
}
