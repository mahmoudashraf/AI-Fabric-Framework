package com.ai.fabric.platform.backend.productservice.repository;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformManagedProductServiceRepository extends JpaRepository<PlatformManagedProductServiceEntity, String> {

    List<PlatformManagedProductServiceEntity> findAllByOrderByDisplayNameAsc();

    Optional<PlatformManagedProductServiceEntity> findByServiceRefIgnoreCase(String serviceRef);
}

