package com.ai.infrastructure.storage;

import lombok.RequiredArgsConstructor;

/**
 * Helper to expose the configured storage strategy in a normalized form.
 */
@RequiredArgsConstructor
public class StorageStrategyProvider {

    private final AIStorageProperties properties;

    public String getStrategy() {
        return properties.getStrategy().name();
    }

    public boolean isCustom() {
        return AIStorageProperties.Strategy.CUSTOM.equals(properties.getStrategy());
    }
}
