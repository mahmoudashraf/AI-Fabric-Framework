package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentPromotionCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentPromotionCommandRepository extends JpaRepository<DeploymentPromotionCommandEntity, String> {
    Optional<DeploymentPromotionCommandEntity> findByConsumerEntityIdAndOperationKey(String consumerEntityId, String operationKey);
}
