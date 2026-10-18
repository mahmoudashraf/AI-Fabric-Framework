package com.ai.fabric.platform.backend.customerselfservice.model;

public final class CustomerStagingConsumerActivationContracts {
    private CustomerStagingConsumerActivationContracts() { }

    public record Request(String consumerId, String releaseId, long expectedBindingRevision,
                          long expectedEntitlementRevision, String operationKey) { }

    /** Omit releaseId for configuration only; an explicit release requires verified activation. */
    public record CreateRequest(String consumerId, String displayName, String description, String releaseId,
                                long expectedEntitlementRevision, String operationKey) { }
}
