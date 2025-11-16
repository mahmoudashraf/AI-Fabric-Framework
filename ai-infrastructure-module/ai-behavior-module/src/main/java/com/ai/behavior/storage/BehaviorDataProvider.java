package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;

import java.util.List;
import java.util.UUID;

public interface BehaviorDataProvider {

    List<BehaviorEvent> query(BehaviorQuery query);

    default List<BehaviorEvent> getRecentEvents(UUID userId, int limit) {
        return query(BehaviorQuery.forUser(userId).limit(limit));
    }

    default List<BehaviorEvent> getEntityEvents(String entityType, String entityId) {
        return query(BehaviorQuery.forEntity(entityType, entityId));
    }

    String getProviderType();
}
