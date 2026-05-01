package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentProviderRegistry {

    private final Map<DeploymentProviderType, DeploymentProvisioningProvider> providers;

    public DeploymentProviderRegistry(List<DeploymentProvisioningProvider> providers) {
        EnumMap<DeploymentProviderType, DeploymentProvisioningProvider> indexed = new EnumMap<>(DeploymentProviderType.class);
        for (DeploymentProvisioningProvider provider : providers) {
            DeploymentProvisioningProvider previous = indexed.putIfAbsent(provider.providerType(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate provisioning provider registered for " + provider.providerType());
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public DeploymentProvisioningProvider require(DeploymentProviderType providerType) {
        DeploymentProvisioningProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new IllegalStateException("No provisioning provider registered for provider type: " + providerType);
        }
        return provider;
    }
}
