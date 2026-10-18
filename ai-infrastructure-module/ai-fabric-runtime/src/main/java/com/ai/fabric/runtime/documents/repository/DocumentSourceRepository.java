package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentSourceRepository extends JpaRepository<DocumentSourceEntity, String> {
    Optional<DocumentSourceEntity> findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseId(
        String id, String tenantId, String customerId, String deploymentId, String targetProfileId, String releaseId);
    Optional<DocumentSourceEntity> findByLogicalSourceKey(String logicalSourceKey);
    List<DocumentSourceEntity> findByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseIdOrderByUpdatedAtDesc(
        String tenantId, String customerId, String deploymentId, String targetProfileId, String releaseId);
    long countByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseIdAndStatusNot(
        String tenantId, String customerId, String deploymentId, String targetProfileId, String releaseId, DocumentSourceEntity.Status status);

    @Query("select coalesce(sum(source.contentLength), 0) from DocumentSourceEntity source "
        + "where source.tenantId = :tenantId and source.deploymentId = :deploymentId "
        + "and source.customerId = :customerId and source.targetProfileId = :targetProfileId and source.releaseId = :releaseId "
        + "and source.status <> com.ai.fabric.runtime.documents.entity.DocumentSourceEntity.Status.DELETED")
    long sumContentLengthForBoundary(@Param("tenantId") String tenantId, @Param("customerId") String customerId,
        @Param("deploymentId") String deploymentId, @Param("targetProfileId") String targetProfileId, @Param("releaseId") String releaseId);
}
