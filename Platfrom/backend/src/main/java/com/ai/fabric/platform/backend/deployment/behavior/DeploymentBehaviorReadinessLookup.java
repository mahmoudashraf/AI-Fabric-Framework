package com.ai.fabric.platform.backend.deployment.behavior;

public interface DeploymentBehaviorReadinessLookup {

    String bestMaturity(String behaviorType, String fallbackMaturity);
}
