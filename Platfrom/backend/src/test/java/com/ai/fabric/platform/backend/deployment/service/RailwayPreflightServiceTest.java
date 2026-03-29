package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
            "connector",
            "runtime",
            "rest-connector",
            "",
            "",
            false,
            false,
            60000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
        PlatformDeliveryProperties deliveryProperties = new PlatformDeliveryProperties("https://platform.example");
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        when(railwayGraphqlClient.listAccessibleWorkspaces()).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayWorkspaceSummary("workspace-123", "AI-Fabric-Platform")
        ));

        MockEnvironment environment = new MockEnvironment()
            .withProperty("OPENAI_API_KEY", "set")
            .withProperty("CONNECTOR_API_KEY", "set")
            .withProperty("ACTIONS_CONNECTOR_API_KEY", "set");

        RailwayPreflightService service = new RailwayPreflightService(
            provisioningProperties,
            deliveryProperties,
            railwayGraphqlClient,
            environment
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
            "connector",
            "runtime",
            "rest-connector",
            "",
            "",
            false,
            false,
            60000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
        PlatformDeliveryProperties deliveryProperties = new PlatformDeliveryProperties("http://localhost:8088");
        RailwayPreflightService service = new RailwayPreflightService(
            provisioningProperties,
            deliveryProperties,
            mock(RailwayGraphqlClient.class),
            new MockEnvironment()
        );

        RailwayPreflightSummary summary = service.run();

        assertThat(summary.ready()).isFalse();
        assertThat(summary.checks()).anyMatch(check -> "provisioning_mode".equals(check.key()) && "FAILED".equals(check.status()));
        assertThat(summary.checks()).anyMatch(check -> "public_base_url".equals(check.key()) && "FAILED".equals(check.status()));
        assertThat(summary.checks()).anyMatch(check -> "platform_secrets".equals(check.key()) && "FAILED".equals(check.status()));
    }
}
