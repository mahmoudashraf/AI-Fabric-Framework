package com.ai.fabric.platform.backend.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformCoreServicesProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyActionResponse;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileConfig;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.model.PlatformCoreServiceActionSummary;
import com.ai.fabric.platform.backend.model.PlatformCoreServiceDeploymentSummary;
import com.ai.fabric.platform.backend.model.PlatformCoreServiceSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformCoreServiceOperationsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listServicesFetchesCoolifyStatusThroughConfiguredTargetProfile() {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTargetProfileEntity profile = targetProfile();
        CoolifyConnection connection = connection();
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("git_repository", "Loom-AI-Labs/private-platform");
        raw.put("updated_at", "2026-06-11T10:00:00Z");

        when(targetProfileRepository.findById("dtp-coolify-production")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.getApplication(connection, "app-123")).thenReturn(Optional.of(
            new CoolifyApplicationSummary(
                "app-123",
                "loomai-platform-backend",
                "https://api.loomai.pro",
                "running:healthy",
                "loomai/platform-backend",
                "latest",
                raw
            )
        ));

        PlatformCoreServiceOperationsService service = service(targetProfileRepository, resolver, coolifyApiClient, auditService, true);

        List<PlatformCoreServiceSummary> summaries = service.listServices();

        assertThat(summaries).hasSize(1);
        PlatformCoreServiceSummary summary = summaries.getFirst();
        assertThat(summary.serviceRef()).isEqualTo("loomai-platform-backend");
        assertThat(summary.status()).isEqualTo("RUNNING_HEALTHY");
        assertThat(summary.observedStatus()).isEqualTo("running:healthy");
        assertThat(summary.healthUrl()).isEqualTo("https://api.loomai.pro/actuator/health");
        assertThat(summary.details().path("gitRepository").asText()).isEqualTo("Loom-AI-Labs/private-platform");
    }

    @Test
    void deployStartsConfiguredCoolifyApplicationAndAuditsRequest() {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTargetProfileEntity profile = targetProfile();
        CoolifyConnection connection = connection();

        when(targetProfileRepository.findById("dtp-coolify-production")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.start(connection, "app-123", true, true)).thenReturn(
            new CoolifyActionResponse("Deployment queued.", "dep-coolify-123", objectMapper.createObjectNode())
        );

        PlatformCoreServiceOperationsService service = service(targetProfileRepository, resolver, coolifyApiClient, auditService, true);

        PlatformCoreServiceActionSummary summary = service.deploy("loomai-platform-backend");

        assertThat(summary.serviceRef()).isEqualTo("loomai-platform-backend");
        assertThat(summary.action()).isEqualTo("DEPLOY");
        assertThat(summary.status()).isEqualTo("REQUESTED");
        assertThat(summary.deploymentUuid()).isEqualTo("dep-coolify-123");
        verify(coolifyApiClient).start(connection, "app-123", true, true);
        verify(auditService).record(
            eq("PLATFORM_CORE_SERVICE_DEPLOY_REQUESTED"),
            eq("PLATFORM_CORE_SERVICE"),
            eq("loomai-platform-backend"),
            anyMap()
        );
    }

    @Test
    void getDeploymentReturnsSafeStatusForConfiguredCoreService() {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTargetProfileEntity profile = targetProfile();
        CoolifyConnection connection = connection();

        when(targetProfileRepository.findById("dtp-coolify-production")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.getDeployment(connection, "deploy-123")).thenReturn(Optional.of(
            new CoolifyDeploymentSummary(
                "deploy-123",
                "loomai-platform-backend",
                "app-123",
                "finished",
                "abc123",
                "Deploy current platform",
                "2026-09-15T10:00:00Z",
                "2026-09-15T10:05:00Z",
                "2026-09-15T10:05:00Z",
                objectMapper.createObjectNode().put("logs", "must not be projected")
            )
        ));

        PlatformCoreServiceOperationsService service = service(targetProfileRepository, resolver, coolifyApiClient, auditService, true);

        PlatformCoreServiceDeploymentSummary summary = service.getDeployment("deploy-123");

        assertThat(summary.serviceRef()).isEqualTo("loomai-platform-backend");
        assertThat(summary.status()).isEqualTo("finished");
        assertThat(summary.commit()).isEqualTo("abc123");
        assertThat(summary.finishedAt()).isEqualTo("2026-09-15T10:05:00Z");
    }

    @Test
    void getDeploymentDoesNotExposeNonCoreApplicationDeployments() {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTargetProfileEntity profile = targetProfile();
        CoolifyConnection connection = connection();

        when(targetProfileRepository.findById("dtp-coolify-production")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.getDeployment(connection, "deploy-other")).thenReturn(Optional.of(
            new CoolifyDeploymentSummary(
                "deploy-other",
                "customer-runtime",
                "other-app",
                "finished",
                "abc123",
                null,
                null,
                null,
                null,
                objectMapper.createObjectNode()
            )
        ));

        PlatformCoreServiceOperationsService service = service(targetProfileRepository, resolver, coolifyApiClient, auditService, true);

        assertThatThrownBy(() -> service.getDeployment("deploy-other"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disabledOperationsReturnDisabledStatusWithoutCoolifyCalls() {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformCoreServiceOperationsService service = service(targetProfileRepository, resolver, coolifyApiClient, auditService, false);

        List<PlatformCoreServiceSummary> summaries = service.listServices();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().status()).isEqualTo("DISABLED");
        verify(targetProfileRepository, never()).findById("dtp-coolify-production");
        verify(coolifyApiClient, never()).getApplication(eq(connection()), eq("app-123"));
    }

    private PlatformCoreServiceOperationsService service(DeploymentTargetProfileRepository targetProfileRepository,
                                                         CoolifyTargetProfileResolver resolver,
                                                         CoolifyApiClient coolifyApiClient,
                                                         PlatformAuditService auditService,
                                                         boolean enabled) {
        return new PlatformCoreServiceOperationsService(
            new PlatformCoreServicesProperties(
                enabled,
                "dtp-coolify-production",
                List.of(new PlatformCoreServicesProperties.CoreService(
                    "loomai-platform-backend",
                    "Platform Backend",
                    "PLATFORM_BACKEND",
                    "app-123",
                    "https://api.loomai.pro",
                    "/actuator/health"
                ))
            ),
            targetProfileRepository,
            resolver,
            coolifyApiClient,
            auditService,
            objectMapper
        );
    }

    private DeploymentTargetProfileEntity targetProfile() {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-production");
        return profile;
    }

    private CoolifyConnection connection() {
        return new CoolifyConnection(
            "http://coolify:8080",
            "token",
            new CoolifyTargetProfileConfig(
                "http://coolify:8080",
                "project",
                "production",
                "environment",
                "server",
                "destination",
                null,
                null,
                5,
                600,
                false,
                true,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
    }
}
