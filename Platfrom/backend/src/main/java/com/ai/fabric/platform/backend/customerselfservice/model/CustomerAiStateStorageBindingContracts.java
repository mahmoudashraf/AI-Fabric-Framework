package com.ai.fabric.platform.backend.customerselfservice.model;

import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts;

public final class CustomerAiStateStorageBindingContracts {
    private CustomerAiStateStorageBindingContracts() { }

    public record Request(long expectedEntitlementRevision, String operationKey, String targetProfileId,
                          String bindingSlotId, DeploymentAiStateStorageContracts.Request connection) {
        @Override public String toString() { return "CustomerAiStateStorageBindingRequest[connection redacted]"; }
    }

    public record Result(String offerId, String deploymentId, String targetProfileId, String bindingSlotId,
                         DeploymentAiStateStorageContracts.Summary binding) { }
}
