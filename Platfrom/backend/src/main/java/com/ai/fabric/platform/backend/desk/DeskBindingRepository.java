package com.ai.fabric.platform.backend.desk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DeskBindingRepository extends JpaRepository<DeskBindingEntity, String> {
    Optional<DeskBindingEntity> findByDeploymentIdAndTargetProfileId(String deploymentId, String targetProfileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from DeskBindingEntity binding where binding.deploymentId = :deploymentId and binding.targetProfileId = :targetProfileId")
    Optional<DeskBindingEntity> findForUpdate(@Param("deploymentId") String deploymentId, @Param("targetProfileId") String targetProfileId);
}
