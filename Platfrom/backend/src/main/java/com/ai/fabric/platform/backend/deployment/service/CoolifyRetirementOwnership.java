package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;

/** Internal retirement authorization; never a customer-supplied ownership assertion. */
public interface CoolifyRetirementOwnership {
    void requireRetirementOwnership(DeploymentProviderResourceHandleEntity handle);
    void beginRetirementDeletion(DeploymentProviderResourceHandleEntity handle);
    ManagedResourceOwnershipService.DeletionPreparation preflightRetirementDeletion(DeploymentProviderResourceHandleEntity handle);
    boolean prepareRetirementDeletion(DeploymentProviderResourceHandleEntity handle,
        ManagedResourceOwnershipService.DeletionPreparation preparation, Runnable guard);
    void finishRetirementDeletion(DeploymentProviderResourceHandleEntity handle);
}
