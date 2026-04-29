package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ResolverDryRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResolverDryRunRepository extends JpaRepository<ResolverDryRunEntity, String> {
    Optional<ResolverDryRunEntity> findTopByProposalIdOrderByCreatedAtDesc(String proposalId);
}
