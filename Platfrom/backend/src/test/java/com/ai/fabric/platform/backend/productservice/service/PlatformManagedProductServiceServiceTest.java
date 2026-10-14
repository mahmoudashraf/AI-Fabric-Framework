package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.CreatePlatformManagedProductServiceRequest;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformManagedProductServiceServiceTest {

    @Test
    void standaloneWorkspaceUsesReviewedManagedServiceDefaultsWithoutCreatingASecret() {
        PlatformManagedProductServiceRepository repository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository stores = mock(ShopifyStoreConnectionRepository.class);
        PlatformSecretService secrets = mock(PlatformSecretService.class);
        when(repository.findByServiceRefIgnoreCase("loomai-engage-web")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformManagedProductServiceService service = new PlatformManagedProductServiceService(
            repository, stores, secrets, new ObjectMapper());

        service.createService(new CreatePlatformManagedProductServiceRequest(
            "loomai-engage-web",
            "LoomAI Engage shared workspace",
            "LOOMAI_ENGAGE",
            PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE,
            "SHARED_PLATFORM_SERVICE",
            "MULTI_TENANT_SHARED_FRONTEND",
            "staging",
            null,
            1,
            1,
            2,
            null,
            null,
            null,
            null,
            null,
            "dtp-coolify-staging",
            null
        ));

        ArgumentCaptor<PlatformManagedProductServiceEntity> captor =
            ArgumentCaptor.forClass(PlatformManagedProductServiceEntity.class);
        verify(repository).save(captor.capture());
        PlatformManagedProductServiceEntity saved = captor.getValue();
        assertThat(saved.getHealthPath()).isEqualTo("/health");
        assertThat(saved.getServiceRoot()).isEqualTo("workspace-apps/standalone-ai-workspace");
        assertThat(saved.getDockerfilePath()).isEqualTo("workspace-apps/standalone-ai-workspace/Dockerfile");
        assertThat(saved.getSecretName()).isNull();
        assertThat(saved.getDetailsJson()).contains("dtp-coolify-staging");
    }
}
