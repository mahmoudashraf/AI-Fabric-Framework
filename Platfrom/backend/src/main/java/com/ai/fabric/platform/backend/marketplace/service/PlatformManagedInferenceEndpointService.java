package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformManagedInferenceEndpointService {

    private final PlatformManagedInferenceEndpointRepository repository;

    public PlatformManagedInferenceEndpointService(PlatformManagedInferenceEndpointRepository repository) {
        this.repository = repository;
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
        return endpoint;
    }
}
