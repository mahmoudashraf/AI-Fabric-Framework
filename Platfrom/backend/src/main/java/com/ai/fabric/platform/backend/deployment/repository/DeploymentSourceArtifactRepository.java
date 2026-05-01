package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentSourceArtifactRepository extends JpaRepository<DeploymentSourceArtifactEntity, String> {

    List<DeploymentSourceArtifactEntity> findByServiceNameOrderByCreatedAtDesc(String serviceName);

    Optional<DeploymentSourceArtifactEntity> findFirstByServiceNameAndPromotionChannelOrderByPromotedAtDesc(
        String serviceName,
        String promotionChannel
    );
}
