package com.ai.fabric.platform.backend.vectorization.repository;

import com.ai.fabric.platform.backend.vectorization.entity.VectorizationCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VectorizationCheckpointRepository extends JpaRepository<VectorizationCheckpointEntity, String> {

    List<VectorizationCheckpointEntity> findByRunIdOrderByUpdatedAtDesc(String runId);
}
