package com.ai.fabric.runtime.documents.repository;

import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentSourceRepository extends JpaRepository<DocumentSourceEntity, String> {
    Optional<DocumentSourceEntity> findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileId(
        String id, String tenantId, String customerId, String deploymentId, String targetProfileId);
    List<DocumentSourceEntity> findByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndDatasetIdAndConnectorTypeAndConnectorBindingRefAndObjectLocatorDigest(
        String tenantId, String customerId, String deploymentId, String targetProfileId, String datasetId,
        String connectorType, String connectorBindingRef, String objectLocatorDigest);
    List<DocumentSourceEntity> findByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
        String tenantId, String customerId, String deploymentId, String targetProfileId);
    long countByTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndStatusNot(
        String tenantId, String customerId, String deploymentId, String targetProfileId, DocumentSourceEntity.Status status);

    @Query("select coalesce(sum(source.contentLength), 0) from DocumentSourceEntity source "
        + "where source.tenantId = :tenantId and source.deploymentId = :deploymentId "
        + "and source.customerId = :customerId and source.targetProfileId = :targetProfileId "
        + "and source.status <> com.ai.fabric.runtime.documents.entity.DocumentSourceEntity.Status.DELETED")
    long sumContentLengthForBoundary(@Param("tenantId") String tenantId, @Param("customerId") String customerId,
        @Param("deploymentId") String deploymentId, @Param("targetProfileId") String targetProfileId);
}
