package com.ai.infrastructure.indexing;

/**
 * Defines how and when an entity should be indexed.
 *
 * <p>The strategy can be configured at multiple levels:
 * <ul>
 *     <li>Entity-level defaults through {@link com.ai.infrastructure.annotation.AICapable}</li>
 *     <li>Operation-level overrides (create, update, delete)</li>
 *     <li>Method-level overrides through {@link com.ai.infrastructure.annotation.AIProcess}</li>
 * </ul>
 */
public enum IndexingStrategy {

    /**
     * Inherit strategy from the parent configuration level.
     */
    AUTO,

    /**
     * Run indexing synchronously in the same transaction as the caller.
     *
     * <p>Use sparingly for compliance-critical paths that require immediate
     * consistency.</p>
     */
    SYNC,

    /**
     * Enqueue for asynchronous near-real time processing.
     *
     * <p>Default option for most CRUD flows—provides fast response time while
     * keeping the indexing SLA within a few seconds.</p>
     */
    ASYNC,

    /**
     * Enqueue for scheduled batch processing.
     *
     * <p>Ideal for high-volume data where eventual consistency is acceptable.</p>
     */
    BATCH
}
