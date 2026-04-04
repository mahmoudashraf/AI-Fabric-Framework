package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationFailureBucketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VectorizationFailureBucketRepository extends JpaRepository<VectorizationFailureBucketEntity, String> {

    List<VectorizationFailureBucketEntity> findByRunIdOrderByUpdatedAtDesc(String runId);

    Optional<VectorizationFailureBucketEntity> findByRunIdAndEntityTypeAndErrorCodeAndSummary(String runId,
                                                                                               String entityType,
                                                                                               String errorCode,
                                                                                               String summary);
}
