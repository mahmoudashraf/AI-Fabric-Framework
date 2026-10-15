package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Resolves only reviewed, server-owned dependency bindings for release planning. */
@Service
public class ManagedDependencyRuntimeEnvironmentResolver {
    private final CustomerOfferDeploymentRepository offers;
    private final ManagedDependencyLifecycleService lifecycle;

    public ManagedDependencyRuntimeEnvironmentResolver(CustomerOfferDeploymentRepository offers,
            ManagedDependencyLifecycleService lifecycle) {
        this.offers = offers;
        this.lifecycle = lifecycle;
    }

    public ManagedDependencyLifecycleService.RuntimeEnvironmentProjection resolve(String deploymentId,
            String targetProfileId, String versionId, String bindingFingerprint) {
        if (!offers.existsById(deploymentId)) {
            return new ManagedDependencyLifecycleService.RuntimeEnvironmentProjection(null, null);
        }
        if (bindingFingerprint == null || !bindingFingerprint.matches("[a-f0-9]{64}")) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_RELEASE_BINDING_REQUIRED");
        }
        return lifecycle.runtimeEnvironment(deploymentId, targetProfileId, versionId, bindingFingerprint);
    }
}
