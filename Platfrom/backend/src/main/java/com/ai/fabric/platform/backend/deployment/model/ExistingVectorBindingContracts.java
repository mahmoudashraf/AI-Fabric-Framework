package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

/** Staff-reviewed connection to an existing cluster; this contract never provisions provider resources. */
public final class ExistingVectorBindingContracts {
    private ExistingVectorBindingContracts() { }

    public record Request(String versionId, String clusterId, String projectId, String regionId,
                          String expectedDeploymentOption, String databaseName, String username,
                          String password, String approvalReference) {
        @Override public String toString() { return "ExistingVectorBindingRequest[credentials redacted]"; }
    }

    public record Summary(String bindingId, String deploymentId, String targetProfileId, String versionId,
                          String clusterId, String deploymentOption, String endpointHost, String databaseName,
                          String collectionPrefix, String lifecycle, String fingerprint, Instant checkedAt) { }
}
