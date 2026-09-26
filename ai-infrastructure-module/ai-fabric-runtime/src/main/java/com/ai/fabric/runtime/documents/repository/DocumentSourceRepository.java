package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentSourceRepository extends JpaRepository<DocumentSourceEntity, String> {
    Optional<DocumentSourceEntity> findByIdAndTenantIdAndDeploymentId(String id, String tenantId, String deploymentId);
    Optional<DocumentSourceEntity> findByLogicalSourceKey(String logicalSourceKey);
    List<DocumentSourceEntity> findByTenantIdAndDeploymentIdOrderByUpdatedAtDesc(String tenantId, String deploymentId);
    long countByTenantIdAndDeploymentIdAndStatusNot(String tenantId, String deploymentId, DocumentSourceEntity.Status status);

    @Query("select coalesce(sum(source.contentLength), 0) from DocumentSourceEntity source "
        + "where source.tenantId = :tenantId and source.deploymentId = :deploymentId "
        + "and source.status <> com.ai.fabric.runtime.documents.entity.DocumentSourceEntity.Status.DELETED")
    long sumContentLengthForBoundary(@Param("tenantId") String tenantId, @Param("deploymentId") String deploymentId);
}
