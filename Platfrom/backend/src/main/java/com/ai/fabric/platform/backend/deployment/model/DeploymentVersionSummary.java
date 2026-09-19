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
    String aiFabricFrameworkVersion,
    String behaviorType,
    String behaviorContractVersion,
    boolean sourceCapabilityManifestRequired
) {

    public DeploymentVersionSummary(String id,
                                    String deploymentId,
                                    String sourceDraftId,
                                    String versionLabel,
                                    String status,
                                    String configHash,
                                    boolean reindexRequired,
                                    Instant publishedAt,
                                    String entityConfigContractVersion,
                                    String aiFabricFrameworkVersion,
                                    String behaviorType,
                                    String behaviorContractVersion) {
        this(
            id,
            deploymentId,
            sourceDraftId,
            versionLabel,
            status,
            configHash,
            reindexRequired,
            publishedAt,
            entityConfigContractVersion,
            aiFabricFrameworkVersion,
            behaviorType,
            behaviorContractVersion,
            false
        );
    }

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
            null,
            null,
            null,
            false
        );
    }
}
