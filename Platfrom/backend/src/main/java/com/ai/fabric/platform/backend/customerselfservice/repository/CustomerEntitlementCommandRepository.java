package com.ai.fabric.platform.backend.customerselfservice.repository;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerEntitlementCommandRepository extends JpaRepository<CustomerEntitlementCommandEntity, String> {
    Optional<CustomerEntitlementCommandEntity> findByEntitlementIdAndOperationKey(String entitlementId, String operationKey);
}
