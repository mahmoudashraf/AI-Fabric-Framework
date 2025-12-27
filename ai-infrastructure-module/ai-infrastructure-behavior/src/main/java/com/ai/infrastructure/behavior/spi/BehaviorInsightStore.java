package com.ai.infrastructure.behavior.spi;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional user-implemented interface to fully customize behavior insight storage.
 * If provided, this becomes the single source of truth for read/write/delete operations.
 */
public interface BehaviorInsightStore {
    
    void save(BehaviorInsights insight);
    
    Optional<BehaviorInsights> findByUserId(UUID userId);
    
    void deleteByUserId(UUID userId);
}
