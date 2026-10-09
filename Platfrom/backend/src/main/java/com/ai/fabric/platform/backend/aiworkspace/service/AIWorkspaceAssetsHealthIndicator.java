package com.ai.fabric.platform.backend.aiworkspace.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("aiWorkspaceAssets")
public class AIWorkspaceAssetsHealthIndicator implements HealthIndicator {

    private final AIWorkspaceAssetCatalogService assets;
    private final AIWorkspaceObservability observability;

    public AIWorkspaceAssetsHealthIndicator(AIWorkspaceAssetCatalogService assets,
                                            AIWorkspaceObservability observability) {
        this.assets = assets;
        this.observability = observability;
    }

    @Override
    public Health health() {
        var health = assets.health();
        observability.recordAssetCatalog(health.ready(), health.status());
        return health.ready()
            ? Health.up().withDetail("status", health.status()).withDetail("workspaceVersion", health.workspaceVersion()).build()
            : Health.down().withDetail("status", health.status()).build();
    }
}
