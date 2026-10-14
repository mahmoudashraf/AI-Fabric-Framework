package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIWorkspaceDomainServiceTest {

    private final AIWorkspaceDomainRepository repository = mock(AIWorkspaceDomainRepository.class);
    private final AIWorkspaceDomainVerifier verifier = mock(AIWorkspaceDomainVerifier.class);
    private final AIWorkspaceDomainRouteService routeService = mock(AIWorkspaceDomainRouteService.class);
    private final AIWorkspaceDomainService service = new AIWorkspaceDomainService(
        repository,
        new PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
            "apps.loomai.test", "https://workspace.loomai.test"),
        verifier,
        routeService
    );

    @Test
    void reservesPendingPlatformAndCustomHostsWithOnlyCurrentOrigins() {
        AIWorkspaceInstallationEntity installation = installation("awi-1");
        when(repository.findByInstallationEntityIdOrderByHostname("awi-1")).thenReturn(List.of());
        when(repository.findByHostname(any())).thenReturn(Optional.empty());

        List<String> origins = service.synchronize(
            installation,
            AIWorkspaceDeliveryMode.STANDALONE,
            "northfield",
            List.of("Assistant.Customer.Example"),
            List.of()
        );

        ArgumentCaptor<AIWorkspaceDomainEntity> saved = ArgumentCaptor.forClass(AIWorkspaceDomainEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(AIWorkspaceDomainEntity::getHostname)
            .containsExactly("northfield.apps.loomai.test", "assistant.customer.example");
        assertThat(saved.getAllValues().get(0).getDomainType()).isEqualTo(AIWorkspaceDomainType.PLATFORM_DEFAULT);
        assertThat(saved.getAllValues().get(0).getStatus()).isEqualTo(AIWorkspaceDomainStatus.PENDING);
        assertThat(saved.getAllValues().get(1).getDomainType()).isEqualTo(AIWorkspaceDomainType.CUSTOMER_CUSTOM);
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(AIWorkspaceDomainStatus.PENDING);
        assertThat(origins).containsExactly(
            "https://northfield.apps.loomai.test", "https://assistant.customer.example");
    }

    @Test
    void verifiesPlatformHostThroughManagedRouteHealthWithoutCustomerTxtRecord() {
        AIWorkspaceDomainEntity domain = domain("northfield.apps.loomai.test", "awi-1");
        domain.setStatus(AIWorkspaceDomainStatus.PENDING);
        when(repository.findByHostname("northfield.apps.loomai.test")).thenReturn(Optional.of(domain));
        when(verifier.verifyRoute("northfield.apps.loomai.test"))
            .thenReturn(new AIWorkspaceDomainVerifier.Verification(true, null));

        var summary = service.verify("awi-1", "northfield.apps.loomai.test");

        assertThat(summary.status()).isEqualTo("VERIFIED");
        assertThat(summary.dnsRecordName()).isNull();
        verify(verifier).verifyRoute("northfield.apps.loomai.test");
        verify(routeService).reconcileRequired();
    }

    @Test
    void customHostRequiresOwnershipBeforeItsCoolifyRouteIsActivated() {
        AIWorkspaceDomainEntity domain = domain("assistant.customer.example", "awi-1");
        domain.setDomainType(AIWorkspaceDomainType.CUSTOMER_CUSTOM);
        domain.setStatus(AIWorkspaceDomainStatus.PENDING);
        when(repository.findByHostname("assistant.customer.example")).thenReturn(Optional.of(domain));
        when(verifier.verifyOwnership("assistant.customer.example", "token"))
            .thenReturn(new AIWorkspaceDomainVerifier.Verification(false, "TXT missing"));

        var summary = service.verify("awi-1", "assistant.customer.example");

        assertThat(summary.status()).isEqualTo("PENDING");
        assertThat(summary.lastVerificationError()).isEqualTo("TXT missing");
        verify(routeService).reconcileIfAvailable();
        verify(verifier).verifyOwnership("assistant.customer.example", "token");
        org.mockito.Mockito.verify(verifier, org.mockito.Mockito.never()).verifyRoute(any());
    }

    @Test
    void exactVerifiedOriginResolvesAndUnknownOrPathOriginFailsClosed() {
        AIWorkspaceDomainEntity domain = domain("northfield.apps.loomai.test", "awi-1");
        when(repository.findByHostnameAndStatusIn(
            "northfield.apps.loomai.test", List.of(AIWorkspaceDomainStatus.VERIFIED)))
            .thenReturn(Optional.of(domain));

        assertThat(service.resolveVerifiedOrigin("https://northfield.apps.loomai.test")).isSameAs(domain);
        assertThatThrownBy(() -> service.resolveVerifiedOrigin("https://northfield.apps.loomai.test/path"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unavailable");
        assertThatThrownBy(() -> service.resolveVerifiedOrigin("https://unknown.apps.loomai.test"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void rejectsAHostnameAlreadyOwnedByAnotherInstallation() {
        AIWorkspaceInstallationEntity installation = installation("awi-2");
        when(repository.findByInstallationEntityIdOrderByHostname("awi-2")).thenReturn(List.of());
        when(repository.findByHostname("northfield.apps.loomai.test"))
            .thenReturn(Optional.of(domain("northfield.apps.loomai.test", "awi-1")));

        assertThatThrownBy(() -> service.synchronize(
            installation, AIWorkspaceDeliveryMode.STANDALONE, "northfield", List.of(), List.of()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already reserved");
    }

    @Test
    void deliveryAvailabilityTracksRouteHealthWithoutRevokingVerifiedOwnership() {
        AIWorkspaceDomainEntity verified = domain("northfield.apps.loomai.test", "awi-1");
        AIWorkspaceDomainEntity pending = domain("pending.apps.loomai.test", "awi-1");
        pending.setStatus(AIWorkspaceDomainStatus.PENDING);
        when(repository.findByInstallationEntityIdOrderByHostname("awi-1")).thenReturn(List.of(pending, verified));
        when(verifier.verifyRoute(verified.getHostname()))
            .thenReturn(new AIWorkspaceDomainVerifier.Verification(false, "Unavailable"))
            .thenReturn(new AIWorkspaceDomainVerifier.Verification(true, null));

        assertThat(service.deliveryAvailable("awi-1")).isFalse();
        assertThat(verified.getStatus()).isEqualTo(AIWorkspaceDomainStatus.VERIFIED);
        assertThat(service.deliveryAvailable("awi-1")).isTrue();
        org.mockito.Mockito.verify(verifier, org.mockito.Mockito.never()).verifyRoute(pending.getHostname());
    }

    private AIWorkspaceInstallationEntity installation(String id) {
        AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
        installation.setId(id);
        return installation;
    }

    private AIWorkspaceDomainEntity domain(String hostname, String installationId) {
        AIWorkspaceDomainEntity domain = new AIWorkspaceDomainEntity();
        domain.setId("domain-" + installationId);
        domain.setInstallationEntityId(installationId);
        domain.setHostname(hostname);
        domain.setDomainType(AIWorkspaceDomainType.PLATFORM_DEFAULT);
        domain.setStatus(AIWorkspaceDomainStatus.VERIFIED);
        domain.setVerificationToken("token");
        return domain;
    }
}
