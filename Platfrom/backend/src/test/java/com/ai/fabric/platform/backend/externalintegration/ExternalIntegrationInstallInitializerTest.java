package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationInstallInitializerTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void configurationOnlyBootstrapEnablesReviewedAliasesThroughTheNormalInstaller() throws Exception {
        var f = new ExternalIntegrationFixture();
        var composition = new ExternalIntegrationCompositionService(f.associations, mock(CustomerOfferRepository.class),
            f.policy, f.compiler, f.codec, f.mapper);
        composition = spy(composition);
        doReturn(List.of(f.profile)).when(composition).profiles(f.deployment);
        var installs = mock(DeploymentMarketplacePluginInstallRepository.class);
        var installation = mock(DeploymentMarketplaceInstallService.class);
        var install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("install"); install.setDeploymentId(f.deployment.getId()); install.setPluginId(f.profile.pluginId());
        install.setPluginVersionId(f.profile.pluginVersionId()); install.setStatus("DISABLED");
        install.setConfigJson("{}"); install.setSecretRefsJson("{}");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc(f.deployment.getId())).thenReturn(List.of(install));
        var initializer = new ExternalIntegrationInstallInitializer(composition, installs, installation, f.mapper);
        initializer.initialize(f.deployment);
        verify(installation).updateInstallForTrustedCallerWithoutDraftSync(eq(f.deployment), eq("install"), argThat(request ->
            "ENABLED".equals(request.status()) && request.pluginVersion() == null
                && request.config().path("endpointUrl").asText().equals("${" + ExternalIntegrationCompilerResolver.alias(f.profile, "endpointUrl") + "}")
                && request.secretRefs().path("apiKey").asText().equals(ExternalIntegrationCompilerResolver.alias(f.profile, "apiKey"))));
        assertThat(install.getConfigJson()).isEqualTo("{}");
        assertThat(install.getStatus()).isEqualTo("DISABLED");
        verifyNoInteractions(f.vault, f.store, f.authority);
    }

    @Test void absentOrIncompatibleInstallCannotBeEnabledByTheOffer() throws Exception {
        var f = new ExternalIntegrationFixture();
        var composition = mock(ExternalIntegrationCompositionService.class);
        when(composition.profiles(f.deployment)).thenReturn(List.of(f.profile));
        var installs = mock(DeploymentMarketplacePluginInstallRepository.class);
        var installation = mock(DeploymentMarketplaceInstallService.class);
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc(f.deployment.getId())).thenReturn(List.of());
        var initializer = new ExternalIntegrationInstallInitializer(composition, installs, installation, f.mapper);
        assertThatThrownBy(() -> initializer.initialize(f.deployment)).hasMessageContaining("COMPILER_PROFILE_INSTALL_REQUIRED");
        var disabled = new DeploymentMarketplacePluginInstallEntity(); disabled.setPluginId(f.profile.pluginId()); disabled.setStatus("BOOTSTRAPPED");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc(f.deployment.getId())).thenReturn(List.of(disabled));
        assertThatThrownBy(() -> initializer.initialize(f.deployment)).hasMessageContaining("COMPILER_PROFILE_INSTALL_STATE_INVALID");
        verifyNoInteractions(installation);
    }
}
