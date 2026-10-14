package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class AIWorkspaceDomainRouteServiceTest {

    @Test
    void replacesOnlyPreviouslyManagedRoutesAndKeepsPendingCustomDomainsUnrouted() {
        AIWorkspaceDomainRepository domains = mock(AIWorkspaceDomainRepository.class);
        PlatformManagedProductServiceRepository services = mock(PlatformManagedProductServiceRepository.class);
        DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolify = mock(CoolifyApiClient.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("service-1");
        service.setServiceRef("loomai-engage-web");
        service.setDisplayName("LoomAI Engage");
        service.setServiceKind(PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE);
        service.setStatus("ACTIVE");
        service.setBaseUrl("https://workspace.loomai.test,https://stale.customer.example");
        service.setDetailsJson("""
            {
              "providerType":"COOLIFY",
              "targetProfileId":"dtp-coolify-staging",
              "coolifyApplicationUuid":"app-1",
              "aiWorkspaceManagedDomains":["stale.customer.example"]
            }
            """);
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-staging");
        CoolifyConnection connection = mock(CoolifyConnection.class);

        when(services.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(service));
        when(profiles.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(coolify.getApplication(connection, "app-1")).thenReturn(Optional.of(new CoolifyApplicationSummary(
            "app-1", "loomai-engage-web",
            "https://workspace.loomai.test,https://stale.customer.example", "running", null, null,
            objectMapper.createObjectNode())));
        when(domains.findAllByOrderByHostname()).thenReturn(List.of(
            domain("northfield.apps.loomai.test", AIWorkspaceDomainType.PLATFORM_DEFAULT, AIWorkspaceDomainStatus.PENDING),
            domain("owned.customer.example", AIWorkspaceDomainType.CUSTOMER_CUSTOM, AIWorkspaceDomainStatus.ROUTING_PENDING),
            domain("unverified.customer.example", AIWorkspaceDomainType.CUSTOMER_CUSTOM, AIWorkspaceDomainStatus.PENDING)
        ));

        AIWorkspaceDomainRouteService routeService = new AIWorkspaceDomainRouteService(
            domains, services, profiles, resolver, coolify,
            new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
                "apps.loomai.test", "https://workspace.loomai.test"),
            provisioningProperties(),
            audit, objectMapper);

        routeService.reconcileRequired();

        ArgumentCaptor<String> routes = ArgumentCaptor.forClass(String.class);
        verify(coolify).updateApplicationDomains(eq(connection), eq("app-1"), routes.capture());
        verify(coolify).start(connection, "app-1", true, true);
        assertThat(routes.getValue()).isEqualTo(String.join(",",
            "https://workspace.loomai.test",
            "https://northfield.apps.loomai.test",
            "https://owned.customer.example"));
        assertThat(service.getBaseUrl()).isEqualTo("https://workspace.loomai.test");
        assertThat(service.getDetailsJson())
            .contains("northfield.apps.loomai.test", "owned.customer.example")
            .doesNotContain("stale.customer.example", "unverified.customer.example");
        verify(services).save(service);
        verify(audit).record(eq("AI_WORKSPACE_FRONTEND_DOMAINS_RECONCILED"), eq("MANAGED_PRODUCT_SERVICE"),
            eq("loomai-engage-web"), any());
    }

    @Test
    void skipsCoolifyUpdateWhenRoutesAlreadyMatch() {
        Fixture fixture = fixture(
            "https://workspace.loomai.test,https://northfield.apps.loomai.test",
            "{\"providerType\":\"COOLIFY\",\"targetProfileId\":\"dtp-coolify-staging\","
                + "\"coolifyApplicationUuid\":\"app-1\",\"aiWorkspaceManagedDomains\":[\"northfield.apps.loomai.test\"]}"
        );
        when(fixture.domains().findAllByOrderByHostname()).thenReturn(List.of(
            domain("northfield.apps.loomai.test", AIWorkspaceDomainType.PLATFORM_DEFAULT, AIWorkspaceDomainStatus.PENDING)
        ));

        fixture.routeService().reconcileRequired();

        verify(fixture.coolify(), never()).updateApplicationDomains(any(), any(), any());
        verify(fixture.coolify(), never()).start(any(), any(), any(Boolean.class), any(Boolean.class));
        assertThat(fixture.service().getBaseUrl()).isEqualTo("https://workspace.loomai.test");
    }

    @Test
    void bestEffortReconcileRecordsFailureWithoutRejectingConfiguration() {
        Fixture fixture = fixture("https://workspace.loomai.test", "{\"providerType\":\"COOLIFY\","
            + "\"targetProfileId\":\"dtp-coolify-staging\",\"coolifyApplicationUuid\":\"app-1\"}");
        when(fixture.domains().findAllByOrderByHostname()).thenReturn(List.of(
            domain("northfield.apps.loomai.test", AIWorkspaceDomainType.PLATFORM_DEFAULT, AIWorkspaceDomainStatus.PENDING)
        ));
        doThrow(new IllegalStateException("Coolify unavailable"))
            .when(fixture.coolify()).updateApplicationDomains(any(), any(), any());

        assertThat(fixture.routeService().reconcileIfAvailable()).isFalse();

        assertThat(fixture.service().getDetailsJson())
            .contains("lastWorkspaceDomainReconcileStatus", "FAILED", "Coolify unavailable");
        verify(fixture.audit()).record(eq("AI_WORKSPACE_FRONTEND_DOMAINS_RECONCILE_FAILED"),
            eq("MANAGED_PRODUCT_SERVICE"), eq("loomai-engage-web"), any());
    }

    private Fixture fixture(String fqdn, String detailsJson) {
        AIWorkspaceDomainRepository domains = mock(AIWorkspaceDomainRepository.class);
        PlatformManagedProductServiceRepository services = mock(PlatformManagedProductServiceRepository.class);
        DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolify = mock(CoolifyApiClient.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("service-1");
        service.setServiceRef("loomai-engage-web");
        service.setDisplayName("LoomAI Engage");
        service.setServiceKind(PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE);
        service.setStatus("ACTIVE");
        service.setBaseUrl("https://workspace.loomai.test");
        service.setDetailsJson(detailsJson);
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-staging");
        CoolifyConnection connection = mock(CoolifyConnection.class);
        when(services.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(service));
        when(profiles.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(coolify.getApplication(connection, "app-1")).thenReturn(Optional.of(new CoolifyApplicationSummary(
            "app-1", "loomai-engage-web", fqdn, "running", null, null, objectMapper.createObjectNode())));
        AIWorkspaceDomainRouteService routeService = new AIWorkspaceDomainRouteService(
            domains, services, profiles, resolver, coolify,
            new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
                "apps.loomai.test", "https://workspace.loomai.test"),
            provisioningProperties(),
            audit, objectMapper);
        return new Fixture(routeService, domains, coolify, audit, service);
    }

    private record Fixture(AIWorkspaceDomainRouteService routeService,
                           AIWorkspaceDomainRepository domains,
                           CoolifyApiClient coolify,
                           PlatformAuditService audit,
                           PlatformManagedProductServiceEntity service) {
    }

    private AIWorkspaceDomainEntity domain(String hostname,
                                             AIWorkspaceDomainType type,
                                             AIWorkspaceDomainStatus status) {
        AIWorkspaceDomainEntity domain = new AIWorkspaceDomainEntity();
        domain.setId("domain-" + hostname);
        domain.setInstallationEntityId("installation-1");
        domain.setHostname(hostname);
        domain.setDomainType(type);
        domain.setStatus(status);
        domain.setVerificationToken("token");
        return domain;
    }

    private PlatformProductProvisioningProperties provisioningProperties() {
        PlatformProductProvisioningProperties properties = mock(PlatformProductProvisioningProperties.class);
        when(properties.pollInterval()).thenReturn(Duration.ofMillis(1));
        when(properties.requestTimeout()).thenReturn(Duration.ofSeconds(1));
        return properties;
    }
}
