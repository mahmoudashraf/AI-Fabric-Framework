package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.CreatePlatformManagedProductServiceRequest;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformManagedProductServiceService {

    private final PlatformManagedProductServiceRepository repository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public PlatformManagedProductServiceService(PlatformManagedProductServiceRepository repository,
                                                ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                                PlatformSecretService platformSecretService,
                                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public List<PlatformManagedProductServiceSummary> listServices() {
        return repository.findAllByOrderByDisplayNameAsc().stream()
            .map(this::toSummary)
            .toList();
    }

    public PlatformManagedProductServiceSummary getService(String serviceRef) {
        return toSummary(requireService(serviceRef));
    }

    public PlatformManagedProductServiceEntity requireService(String serviceRef) {
        return repository.findByServiceRefIgnoreCase(serviceRef)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Managed product service not found: " + serviceRef));
    }

    public PlatformManagedProductServiceEntity requireServiceById(String serviceId) {
        return repository.findById(serviceId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Managed product service not found: " + serviceId));
    }

    public PlatformManagedProductServiceSummary createService(CreatePlatformManagedProductServiceRequest request) {
        String normalizedServiceRef = normalizeServiceRef(request.serviceRef());
        repository.findByServiceRefIgnoreCase(normalizedServiceRef).ifPresent(existing -> {
            throw new ResponseStatusException(CONFLICT, "Managed product service already exists: " + normalizedServiceRef);
        });

        int minReplicas = request.minReplicas() != null && request.minReplicas() > 0 ? request.minReplicas() : 1;
        int maxReplicas = request.maxReplicas() != null && request.maxReplicas() > 0 ? request.maxReplicas() : Math.max(minReplicas, 3);
        int desiredReplicas = request.desiredReplicas() != null && request.desiredReplicas() > 0 ? request.desiredReplicas() : minReplicas;
        if (desiredReplicas < minReplicas || desiredReplicas > maxReplicas) {
            throw new ResponseStatusException(CONFLICT, "desiredReplicas must be between minReplicas and maxReplicas.");
        }

        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(generateId("psv"));
        entity.setServiceRef(normalizedServiceRef);
        entity.setDisplayName(trimmed(request.displayName()));
        entity.setProductFamily(normalizeEnum(request.productFamily()));
        entity.setServiceKind(normalizeEnum(request.serviceKind()));
        entity.setDeploymentMode(normalizeEnum(request.deploymentMode()));
        entity.setTenantMode(normalizeEnum(request.tenantMode()));
        entity.setEnvironmentScope(trimToNull(request.environmentScope()));
        entity.setDeploymentId(trimToNull(request.deploymentId()));
        entity.setDesiredReplicas(desiredReplicas);
        entity.setActualReplicas(0);
        entity.setMinReplicas(minReplicas);
        entity.setMaxReplicas(maxReplicas);
        entity.setBaseUrl(trimToNull(request.baseUrl()));
        entity.setHealthPath(trimToNull(request.healthPath()) == null ? "/actuator/health" : trimToNull(request.healthPath()));
        entity.setServiceRoot(trimToNull(request.serviceRoot()));
        entity.setDockerfilePath(trimToNull(request.dockerfilePath()));
        entity.setSecretName(trimToNull(request.secretName()));
        entity.setStatus(StringUtils.hasText(entity.getBaseUrl()) ? "ACTIVE" : "CREATED");
        entity.setDetailsJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return toSummary(entity);
    }

    public PlatformManagedProductServiceSummary updateDesiredReplicas(String serviceRef, Integer desiredReplicas) {
        PlatformManagedProductServiceEntity service = requireService(serviceRef);
        int min = service.getMinReplicas() != null && service.getMinReplicas() > 0 ? service.getMinReplicas() : 1;
        int max = service.getMaxReplicas() != null && service.getMaxReplicas() > 0 ? service.getMaxReplicas() : Math.max(min, 1);
        if (desiredReplicas == null || desiredReplicas < min || desiredReplicas > max) {
            throw new ResponseStatusException(CONFLICT, "desiredReplicas must be between " + min + " and " + max + " for service " + serviceRef + ".");
        }
        service.setDesiredReplicas(desiredReplicas);
        service.setUpdatedAt(Instant.now());
        repository.save(service);
        return toSummary(service);
    }

    PlatformManagedProductServiceSummary toSummary(PlatformManagedProductServiceEntity entity) {
        JsonNode details = readDetails(entity.getDetailsJson());
        int dependentStores = (int) shopifyStoreConnectionRepository.countByProductServiceId(entity.getId());
        int activeDependentStores = (int) shopifyStoreConnectionRepository.countByProductServiceIdAndInstallStatusIgnoreCase(entity.getId(), "INSTALLED");
        return new PlatformManagedProductServiceSummary(
            entity.getId(),
            entity.getServiceRef(),
            entity.getDisplayName(),
            entity.getProductFamily(),
            entity.getServiceKind(),
            entity.getDeploymentMode(),
            entity.getTenantMode(),
            entity.getEnvironmentScope(),
            entity.getDeploymentId(),
            entity.getRailwayProjectId(),
            entity.getRailwayEnvironmentId(),
            entity.getRailwayServiceId(),
            entity.getDesiredReplicas(),
            entity.getActualReplicas(),
            entity.getMinReplicas(),
            entity.getMaxReplicas(),
            entity.getBaseUrl(),
            entity.getPrivateNetworkUrl(),
            entity.getHealthPath(),
            entity.getServiceRoot(),
            entity.getDockerfilePath(),
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
            text(details, "lastVerifiedOperation"),
            text(details, "lastVerifiedAt"),
            text(details, "lastVerifiedStatus"),
            text(details, "lastVerifiedMessage"),
            text(details, "driftStatus"),
            text(details, "driftMessage"),
            dependentStores,
            activeDependentStores
        );
    }

    private JsonNode readDetails(String detailsJson) {
        try {
            return hasText(detailsJson) ? objectMapper.readTree(detailsJson) : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String... candidates) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode value = node.path(candidate);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = trimToNull(value.asText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private String normalizeServiceRef(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "serviceRef is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw new ResponseStatusException(CONFLICT, "serviceRef must be 3-128 characters and contain only lowercase letters, numbers, hyphen, or underscore.");
        }
        return normalized;
    }

    private String normalizeEnum(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "Enum-like values are required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimmed(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(CONFLICT, "Required value is blank.");
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
