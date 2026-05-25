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
import java.util.Map;

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
                    false,
                    List.of(),
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
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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
        assertThat(context.environment()).containsEntry("INFERENCE_SERVICE_REF", "openai-cloud-orchestration");
        assertThat(context.secretEnvironment()).containsEntry("PLATFORM_API_KEY", "admin-key");
    }

    @Test
    void buildsReleaseBlockingQdrantProviderVerificationContext() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        when(secretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn("qdrant-mgmt");
        when(secretService.resolveSecret("QDRANT_API_KEY")).thenReturn("qdrant");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION
        );

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-managed-vector-providers.sh");
        assertThat(context.environment())
            .containsEntry("RUN_PINECONE", "false")
            .containsEntry("RUN_QDRANT", "true")
            .containsEntry("RUN_ZILLIZ", "false")
            .containsEntry("RUN_WEAVIATE", "false")
            .containsEntry("QDRANT_EXISTING_CLUSTER_NAME", "cluster")
            .containsEntry("QDRANT_CREATE_EPHEMERAL_DB_KEY", "false")
            .containsEntry("QDRANT_CREATE_EPHEMERAL_CLUSTER", "false");
        assertThat(context.environment()).doesNotContainKey("WEAVIATE_HOST");
        assertThat(context.secretEnvironment())
            .containsEntry("QDRANT_CLOUD_MANAGEMENT_API_KEY", "qdrant-mgmt")
            .containsEntry("QDRANT_API_KEY", "qdrant");
        assertThat(context.secretEnvironment())
            .doesNotContainKeys("PINECONE_API_KEY", "ZILLIZ_CLOUD_API_KEY", "WEAVIATE_API_KEY");
    }

    @Test
    void buildsMarketplaceInstallFlowContextWithReleaseVectorDefaults() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_MARKETPLACE_INSTALL_FLOW
        );

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-marketplace-install-flow.sh");
        assertThat(context.environment()).containsEntry("PLATFORM_BASE_URL", "https://platform.example.test");
        assertThat(context.environment()).containsEntry("MARKETPLACE_INSTALL_FLOW_APPLY_RELEASE", "false");
        assertThat(context.environment()).doesNotContainKeys(
            "VALIDATION_TEMPLATE_ID",
            "VALIDATION_VECTOR_PROVISIONING_MODE",
            "MARKETPLACE_INSTALL_FLOW_SHARED_VECTOR_PATCH"
        );
        assertThat(context.secretEnvironment()).containsEntry("PLATFORM_API_KEY", "admin-key");
    }

    @Test
    void buildMergesShopifyEnvironmentOverrides() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_COMPANION_VERIFICATION,
            Map.of(
                "EXPECT_BILLING_TIER", "ELITE",
                "EXPECT_STOREFRONT_READY", "false",
                "EXPECT_ORDER_LOOKUP_STATUS", "PENDING_SCOPE_GRANT"
            )
        );

        assertThat(context.environment()).containsEntry("SHOPIFY_BRIDGE_BASE_URL", "https://bridge.example.test");
        assertThat(context.environment()).containsEntry("SHOP_DOMAIN", "shop.example.test");
        assertThat(context.environment()).containsEntry("SHOPIFY_COMPANION_ENSURE_BILLING_STATE", "true");
        assertThat(context.environment()).containsEntry("EXPECT_BILLING_TIER", "ELITE");
        assertThat(context.environment()).containsEntry("EXPECT_BILLING_STATUS", "ACTIVE");
        assertThat(context.environment()).containsEntry("EXPECT_STOREFRONT_READY", "false");
        assertThat(context.environment()).containsEntry("EXPECT_ORDER_LOOKUP_STATUS", "PENDING_SCOPE_GRANT");
    }

    @Test
    void buildsShopifyFirstProductReadinessAuditContext() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");
        when(secretService.resolveSecret("SHOPIFY_BRIDGE_ADMIN_API_KEY")).thenReturn("bridge-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT
        );

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-shopify-first-product-readiness-audit.sh");
        assertThat(context.environment()).containsEntry("PLATFORM_BASE_URL", "https://platform.example.test");
        assertThat(context.environment()).containsEntry("SHOPIFY_BRIDGE_BASE_URL", "https://bridge.example.test");
        assertThat(context.environment()).containsEntry("SHOP_DOMAIN", "shop.example.test");
        assertThat(context.environment()).containsEntry("PRODUCT_SERVICE_REF", "shopify-bridge-prod");
        assertThat(context.environment()).containsEntry("READINESS_AUDIT_LOCAL_GATES", "false");
        assertThat(context.secretEnvironment()).containsEntry("PLATFORM_API_KEY", "admin-key");
        assertThat(context.secretEnvironment()).containsEntry("SHOPIFY_BRIDGE_ADMIN_API_KEY", "bridge-key");
    }

    @Test
    void buildsShopifyMcpGatewayReleaseGateContextWithManagedSecrets() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");
        when(secretService.resolveSecret("SHOPIFY_BRIDGE_ADMIN_API_KEY")).thenReturn("bridge-key");
        when(secretService.resolveSecret("MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY")).thenReturn("gateway-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_MCP_GATEWAY_VERIFICATION
        );

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-shopify-mcp-gateway.sh");
        assertThat(context.environment()).containsEntry("PLATFORM_BASE_URL", "https://platform.example.test");
        assertThat(context.environment()).containsEntry("SHOPIFY_BRIDGE_BASE_URL", "https://bridge.example.test");
        assertThat(context.environment()).containsEntry("SHOP_DOMAIN", "shop.example.test");
        assertThat(context.environment()).containsEntry("PRODUCT_SERVICE_REF", "shopify-bridge-prod");
        assertThat(context.environment()).containsEntry("MCP_GATEWAY_PRODUCT_SERVICE_REF", "mcp-execution-gateway");
        assertThat(context.environment()).containsEntry("MCP_GATEWAY_API_KEY_HEADER", "X-MCP-GATEWAY-API-KEY");
        assertThat(context.secretEnvironment()).containsEntry("PLATFORM_API_KEY", "admin-key");
        assertThat(context.secretEnvironment()).containsEntry("SHOPIFY_BRIDGE_ADMIN_API_KEY", "bridge-key");
        assertThat(context.secretEnvironment()).containsEntry("MCP_GATEWAY_API_KEY", "gateway-key");
    }

    @Test
    void shopifyMcpGatewayReleaseGateRequiresManagedGatewaySecret() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");
        when(secretService.resolveSecret("SHOPIFY_BRIDGE_ADMIN_API_KEY")).thenReturn("bridge-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        assertThatThrownBy(() -> service.build(PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_MCP_GATEWAY_VERIFICATION))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY");
    }

    @Test
    void buildsPartnerEnablementStrictLiveContext() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PARTNER_SUPABASE_JWT")).thenReturn("partner-jwt");
        when(secretService.resolveSecret("MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY")).thenReturn("product-service-key");
        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION
        );

        assertThat(context.scriptPath()).isEqualTo("scripts/verify-partner-enablement-live.sh");
        assertThat(context.environment()).containsEntry("PLATFORM_BASE_URL", "https://platform.example.test");
        assertThat(context.environment()).containsEntry("PLATFORM_UI_BASE_URL", "https://platform-ui.example.test");
        assertThat(context.environment()).containsEntry("PARTNER_UI_BASE_URL", "https://partner-ui.example.test");
        assertThat(context.environment()).containsEntry("PARTNER_LIVE_STRICT", "true");
        assertThat(context.environment()).containsEntry("PARTNER_LIVE_SHOP_DOMAIN", "shop.example.test");
        assertThat(context.secretEnvironment()).containsEntry("PARTNER_SUPABASE_JWT", "partner-jwt");
        assertThat(context.secretEnvironment()).containsEntry("PLATFORM_API_KEY", "admin-key");
    }

    @Test
    void partnerEnablementContextCannotDisableStrictModeWithOverrides() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PARTNER_SUPABASE_JWT")).thenReturn("partner-jwt");
        when(secretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-key");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        PlatformVerificationScriptContextSummary context = service.build(
            PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION,
            Map.of("PARTNER_LIVE_STRICT", "false")
        );

        assertThat(context.environment()).containsEntry("PARTNER_LIVE_STRICT", "true");
    }

    @Test
    void partnerEnablementContextRequiresPartnerJwtSecret() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        assertThatThrownBy(() -> service.build(PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PARTNER_SUPABASE_JWT");
    }

    @Test
    void partnerEnablementContextRequiresPlatformApiKeyForApprovalProof() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        when(secretService.resolveSecret("PARTNER_SUPABASE_JWT")).thenReturn("partner-jwt");

        PlatformVerificationSuiteScriptContextService service = new PlatformVerificationSuiteScriptContextService(
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofMinutes(20),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(3),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
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

        assertThatThrownBy(() -> service.build(PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PLATFORM_ADMIN_API_KEY");
    }

}
