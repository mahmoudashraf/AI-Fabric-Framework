package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationScriptContextSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformVerificationSuiteScriptContextServiceTest {

    @Test
    void buildsPlatformAdminRegressionContextFromCanonicalRollouts() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");
        when(rolloutService.listRollouts()).thenReturn(new DeploymentVerificationRolloutSummary(
            "ready",
            List.of(
                new DeploymentVerificationRolloutItemSummary(
                    "ecommerce",
                    "Ecommerce",
                    "",
                    "ecommerce",
                    false,
                    "dep-ecommerce",
                    "dev",
                    true,
                    false,
                    true,
                    "ACTIVE",
                    "ver-1",
                    "APPLIED_VERIFIED",
                    "READY",
                    "PASSED",
                    "https://runtime.example.test",
                    true,
                    "ready",
                    List.of()
                )
            )
        ));

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null
            ),
            new PlatformDeliveryProperties("https://platform.example.test", true, Duration.ofDays(1)),
            new PlatformAuthProperties(
                true,
                "X-PLATFORM-API-KEY",
                true,
                true,
                "sid",
                Duration.ofHours(8),
                true,
                "Lax",
                null,
                null,
                false,
                null,
                null,
                null
            ),
            secretService,
            rolloutService
        );

        PlatformVerificationScriptContextSummary context = service.build(PlatformVerificationSuiteScriptContextService.SCRIPT_PLATFORM_ADMIN_REGRESSION);

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-platform-admin-regression.sh");
        assertThat(context.environment()).containsEntry("PLATFORM_BASE_URL", "https://platform.example.test");
        assertThat(context.environment()).containsEntry("PLATFORM_UI_BASE_URL", "https://platform-ui.example.test");
        assertThat(context.environment()).containsEntry("ADMIN_TARGET_DEPLOYMENT_ID", "dep-ecommerce");
        assertThat(context.secretEnvironment()).containsEntry("PLATFORM_API_KEY", "admin-key");
    }

    @Test
    void providerVerificationRequiresWeaviateHostAndSecrets() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        when(secretService.resolveSecret("PINECONE_API_KEY")).thenReturn("pinecone");
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-mgmt");
        when(secretService.resolveSecret("QDRANT_API_KEY")).thenReturn("qdrant");
        when(secretService.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn("zilliz");
        when(secretService.resolveSecret("WEAVIATE_API_KEY")).thenReturn(null);

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null
            ),
            new PlatformDeliveryProperties("https://platform.example.test", true, Duration.ofDays(1)),
            new PlatformAuthProperties(
                true,
                "X-PLATFORM-API-KEY",
                true,
                true,
                "sid",
                Duration.ofHours(8),
                true,
                "Lax",
                null,
                null,
                false,
                null,
                null,
                null
            ),
            secretService,
            rolloutService
        );

        assertThatThrownBy(() -> service.build(PlatformVerificationSuiteScriptContextService.SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("WEAVIATE_API_KEY");
    }

    @Test
    void buildsPlatformCodeRegressionContextWithExtendedTimeout() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(180),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null
            ),
            new PlatformDeliveryProperties("https://platform.example.test", true, Duration.ofDays(1)),
            new PlatformAuthProperties(
                true,
                "X-PLATFORM-API-KEY",
                true,
                true,
                "sid",
                Duration.ofHours(8),
                true,
                "Lax",
                null,
                null,
                false,
                null,
                null,
                null
            ),
            secretService,
            rolloutService
        );

        PlatformVerificationScriptContextSummary context = service.build(PlatformVerificationSuiteScriptContextService.SCRIPT_PLATFORM_CODE_REGRESSION);

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-platform-code-regression.sh");
        assertThat(context.secretEnvironment()).isEmpty();
        assertThat(context.environment()).containsEntry("BACKEND_TESTS", "true");
        assertThat(context.timeoutOverride()).isEqualTo(Duration.ofMinutes(75));
        assertThat(context.maxOutputCharactersOverride()).isEqualTo(80_000);
    }
}
