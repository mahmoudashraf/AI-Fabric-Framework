package com.ai.fabric.platform.backend.experiencekit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CustomerExperienceKitRepository extends JpaRepository<CustomerExperienceKitEntity, String> {
    List<CustomerExperienceKitEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    Optional<CustomerExperienceKitEntity> findByCustomerIdAndId(String customerId, String id);
    Optional<CustomerExperienceKitEntity> findByCustomerIdAndOperationKey(String customerId, String operationKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from CustomerExperienceKitEntity k where k.customerId=:customerId and k.id=:id")
    Optional<CustomerExperienceKitEntity> findOwnedForUpdate(String customerId, String id);
}
