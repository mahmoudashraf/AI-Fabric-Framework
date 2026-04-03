package com.ai.fabric.platform.backend.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentHostedVerificationLogParser;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.deployment.service.RailwayPreflightService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformDiagnosticsServiceTest {

    @Test
    void getSummaryResolvesPlatformRailwayServiceAndRecentRuns() {
        RailwayPreflightService preflightService = mock(RailwayPreflightService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);

        when(preflightService.run()).thenReturn(new RailwayPreflightSummary(
            "RAILWAY_API",
            true,
            Instant.now().toString(),
            "https://platform.example.com",
            "ws-123",
            "workspace",
            "repo",
            "main",
            List.of(new RailwayPreflightCheckSummary("workspace", "PASSED", "ok", null))
        ));
        when(railwayGraphqlClient.listProjectsInWorkspace("ws-123")).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "platform-project",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "production")),
                List.of(new RailwayGraphqlClient.RailwayServiceSummary("svc-1", "platform-backend"))
            )
        ));
        when(railwayGraphqlClient.listServiceDomains("proj-1", "env-1", "svc-1")).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-1", "platform.example.com")
        ));
        when(railwayGraphqlClient.getServiceInstance("env-1", "svc-1")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-1",
                "svc-1",
                "platform-backend",
                "/platform",
                "Dockerfile",
                "/actuator/health",
                "https://platform.example.com",
                "org/repo",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-1")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary(
                "svc-1",
                "platform-backend",
                List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary(
                    "trg-1",
                    "svc-1",
                    "org/repo",
                    "main",
                    "github"
                ))
            )
        );
        when(railwayGraphqlClient.listServiceDeployments("svc-1", 5)).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayDeploymentSummary(
                "dep-railway-1",
                "SUCCESS",
                "https://platform.example.com",
                null,
                Instant.now().toString()
            )
        ));

        DeploymentHostedVerificationRunEntity run = new DeploymentHostedVerificationRunEntity();
        run.setId("hvr-1");
        run.setDeploymentId("dep-app");
        run.setReleaseId("rel-1");
        run.setDeploymentVersionId("ver-1");
        run.setVerificationProfile("ecommerce");
        run.setRunnerType("PLATFORM_HOSTED");
        run.setScriptPath("scripts/verify-ecommerce-deployment.sh");
        run.setStatus("FAILED");
        run.setVerifyWrite(false);
        run.setSummaryMessage("FAIL: platform overview (expected HTTP 200)");
        run.setLogOutput("""
            == Platform Health ==
            PASS: platform /actuator/health
            FAIL: platform overview (expected HTTP 200)
            """);
        run.setCreatedAt(Instant.now());
        when(runRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of(run));

        PlatformDiagnosticsService service = new PlatformDiagnosticsService(
            new PlatformProperties("AI Enablement", "prod", "Wave 3.5"),
            new PlatformDeliveryProperties("https://platform.example.com", true, Duration.ofDays(1)),
            new PlatformProvisioningProperties(
                "RAILWAY_API",
                "https://backboard.railway.com/graphql/v2",
                "token",
                "org/repo",
                "main",
                "production",
                "ws-123",
                "runtime",
                "runtime/Dockerfile",
                "connector",
                "connector/Dockerfile",
                "runtime",
                "rest",
                32,
                "",
                "",
                true,
                false,
                60000,
                Duration.ofSeconds(5),
                Duration.ofMinutes(10)
            ),
            preflightService,
            railwayGraphqlClient,
            runRepository,
            new DeploymentHostedVerificationLogParser()
        );

        var summary = service.getSummary();

        assertThat(summary.railwayService().available()).isTrue();
        assertThat(summary.railwayService().serviceName()).isEqualTo("platform-backend");
        assertThat(summary.recentHostedVerificationRuns()).hasSize(1);
        assertThat(summary.recentHostedVerificationRuns().get(0).diagnostics().failCount()).isEqualTo(1);
    }

    @Test
    void fetchLogsUsesResolvedDeploymentId() {
        RailwayPreflightService preflightService = mock(RailwayPreflightService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);

        when(railwayGraphqlClient.listProjectsInWorkspace("ws-123")).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "platform-project",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "production")),
                List.of(new RailwayGraphqlClient.RailwayServiceSummary("svc-1", "platform-backend"))
            )
        ));
        when(railwayGraphqlClient.listServiceDomains("proj-1", "env-1", "svc-1")).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-1", "platform.example.com")
        ));
        when(railwayGraphqlClient.getServiceInstance("env-1", "svc-1")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-1",
                "svc-1",
                "platform-backend",
                "/platform",
                "Dockerfile",
                "/actuator/health",
                "https://platform.example.com",
                "org/repo",
                null
            )
        );
        when(railwayGraphqlClient.getServiceSource("svc-1")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceSourceSummary("svc-1", "platform-backend", List.of())
        );
        when(railwayGraphqlClient.listServiceDeployments("svc-1", 5)).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayDeploymentSummary(
                "dep-railway-1",
                "SUCCESS",
                "https://platform.example.com",
                null,
                Instant.now().toString()
            )
        ));
        when(railwayGraphqlClient.fetchDeploymentLogs("dep-railway-1", 50, null, null, null)).thenReturn(List.of(
            new RailwayLogEntrySummary(Instant.now().toString(), "INFO", "platform started", null, List.of())
        ));

        PlatformDiagnosticsService service = new PlatformDiagnosticsService(
            new PlatformProperties("AI Enablement", "prod", "Wave 3.5"),
            new PlatformDeliveryProperties("https://platform.example.com", true, Duration.ofDays(1)),
            new PlatformProvisioningProperties(
                "RAILWAY_API",
                "https://backboard.railway.com/graphql/v2",
                "token",
                "org/repo",
                "main",
                "production",
                "ws-123",
                "runtime",
                "runtime/Dockerfile",
                "connector",
                "connector/Dockerfile",
                "runtime",
                "rest",
                32,
                "",
                "",
                true,
                false,
                60000,
                Duration.ofSeconds(5),
                Duration.ofMinutes(10)
            ),
            preflightService,
            railwayGraphqlClient,
            runRepository,
            new DeploymentHostedVerificationLogParser()
        );

        var response = service.fetchLogs("deployment", 50, null, null, null);

        assertThat(response.available()).isTrue();
        assertThat(response.railwayDeploymentId()).isEqualTo("dep-railway-1");
        assertThat(response.entries()).hasSize(1);
    }
}
