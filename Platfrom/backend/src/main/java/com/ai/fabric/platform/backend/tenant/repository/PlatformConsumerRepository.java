package com.ai.fabric.platform.backend.tenant.repository;

import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlatformConsumerRepository extends JpaRepository<PlatformConsumerEntity, String> {

    List<PlatformConsumerEntity> findByCustomerIdOrderByCreatedAtAsc(String customerId);

    List<PlatformConsumerEntity> findByCustomerIdInOrderByCreatedAtAsc(Collection<String> customerIds);

    Optional<PlatformConsumerEntity> findByCustomerIdAndConsumerIdIgnoreCase(String customerId, String consumerId);

    Optional<PlatformConsumerEntity> findByConsumerIdIgnoreCase(String consumerId);

    List<PlatformConsumerEntity> findByBoundDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    List<PlatformConsumerEntity> findByBoundReleaseIdOrderByUpdatedAtDesc(String releaseId);

    long deleteByCustomerIdAndConsumerIdIgnoreCase(String customerId, String consumerId);
}
