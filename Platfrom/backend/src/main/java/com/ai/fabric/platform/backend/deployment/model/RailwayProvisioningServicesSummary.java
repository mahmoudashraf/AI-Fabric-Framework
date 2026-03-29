package com.ai.fabric.platform.backend.deployment.model;

public record RailwayProvisioningServicesSummary(
    RailwayServicePlanSummary runtime,
    RailwayServicePlanSummary restConnector
) {
}
