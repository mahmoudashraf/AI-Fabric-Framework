package com.ai.fabric.platform.backend.customerselfservice.repository;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOfferDeploymentRepository extends JpaRepository<CustomerOfferDeploymentEntity, String> {
    List<CustomerOfferDeploymentEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
