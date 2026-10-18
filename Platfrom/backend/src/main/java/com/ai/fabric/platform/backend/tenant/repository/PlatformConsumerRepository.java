package com.ai.fabric.platform.backend.tenant.repository;

import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlatformConsumerRepository extends JpaRepository<PlatformConsumerEntity, String> {

    List<PlatformConsumerEntity> findByCustomerIdOrderByCreatedAtAsc(String customerId);

    List<PlatformConsumerEntity> findByCustomerIdInOrderByCreatedAtAsc(Collection<String> customerIds);

    Optional<PlatformConsumerEntity> findByCustomerIdAndConsumerIdIgnoreCase(String customerId, String consumerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PlatformConsumerEntity c where c.customerId = :customerId and lower(c.consumerId) = lower(:consumerId)")
    Optional<PlatformConsumerEntity> findBindingForUpdate(@Param("customerId") String customerId, @Param("consumerId") String consumerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PlatformConsumerEntity c where c.id in :ids order by c.id")
    List<PlatformConsumerEntity> findAllByIdForUpdate(@Param("ids") Collection<String> ids);

    Optional<PlatformConsumerEntity> findByConsumerIdIgnoreCase(String consumerId);

    List<PlatformConsumerEntity> findByBoundDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    List<PlatformConsumerEntity> findByBoundReleaseIdOrderByUpdatedAtDesc(String releaseId);

    long deleteByCustomerIdAndConsumerIdIgnoreCase(String customerId, String consumerId);
}
