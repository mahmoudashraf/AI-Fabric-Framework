package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformManagedInferenceEndpointRepository
    extends JpaRepository<PlatformManagedInferenceEndpointEntity, String> {

    Optional<PlatformManagedInferenceEndpointEntity> findByProfileRefIgnoreCase(String profileRef);
}
