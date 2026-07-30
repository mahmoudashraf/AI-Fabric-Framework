package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentVersionSummary(
    String id,
    String deploymentId,
    String sourceDraftId,
    String versionLabel,
    String status,
    String configHash,
    boolean reindexRequired,
    Instant publishedAt,
    String entityConfigContractVersion,
    String aiFabricFrameworkVersion
) {

    public DeploymentVersionSummary(String id,
                                    String deploymentId,
                                    String sourceDraftId,
                                    String versionLabel,
                                    String status,
                                    String configHash,
                                    boolean reindexRequired,
                                    Instant publishedAt) {
        this(
            id,
            deploymentId,
            sourceDraftId,
            versionLabel,
            status,
            configHash,
            reindexRequired,
            publishedAt,
            null,
            null
        );
    }
}
