package com.ai.infrastructure.behavior.repository;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {
    
    Optional<BehaviorInsights> findByUserId(UUID userId);
    
    void deleteByUserId(UUID userId);
}
