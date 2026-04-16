package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceEndpointSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformManagedInferenceServiceService {

    private final PlatformManagedInferenceServiceRepository serviceRepository;
    private final PlatformManagedInferenceEndpointRepository endpointRepository;

    public PlatformManagedInferenceServiceService(PlatformManagedInferenceServiceRepository serviceRepository,
                                                  PlatformManagedInferenceEndpointRepository endpointRepository) {
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
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
}
