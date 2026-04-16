package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceEndpointSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformManagedInferenceServiceService {

    private final PlatformManagedInferenceServiceRepository serviceRepository;
    private final PlatformManagedInferenceEndpointRepository endpointRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public PlatformManagedInferenceServiceService(PlatformManagedInferenceServiceRepository serviceRepository,
                                                  PlatformManagedInferenceEndpointRepository endpointRepository,
                                                  DeploymentRepository deploymentRepository,
                                                  DeploymentDraftRepository deploymentDraftRepository,
                                                  DeploymentVersionRepository deploymentVersionRepository,
                                                  PlatformSecretService platformSecretService,
                                                  ObjectMapper objectMapper) {
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentDraftRepository = deploymentDraftRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public List<PlatformManagedInferenceServiceSummary> listServices() {
        return serviceRepository.findAllByOrderByDisplayNameAsc().stream()
            .map(this::toSummary)
            .toList();
    }

    public PlatformManagedInferenceServiceSummary getService(String serviceRef) {
        return toSummary(requireService(serviceRef));
    }

    public PlatformManagedInferenceServiceEntity requireService(String serviceRef) {
        return serviceRepository.findByServiceRefIgnoreCase(serviceRef)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Managed inference service not found: " + serviceRef
            ));
    }

    public PlatformManagedInferenceServiceEntity requireActiveService(String serviceRef) {
        PlatformManagedInferenceServiceEntity service = requireService(serviceRef);
        if (!"ACTIVE".equalsIgnoreCase(service.getStatus())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed inference service is not active: " + serviceRef
            );
        }
        return service;
    }

    public PlatformManagedInferenceEndpointEntity requireActiveEndpointForService(String serviceRef,
                                                                                  String purpose,
                                                                                  String providerType) {
        PlatformManagedInferenceServiceEntity service = requireActiveService(serviceRef);
        PlatformManagedInferenceEndpointEntity endpoint = endpointRepository
            .findByServiceIdAndEndpointPurposeIgnoreCase(service.getId(), normalizePurpose(purpose))
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Managed inference service '" + serviceRef + "' does not expose an endpoint for purpose " + normalizePurpose(purpose) + "."
            ));
        if (!"ACTIVE".equalsIgnoreCase(endpoint.getStatus())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed inference endpoint is not active for service " + serviceRef + "."
            );
        }
        if (StringUtils.hasText(providerType)
            && StringUtils.hasText(endpoint.getProviderType())
            && !endpoint.getProviderType().trim().equalsIgnoreCase(providerType.trim())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed inference service '" + serviceRef + "' uses provider '" + endpoint.getProviderType()
                    + "' but the deployment requested '" + providerType + "'."
            );
        }
        return endpoint;
    }

    public PlatformManagedInferenceServiceSummary updateDesiredReplicas(String serviceRef, Integer desiredReplicas) {
        PlatformManagedInferenceServiceEntity service = requireService(serviceRef);
        int min = service.getMinReplicas() != null && service.getMinReplicas() > 0 ? service.getMinReplicas() : 1;
        int max = service.getMaxReplicas() != null && service.getMaxReplicas() > 0 ? service.getMaxReplicas() : Math.max(min, 1);
        if (desiredReplicas == null || desiredReplicas < min || desiredReplicas > max) {
            throw new ResponseStatusException(
                CONFLICT,
                "desiredReplicas must be between " + min + " and " + max + " for service " + serviceRef + "."
            );
        }
        service.setDesiredReplicas(desiredReplicas);
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        return toSummary(service);
    }

    private PlatformManagedInferenceServiceSummary toSummary(PlatformManagedInferenceServiceEntity entity) {
        List<PlatformManagedInferenceEndpointSummary> endpoints = endpointRepository
            .findAllByServiceIdOrderByProfileRefAsc(entity.getId())
            .stream()
            .map(this::toSummary)
            .toList();
        JsonNode details = readDetails(entity.getDetailsJson());
        DependencyStats dependencyStats = dependencyStats(entity, endpoints);
        return new PlatformManagedInferenceServiceSummary(
            entity.getId(),
            entity.getServiceRef(),
            entity.getDisplayName(),
            entity.getServiceKind(),
            entity.getDeploymentMode(),
            entity.getProviderType(),
            entity.getProtocolType(),
            entity.getModelId(),
            entity.getEnvironmentScope(),
            entity.getTierScope(),
            entity.getDeploymentId(),
            entity.getRailwayProjectId(),
            entity.getRailwayEnvironmentId(),
            entity.getRailwayServiceId(),
            entity.getDesiredReplicas(),
            entity.getActualReplicas(),
            entity.getMinReplicas(),
            entity.getMaxReplicas(),
            entity.getAutoscalingMode(),
            entity.getBaseUrl(),
            entity.getPrivateNetworkUrl(),
            entity.getHealthPath(),
            entity.getSecretName(),
            entity.getStatus(),
            hasText(entity.getSecretName()) && platformSecretService.isSecretPresent(entity.getSecretName()),
            text(details, "lastDeploymentId", "deploymentId"),
            text(details, "lastReconciledAt", "reconciledAt"),
            text(details, "lastReconcileStatus"),
            text(details, "lastReconcileMessage", "statusMessage"),
            text(details, "lastHealthyAt"),
            text(details, "lastProbeAt"),
            text(details, "lastSuccessfulProbeAt"),
            text(details, "lastFailedProbeAt"),
            text(details, "lastProbeStatus"),
            text(details, "lastProbeMessage"),
            text(details, "driftStatus"),
            text(details, "driftMessage"),
            dependencyStats.dependentDeploymentsCount(),
            dependencyStats.dependentActiveDeploymentsCount(),
            endpoints
        );
    }

    private PlatformManagedInferenceEndpointSummary toSummary(PlatformManagedInferenceEndpointEntity entity) {
        return new PlatformManagedInferenceEndpointSummary(
            entity.getId(),
            entity.getProfileRef(),
            entity.getServiceId(),
            entity.getEndpointPurpose(),
            entity.getDisplayName(),
            entity.getProviderType(),
            entity.getProtocolType(),
            entity.getBaseUrl(),
            entity.getDeploymentName(),
            entity.getApiVersion(),
            entity.getSecretName(),
            entity.getStatus()
        );
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORCHESTRATION", "GENERATION", "EMBEDDING" -> normalized;
            default -> throw new ResponseStatusException(
                CONFLICT,
                "Unsupported inference endpoint purpose: " + purpose
            );
        };
    }

    private DependencyStats dependencyStats(PlatformManagedInferenceServiceEntity service,
                                            List<PlatformManagedInferenceEndpointSummary> endpoints) {
        Set<String> endpointRefs = new LinkedHashSet<>();
        for (PlatformManagedInferenceEndpointSummary endpoint : endpoints) {
            if (hasText(endpoint.profileRef())) {
                endpointRefs.add(endpoint.profileRef().trim());
            }
        }
        int dependentDeployments = 0;
        int activeDependents = 0;
        for (DeploymentEntity deployment : deploymentRepository.findAll()) {
            DeploymentDraftEntity draft = deployment.getActiveDraftId() == null
                ? null
                : deploymentDraftRepository.findById(deployment.getActiveDraftId()).orElse(null);
            DeploymentVersionEntity version = deployment.getActiveVersionId() == null
                ? null
                : deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
            boolean draftUses = providerConfigUsesService(draft == null ? null : draft.getProviderConfigJson(), service.getServiceRef(), endpointRefs);
            boolean versionUses = providerConfigUsesService(version == null ? null : version.getProviderConfigJson(), service.getServiceRef(), endpointRefs);
            if (draftUses || versionUses) {
                dependentDeployments++;
                if (isRuntimeActive(deployment)) {
                    activeDependents++;
                }
            }
        }
        return new DependencyStats(dependentDeployments, activeDependents);
    }

    private boolean providerConfigUsesService(String providerConfigJson,
                                              String serviceRef,
                                              Set<String> endpointRefs) {
        if (!hasText(providerConfigJson)) {
            return false;
        }
        try {
            JsonNode providerConfig = objectMapper.readTree(providerConfigJson);
            if (matchesValue(providerConfig, "orchestrationManagedServiceRef", serviceRef)
                || matchesValue(providerConfig, "generationManagedServiceRef", serviceRef)
                || matchesValue(providerConfig, "embeddingManagedServiceRef", serviceRef)
                || matchesValue(providerConfig.path("marketplaceInference"), "managedServiceRefs", serviceRef)) {
                return true;
            }
            return endpointRefs.stream().anyMatch(ref ->
                matchesValue(providerConfig, "orchestrationEndpointProfile", ref)
                    || matchesValue(providerConfig, "generationEndpointProfile", ref)
                    || matchesValue(providerConfig, "embeddingEndpointProfile", ref)
                    || matchesValue(providerConfig.path("marketplaceInference"), "endpointProfileRefs", ref)
            );
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean matchesValue(JsonNode node, String fieldName, String expected) {
        if (node == null || node.isMissingNode() || node.isNull() || !hasText(expected)) {
            return false;
        }
        JsonNode field = node.path(fieldName);
        if (field.isTextual()) {
            return expected.equalsIgnoreCase(field.asText(""));
        }
        if (field.isArray()) {
            for (JsonNode item : field) {
                if (item.isTextual() && expected.equalsIgnoreCase(item.asText(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRuntimeActive(DeploymentEntity deployment) {
        if (deployment == null) {
            return false;
        }
        String status = deployment.getStatus();
        return hasText(status)
            && ("ACTIVE".equalsIgnoreCase(status)
            || "APPLIED_VERIFIED".equalsIgnoreCase(status)
            || "VERSION_PUBLISHED".equalsIgnoreCase(status)
            || "PROVISIONING".equalsIgnoreCase(status));
    }

    private JsonNode readDetails(String detailsJson) {
        try {
            return hasText(detailsJson) ? objectMapper.readTree(detailsJson) : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DependencyStats(int dependentDeploymentsCount, int dependentActiveDeploymentsCount) {
    }
}
