package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;

import java.util.List;
import java.util.UUID;

/**
 * Flexible read abstraction to support multiple storage backends.
 */
public interface BehaviorDataProvider {

    List<BehaviorSignal> query(BehaviorQuery query);

    default List<BehaviorSignal> getRecentEvents(UUID userId, int limit) {
        return query(BehaviorQuery.forUser(userId).limit(limit));
    }

    default List<BehaviorSignal> getEntityEvents(String entityType, String entityId, int limit) {
        return query(BehaviorQuery.builder()
            .entityType(entityType)
            .entityId(entityId)
            .limit(limit)
            .build());
    }

    String getProviderType();
}
