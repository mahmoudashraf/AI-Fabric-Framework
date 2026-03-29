package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RailwayPreflightServiceTest {

    @Test
    void runReportsReadyWhenRailwayConfigAndSecretsAreValid() {
        PlatformProvisioningProperties provisioningProperties = new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "dev",
            "workspace-123",
            "runtime",
            "runtime/deploy/railway/Dockerfile",
            "connector",
            "connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            32,
            "",
            "",
            false,
            false,
            60000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
        PlatformDeliveryProperties deliveryProperties = new PlatformDeliveryProperties(
            "https://platform.example",
            true,
            Duration.ofDays(3650)
        );
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        when(railwayGraphqlClient.listAccessibleWorkspaces()).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayWorkspaceSummary("workspace-123", "AI-Fabric-Platform")
        ));

        MockEnvironment environment = new MockEnvironment()
            .withProperty("OPENAI_API_KEY", "set")
            .withProperty("CONNECTOR_API_KEY", "set")
            .withProperty("ACTIONS_CONNECTOR_API_KEY", "set")
            .withProperty("PLATFORM_ARTIFACT_SIGNING_KEY", "set");
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of());
        PlatformSecretService platformSecretService = new PlatformSecretService(
            repository,
            mock(PlatformAuditService.class),
            environment
        );

        RailwayPreflightService service = new RailwayPreflightService(
            provisioningProperties,
            deliveryProperties,
            railwayGraphqlClient,
            platformSecretService
        );

        RailwayPreflightSummary summary = service.run();

        assertThat(summary.ready()).isTrue();
        assertThat(summary.workspaceName()).isEqualTo("AI-Fabric-Platform");
        assertThat(summary.checks()).noneMatch(check -> "FAILED".equals(check.status()));
    }

    @Test
    void runFailsForLocalhostBaseUrlAndMissingSecrets() {
        PlatformProvisioningProperties provisioningProperties = new PlatformProvisioningProperties(
            "RAILWAY_STUB",
            "https://backboard.railway.com/graphql/v2",
            "",
            "TheBaseRepo",
            "main",
            "dev",
            "",
            "runtime",
            "runtime/deploy/railway/Dockerfile",
            "connector",
            "connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            32,
            "",
            "",
            false,
            false,
            60000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
        PlatformDeliveryProperties deliveryProperties = new PlatformDeliveryProperties(
            "http://localhost:8088",
            true,
            Duration.ofDays(3650)
        );
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of());
        PlatformSecretService platformSecretService = new PlatformSecretService(
            repository,
            mock(PlatformAuditService.class),
            new MockEnvironment()
        );
        RailwayPreflightService service = new RailwayPreflightService(
            provisioningProperties,
            deliveryProperties,
            mock(RailwayGraphqlClient.class),
            platformSecretService
        );

        RailwayPreflightSummary summary = service.run();

        assertThat(summary.ready()).isFalse();
        assertThat(summary.checks()).anyMatch(check -> "provisioning_mode".equals(check.key()) && "FAILED".equals(check.status()));
        assertThat(summary.checks()).anyMatch(check -> "public_base_url".equals(check.key()) && "FAILED".equals(check.status()));
        assertThat(summary.checks()).anyMatch(check -> "platform_secrets".equals(check.key()) && "FAILED".equals(check.status()));
    }
}
