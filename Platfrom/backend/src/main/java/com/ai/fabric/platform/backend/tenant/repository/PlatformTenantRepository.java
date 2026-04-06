package com.ai.fabric.platform.backend.tenant.repository;

import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformTenantRepository extends JpaRepository<PlatformTenantEntity, String> {

    List<PlatformTenantEntity> findAllByOrderByCreatedAtAsc();

    List<PlatformTenantEntity> findByCustomerIdOrderByCreatedAtAsc(String customerId);

    Optional<PlatformTenantEntity> findByCustomerIdAndSlugIgnoreCase(String customerId, String slug);
}
