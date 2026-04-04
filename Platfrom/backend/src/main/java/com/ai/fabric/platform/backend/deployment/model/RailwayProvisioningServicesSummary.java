package com.ai.fabric.platform.backend.deployment.model;

public record RailwayProvisioningServicesSummary(
    RailwayServicePlanSummary runtime,
    RailwayServicePlanSummary restConnector,
    RailwayServicePlanSummary vectorizationRunner
) {
    public RailwayProvisioningServicesSummary(RailwayServicePlanSummary runtime,
                                              RailwayServicePlanSummary restConnector) {
        this(runtime, restConnector, null);
    }
}
