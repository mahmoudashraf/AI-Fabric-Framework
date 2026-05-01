package com.ai.fabric.platform.backend.thinker.repository;

import com.ai.fabric.platform.backend.thinker.entity.ResolverIntentProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResolverIntentProposalRepository extends JpaRepository<ResolverIntentProposalEntity, String> {
    List<ResolverIntentProposalEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<ResolverIntentProposalEntity> findTop200ByOrderByCreatedAtDesc();
}
