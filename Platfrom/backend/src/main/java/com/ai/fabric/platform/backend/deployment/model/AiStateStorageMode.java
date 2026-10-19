package com.ai.fabric.platform.backend.deployment.model;

/** Hosting choice for durable AI state, never customer business systems. */
public enum AiStateStorageMode {
    MANAGED_POSTGRES,
    CUSTOMER_POSTGRES
}
