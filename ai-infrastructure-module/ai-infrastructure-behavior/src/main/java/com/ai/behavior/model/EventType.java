package com.ai.behavior.model;

/**
 * Simplified behavior event types aligned with the AI behavior solution.
 * <p>
 * Simple, high-signal interactions are tracked here. Complex transactional or operational
 * events must remain in their dedicated bounded contexts.
 */
public enum EventType {

    // Viewing & navigation
    VIEW,
    NAVIGATION,

    // Interaction
    CLICK,
    SEARCH,
    FILTER,

    // Conversion funnel
    ADD_TO_CART,
    REMOVE_FROM_CART,
    PURCHASE,
    WISHLIST,

    // Text-heavy events (eligible for embeddings)
    FEEDBACK,
    REVIEW,
    RATING,

    // Engagement
    SHARE,
    SAVE,

    // Fallback for domain-specific events (must provide metadata.customType)
    CUSTOM
}
