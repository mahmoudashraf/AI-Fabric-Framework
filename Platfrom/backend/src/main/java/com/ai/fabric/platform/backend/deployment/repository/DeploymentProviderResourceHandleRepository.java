package com.ai.fabric.platform.backend.deployment.repository;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentProviderResourceHandleRepository extends JpaRepository<DeploymentProviderResourceHandleEntity, String> {

    Optional<DeploymentProviderResourceHandleEntity> findTopByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    List<DeploymentProviderResourceHandleEntity> findByDeploymentIdOrderByUpdatedAtDesc(String deploymentId);

    List<DeploymentProviderResourceHandleEntity> findByReleaseIdOrderByUpdatedAtDesc(String releaseId);

    List<DeploymentProviderResourceHandleEntity> findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
        String deploymentId,
        String targetProfileId
    );

    List<DeploymentProviderResourceHandleEntity> findByTargetProfileIdOrderByUpdatedAtDesc(String targetProfileId);

    List<DeploymentProviderResourceHandleEntity> findByProviderTypeOrderByUpdatedAtDesc(DeploymentProviderType providerType);

    Optional<DeploymentProviderResourceHandleEntity> findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
        String deploymentId,
        String targetProfileId,
        String resourceKind
    );

    Optional<DeploymentProviderResourceHandleEntity> findFirstByProviderTypeAndProviderResourceUuid(
        DeploymentProviderType providerType,
        String providerResourceUuid
    );
}
