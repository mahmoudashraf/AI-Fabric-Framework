package com.ai.infrastructure.access.policy;

import java.util.Map;

/**
 * Infrastructure hook for entity-level access control decisions.
 * Implementations live in customer applications and are injected optionally.
 */
@FunctionalInterface
public interface EntityAccessPolicy {

    /**
     * Determine whether a user can access the supplied entity.
     *
     * @param userId identifier of the user making the request
     * @param entity immutable view of the entity metadata for the access decision
     * @return {@code true} when access should be granted, {@code false} otherwise
     */
    boolean canUserAccessEntity(String userId, Map<String, Object> entity);

    /**
     * Optional callback invoked when access is denied. Implementations can override to emit
     * custom audit records or telemetry. Default implementation is a no-op.
     *
     * @param userId identifier of the user that was denied access
     * @param entity entity metadata that triggered the denial
     * @param reason machine readable reason (infrastructure supplied)
     */
    default void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
        // intentionally left blank - customers can override
    }
}
