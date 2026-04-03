package com.ai.fabric.platform.backend.tenant.repository;

import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformCustomerRepository extends JpaRepository<PlatformCustomerEntity, String> {

    List<PlatformCustomerEntity> findAllByOrderByPlatformManagedDescCreatedAtAsc();

    Optional<PlatformCustomerEntity> findBySlugIgnoreCase(String slug);
}
