package com.ai.fabric.platform.backend.deployment.model;

public record RailwayProvisioningServicesSummary(
    RailwayServicePlanSummary runtime,
    RailwayServicePlanSummary restConnector,
    RailwayServicePlanSummary vectorizationRunner,
    RailwayServicePlanSummary embeddingWorker
) {
    public RailwayProvisioningServicesSummary(RailwayServicePlanSummary runtime,
                                              RailwayServicePlanSummary restConnector) {
        this(runtime, restConnector, null, null);
    }

    public RailwayProvisioningServicesSummary(RailwayServicePlanSummary runtime,
                                              RailwayServicePlanSummary restConnector,
                                              RailwayServicePlanSummary vectorizationRunner) {
        this(runtime, restConnector, vectorizationRunner, null);
    }
}
