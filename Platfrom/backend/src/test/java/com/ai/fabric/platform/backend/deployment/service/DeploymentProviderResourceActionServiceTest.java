package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLifecycleRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class DeploymentProviderResourceActionServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_DOCUMENT_STORAGE_BINDING", "EXTERNAL_VECTOR_STORAGE_BINDING",
        "EXTERNAL_VECTOR_STORAGE_BINDING_V2", "external_vector_storage_binding"})
    void retainedAndUnknownKindsCannotEnterProviderActionsOrLifecycleMarking(String resourceKind) {
        var handles = mock(DeploymentProviderResourceHandleRepository.class);
        var providers = mock(DeploymentProviderRegistry.class);
        var audit = mock(PlatformAuditService.class);
        var handle = providerHandle("ACTIVE");
        handle.setResourceKind(resourceKind);
        when(handles.findById(handle.getId())).thenReturn(Optional.of(handle));
        when(handles.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));
        var service = new DeploymentProviderResourceActionService(handles,
            mock(DeploymentTargetProfileRepository.class), providers, audit, new ObjectMapper());

        assertThatThrownBy(() -> service.start(handle.getId(), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.stop(handle.getId(), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.restart(handle.getId(), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(handle.getId(), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.status(handle.getId())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.logs(handle.getId(), 10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.markLifecycleStatus(handle.getId(),
            new DeploymentProviderResourceLifecycleRequest("DELETED", "not owned")))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(service.listResources(null, "dep-123", null, true)).singleElement()
            .satisfies(summary -> assertThat(summary.status()).isEqualTo("ACTIVE"));
        assertThat(handle.getStatus()).isEqualTo("ACTIVE");
        verify(handles, never()).save(any());
        verifyNoInteractions(providers, audit);
    }

    @Test
    void listResourcesLeavesStoredStatusUntouchedWhenRefreshIsFalse() {
        DeploymentProviderResourceHandleRepository resourceHandleRepository =
            mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentProviderRegistry providerRegistry = mock(DeploymentProviderRegistry.class);
        DeploymentProviderResourceHandleEntity handle = providerHandle("exited:unhealthy");
        when(resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));

        DeploymentProviderResourceActionService service = new DeploymentProviderResourceActionService(
            resourceHandleRepository,
            mock(DeploymentTargetProfileRepository.class),
            providerRegistry,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );

        var summaries = service.listResources(null, "dep-123", null, false);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).status()).isEqualTo("exited:unhealthy");
        verify(providerRegistry, never()).require(any());
        verify(resourceHandleRepository, never()).save(any());
    }

    @Test
    void listResourcesRefreshesProviderStatusWhenRequested() {
        DeploymentProviderResourceHandleRepository resourceHandleRepository =
            mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentProviderRegistry providerRegistry = mock(DeploymentProviderRegistry.class);
        DeploymentProvisioningProvider provider = mock(DeploymentProvisioningProvider.class);
        DeploymentProviderResourceHandleEntity handle = providerHandle("exited:unhealthy");
        when(resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));
        when(providerRegistry.require(DeploymentProviderType.COOLIFY)).thenReturn(provider);
        when(provider.status(handle)).thenReturn(new DeploymentProviderResourceStatusSummary(
            handle.getId(),
            DeploymentProviderType.COOLIFY,
            handle.getProviderResourceUuid(),
            "ACTIVE",
            "running:healthy",
            "runtime.example.test",
            new ObjectMapper().createObjectNode(),
            Instant.parse("2026-03-31T00:00:01Z")
        ));
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentProviderResourceActionService service = new DeploymentProviderResourceActionService(
            resourceHandleRepository,
            mock(DeploymentTargetProfileRepository.class),
            providerRegistry,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );

        var summaries = service.listResources(null, "dep-123", null, true);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).status()).isEqualTo("ACTIVE");
        assertThat(summaries.get(0).lastObservedStatus()).isEqualTo("running:healthy");
        assertThat(summaries.get(0).fqdn()).isEqualTo("runtime.example.test");
        verify(resourceHandleRepository).save(handle);
    }

    private static DeploymentProviderResourceHandleEntity providerHandle(String status) {
        Instant now = Instant.parse("2026-03-31T00:00:00Z");
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("dpr-123");
        handle.setDeploymentId("dep-123");
        handle.setReleaseId("rel-123");
        handle.setTargetProfileId("dtp-coolify");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind("APPLICATION");
        handle.setProviderResourceUuid("coolify-app-123");
        handle.setStatus(status);
        handle.setLastObservedStatus(status);
        handle.setLastObservedAt(now);
        handle.setMetadataJson("{}");
        handle.setCreatedAt(now);
        handle.setUpdatedAt(now);
        return handle;
    }
}
