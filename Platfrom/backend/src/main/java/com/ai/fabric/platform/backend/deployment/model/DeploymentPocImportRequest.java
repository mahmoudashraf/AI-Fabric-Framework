package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocImportRequest(
    String datasetLabel,
    String vectorSpace,
    List<DeploymentPocImportRecordRequest> records
) {
}
