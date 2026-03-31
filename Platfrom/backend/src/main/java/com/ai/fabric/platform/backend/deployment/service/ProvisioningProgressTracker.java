package com.ai.fabric.platform.backend.deployment.service;

public interface ProvisioningProgressTracker {

    void stepStarted(String key, String description);

    void stepCompleted(String key, String description);

    void stepFailed(String key, String description, String errorMessage);

    default void mergeDetails(String detailsJson) {
    }

    static ProvisioningProgressTracker noop() {
        return new ProvisioningProgressTracker() {
            @Override
            public void stepStarted(String key, String description) {
            }

            @Override
            public void stepCompleted(String key, String description) {
            }

            @Override
            public void stepFailed(String key, String description, String errorMessage) {
            }
        };
    }
}
