package com.ai.fabric.platform.backend.security.repository;

import com.ai.fabric.platform.backend.security.entity.CustomerStaffAccessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerStaffAccessRepository extends JpaRepository<CustomerStaffAccessEntity, String> {
    Optional<CustomerStaffAccessEntity> findByCustomerIdAndUserId(String customerId, String userId);
    Optional<CustomerStaffAccessEntity> findByCustomerIdAndId(String customerId, String id);
    List<CustomerStaffAccessEntity> findByCustomerIdOrderByGrantedAtAscIdAsc(String customerId);
    List<CustomerStaffAccessEntity> findByUserIdAndStatus(String userId, String status);
}
