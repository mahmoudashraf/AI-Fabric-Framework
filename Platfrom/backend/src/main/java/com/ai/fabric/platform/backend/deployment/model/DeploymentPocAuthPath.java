package com.ai.fabric.platform.backend.deployment.model;

public enum DeploymentPocAuthPath {
    PLATFORM_PRIVATE,
    PUBLIC_AUTHENTICATED,
    PUBLIC_ANONYMOUS;

    public static DeploymentPocAuthPath defaultValue(DeploymentPocAuthPath value) {
        return value == null ? PLATFORM_PRIVATE : value;
    }

    public boolean usesPublicRuntimeBearer() {
        return this == PUBLIC_AUTHENTICATED || this == PUBLIC_ANONYMOUS;
    }
}
