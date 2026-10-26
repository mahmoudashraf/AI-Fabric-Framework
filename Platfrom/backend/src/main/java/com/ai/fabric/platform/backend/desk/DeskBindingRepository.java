package com.ai.fabric.platform.backend.desk;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeskBindingRepository extends JpaRepository<DeskBindingEntity, String> {
    Optional<DeskBindingEntity> findByDeploymentIdAndTargetProfileId(String deploymentId, String targetProfileId);
}
