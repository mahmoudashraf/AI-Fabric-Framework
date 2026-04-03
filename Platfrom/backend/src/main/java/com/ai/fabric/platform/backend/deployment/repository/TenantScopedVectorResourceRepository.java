package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TenantScopedVectorResourceRepository extends JpaRepository<TenantScopedVectorResourceEntity, String> {

    List<TenantScopedVectorResourceEntity> findByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    List<TenantScopedVectorResourceEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<TenantScopedVectorResourceEntity> findByTenantIdInOrderByUpdatedAtDesc(Collection<String> tenantIds);

    Optional<TenantScopedVectorResourceEntity> findByTenantIdAndRegistryKeyIgnoreCase(String tenantId, String registryKey);
}
