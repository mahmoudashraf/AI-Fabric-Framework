package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ResolverExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResolverExecutionRepository extends JpaRepository<ResolverExecutionEntity, String> {
    Optional<ResolverExecutionEntity> findByIdempotencyKey(String idempotencyKey);
    List<ResolverExecutionEntity> findTop200ByOrderByCreatedAtDesc();
    List<ResolverExecutionEntity> findByProposalIdOrderByCreatedAtDesc(String proposalId);
}
