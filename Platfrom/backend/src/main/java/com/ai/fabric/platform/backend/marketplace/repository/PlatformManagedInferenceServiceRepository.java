package com.ai.fabric.platform.backend.marketplace.repository;

import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformManagedInferenceServiceRepository
    extends JpaRepository<PlatformManagedInferenceServiceEntity, String> {

    Optional<PlatformManagedInferenceServiceEntity> findByServiceRefIgnoreCase(String serviceRef);

    List<PlatformManagedInferenceServiceEntity> findAllByOrderByDisplayNameAsc();
}
