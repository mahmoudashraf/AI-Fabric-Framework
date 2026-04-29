package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ResolverPolicyDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResolverPolicyDecisionRepository extends JpaRepository<ResolverPolicyDecisionEntity, String> {
    Optional<ResolverPolicyDecisionEntity> findTopByProposalIdOrderByDecidedAtDesc(String proposalId);
    List<ResolverPolicyDecisionEntity> findTop200ByOrderByDecidedAtDesc();
}
