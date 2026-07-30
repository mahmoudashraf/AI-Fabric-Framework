package com.ai.fabric.platform.backend.deployment.entityconfig;

public record EntityConfigValidationContext(
    boolean piiCapabilityAvailable,
    boolean sharedVectorStorage
) {

    public static EntityConfigValidationContext standard() {
        return new EntityConfigValidationContext(false, false);
    }
}
