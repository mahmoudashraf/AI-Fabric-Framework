package com.ai.behavior.model;

/**
 * Processing lifecycle for queued behavior events.
 */
public enum BehaviorEventProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXPIRED
}
