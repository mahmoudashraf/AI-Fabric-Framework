package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeveloperDeploymentProvisioningPolicyTest {
    @Test void queuedOrDirectProvisionCannotBypassCanonicalPolicyWithProductionDefault() {
        var targets = mock(DeploymentTargetProfileService.class);
        var registry = mock(DeploymentProviderRegistry.class);
        var compiler = mock(DeploymentConfigCompiler.class);
        var service = new DeploymentProvisioningService(targets, registry, compiler);
        var deployment = new DeploymentEntity(); deployment.restrictToStaging();
        var release = new DeploymentReleaseEntity();
        var version = new DeploymentVersionEntity();
        var target = new DeploymentTargetProfileEntity(); target.setEnvironmentName("production");
        when(targets.resolveForRelease(release)).thenReturn(target);
        assertThatThrownBy(() -> service.provision(deployment, version, release, null)).hasMessageContaining("DEPLOYMENT_STAGING_ONLY");
        verifyNoInteractions(registry);

        target.setEnvironmentName("staging"); target.setProviderType(DeploymentProviderType.COOLIFY);
        var provider = mock(DeploymentProvisioningProvider.class); when(registry.require(DeploymentProviderType.COOLIFY)).thenReturn(provider);
        assertThatCode(() -> service.provision(deployment, version, release, null)).doesNotThrowAnyException();
        verify(provider).provision(eq(deployment), eq(version), eq(release), any());
    }
}
