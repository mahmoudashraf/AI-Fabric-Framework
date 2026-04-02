package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RailwayApiProvisioningProviderTest {

    @Test
    void awaitSuccessfulDeploymentRetriesAfterTransientRailwayApiFailure() {
        AtomicInteger attempts = new AtomicInteger();

        RailwayGraphqlClient.RailwayDeploymentSummary deployment = RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
            "dep-123",
            "runtime",
            Duration.ofMillis(50),
            Duration.ZERO,
            () -> switch (attempts.getAndIncrement()) {
                case 0 -> throw new RailwayProvisioningException("Railway API request failed.");
                case 1 -> new RailwayGraphqlClient.RailwayDeploymentSummary("dep-123", "TRIGGERED", null, null, null);
                default -> new RailwayGraphqlClient.RailwayDeploymentSummary("dep-123", "SUCCESS", null, null, null);
            },
            ignored -> {
            },
            ignored -> {
            }
        );

        assertThat(deployment.status()).isEqualTo("SUCCESS");
        assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void awaitSuccessfulDeploymentTimesOutWithLastPollingErrorWhenRailwayApiKeepsFailing() {
        assertThatThrownBy(() -> RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
            "dep-123",
            "runtime",
            Duration.ofMillis(10),
            Duration.ZERO,
            () -> {
                throw new RailwayProvisioningException("Railway API request failed.");
            },
            ignored -> Thread.sleep(2),
            ignored -> {
            }
        ))
            .isInstanceOf(RailwayProvisioningException.class)
            .hasMessageContaining("after Railway API polling errors")
            .hasMessageContaining("Railway API request failed.");
    }

    @Test
    void awaitSuccessfulDeploymentMarksActivationAsUnconfirmedWhenRailwayRemainsNonTerminal() {
        assertThatThrownBy(() -> RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
            "dep-123",
            "runtime",
            Duration.ofMillis(10),
            Duration.ZERO,
            () -> new RailwayGraphqlClient.RailwayDeploymentSummary("dep-123", "DEPLOYING", null, null, null),
            ignored -> Thread.sleep(2),
            ignored -> {
            }
        ))
            .isInstanceOf(RailwayActivationUnconfirmedException.class)
            .hasMessageContaining("Last observed Railway status")
            .hasMessageContaining("DEPLOYING");
    }

    @Test
    void resolveOrTriggerDeploymentReusesFreshDeploymentAlreadyStartedForRelease() {
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        when(railwayGraphqlClient.listServiceDeployments(eq("svc-1"), anyInt()))
            .thenReturn(List.of(
                new RailwayGraphqlClient.RailwayDeploymentSummary(
                    "dep-live",
                    "SUCCESS",
                    null,
                    null,
                    Instant.parse("2026-04-01T10:09:15Z").toString()
                )
            ));

        RailwayApiProvisioningProvider provider = providerWithRailwayClient(railwayGraphqlClient, Duration.ofMillis(10));

        String deploymentId = provider.resolveOrTriggerDeployment(
            "svc-1",
            "env-1",
            "runtime",
            Instant.parse("2026-04-01T10:08:42Z")
        );

        assertThat(deploymentId).isEqualTo("dep-live");
        verify(railwayGraphqlClient, never()).deployService("svc-1", "env-1");
    }

    private RailwayApiProvisioningProvider providerWithRailwayClient(RailwayGraphqlClient railwayGraphqlClient,
                                                                     Duration pollInterval) {
        PlatformProvisioningProperties provisioningProperties = new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platformv-V2",
            "dev",
            "workspace",
            null,
            null,
            null,
            null,
            null,
            null,
            32,
            "",
            "",
            false,
            false,
            60_000,
            pollInterval,
            Duration.ofSeconds(1)
        );
        PlatformVerificationProperties verificationProperties = new PlatformVerificationProperties(
            Duration.ofSeconds(3),
            "/actuator/health",
            "/actuator/health",
            "/api/admin/overview",
            "/api/admin/actions/overview",
            "/api/admin/indexing/overview",
            "/api/admin/overview",
            "/api/admin/actions/overview"
        );
        return new RailwayApiProvisioningProvider(
            provisioningProperties,
            verificationProperties,
            mock(DeploymentManagedVectorProvisioningService.class),
            mock(DeploymentManagedVectorResourceService.class),
            mock(RailwayProvisioningPlanService.class),
            mock(DeploymentSourceResolver.class),
            railwayGraphqlClient,
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );
    }
}
