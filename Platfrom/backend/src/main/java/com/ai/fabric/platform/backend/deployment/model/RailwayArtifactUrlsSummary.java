package com.ai.fabric.platform.backend.deployment.model;

public record RailwayArtifactUrlsSummary(
    String actions,
    String entities,
    String routing,
    String prompts,
    String knowledgeSources,
    String shell,
    String manifest
) {
    public RailwayArtifactUrlsSummary(String actions,
                                      String entities,
                                      String routing,
                                      String prompts,
                                      String manifest) {
        this(actions, entities, routing, prompts, null, null, manifest);
    }
}
