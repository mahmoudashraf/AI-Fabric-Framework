package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformManagedInferenceEndpointService {

    private final PlatformManagedInferenceEndpointRepository repository;
    private final PlatformManagedInferenceServiceRepository serviceRepository;

    public PlatformManagedInferenceEndpointService(PlatformManagedInferenceEndpointRepository repository,
                                                   PlatformManagedInferenceServiceRepository serviceRepository) {
        this.repository = repository;
        this.serviceRepository = serviceRepository;
    }

    public PlatformManagedInferenceEndpointEntity requireActive(String profileRef) {
        PlatformManagedInferenceEndpointEntity endpoint = repository.findByProfileRefIgnoreCase(profileRef)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Managed inference endpoint not found: " + profileRef
            ));
        if (!"ACTIVE".equalsIgnoreCase(endpoint.getStatus())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed inference endpoint is not active: " + profileRef
            );
        }
        if (endpoint.getServiceId() != null && !endpoint.getServiceId().isBlank()) {
            serviceRepository.findById(endpoint.getServiceId()).ifPresent(service -> {
                if (!"ACTIVE".equalsIgnoreCase(service.getStatus())) {
                    throw new ResponseStatusException(
                        CONFLICT,
                        "Managed inference service is not active for endpoint: " + profileRef
                    );
                }
            });
        }
        return endpoint;
    }
}
