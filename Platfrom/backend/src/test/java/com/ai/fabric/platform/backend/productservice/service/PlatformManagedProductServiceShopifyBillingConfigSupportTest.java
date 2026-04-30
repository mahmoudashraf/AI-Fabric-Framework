package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceShopifyBillingConfigSummary;
import com.ai.fabric.platform.backend.productservice.model.UpdatePlatformManagedProductServiceShopifyBillingConfigRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformManagedProductServiceShopifyBillingConfigSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesConfigAndBuildsRailwayEnvironment() {
        String detailsJson = PlatformManagedProductServiceShopifyBillingConfigSupport.detailsWithConfig(
            objectMapper,
            "{}",
            new UpdatePlatformManagedProductServiceShopifyBillingConfigRequest(
                "shopify_app_subscription",
                true,
                "Starter Plan",
                "Starter-Plan",
                "29",
                "usd",
                "every_30_days",
                14,
                true,
                true,
                "Elite Plan",
                "Elite-Plan",
                "179.5",
                "usd",
                "annual",
                0,
                true
            )
        ).toString();

        PlatformManagedProductServiceShopifyBillingConfigSummary summary =
            PlatformManagedProductServiceShopifyBillingConfigSupport.summaryFromDetails(
                objectMapper,
                detailsJson,
                "SHOPIFY_BRIDGE_SERVICE"
            );
        Map<String, String> env = PlatformManagedProductServiceShopifyBillingConfigSupport.railwayEnv(summary);

        assertThat(summary.mode()).isEqualTo("SHOPIFY_APP_SUBSCRIPTION");
        assertThat(summary.starterPlanHandle()).isEqualTo("starter-plan");
        assertThat(summary.starterAmount()).isEqualTo("29.00");
        assertThat(summary.eliteInterval()).isEqualTo("ANNUAL");
        assertThat(summary.eliteAmount()).isEqualTo("179.50");
        assertThat(env).containsEntry("SHOPIFY_BRIDGE_BILLING_MODE", "SHOPIFY_APP_SUBSCRIPTION");
        assertThat(env).containsEntry("SHOPIFY_BRIDGE_BILLING_ELITE_ENABLED", "true");
        assertThat(env).containsEntry("SHOPIFY_BRIDGE_BILLING_ELITE_AMOUNT", "179.50");
    }

    @Test
    void rejectsInvalidBillingMode() {
        assertThatThrownBy(() -> PlatformManagedProductServiceShopifyBillingConfigSupport.detailsWithConfig(
            objectMapper,
            "{}",
            new UpdatePlatformManagedProductServiceShopifyBillingConfigRequest(
                "MANUAL",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Billing mode must be FREE or SHOPIFY_APP_SUBSCRIPTION");
    }
}
