package com.ai.fabric.product.shopify.bridge.governedaction.service;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.entity.ShopifyBridgeGovernedActionAuditEntity;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCompletionRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantRequest;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.governedaction.repository.ShopifyBridgeGovernedActionAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontGovernedActionServiceTest {

    @Test
    void capabilityReflectsEliteGuidedCommerceEntitlements() {
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(eliteSummary());

        ShopifyStorefrontGovernedActionService service = new ShopifyStorefrontGovernedActionService(
            mock(PlatformShopifyStoreClient.class),
            emptyCredentialService(),
            billingService,
            mock(ShopifyBridgeGovernedActionAuditRepository.class),
            tokenService(),
            mock(ShopifyBridgeUsageService.class),
            fixedClock()
        );

        ShopifyStorefrontGovernedActionCapability capability = service.capability(
            "alpha.myshopify.com",
            "https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/actions/grant",
            "https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/actions/complete"
        );

        assertThat(capability.available()).isTrue();
        assertThat(capability.requiresExplicitConfirmation()).isTrue();
        assertThat(capability.auditTrailAvailable()).isTrue();
        assertThat(capability.allowedActionTypes()).containsExactly("ADD_TO_CART", "UPDATE_CART_QUANTITY");
        assertThat(capability.grantUrl()).contains("/actions/grant");
        assertThat(capability.completeUrl()).contains("/actions/complete");
    }

    @Test
    void grantAndCompletePersistGovernedCommerceAudit() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeGovernedActionAuditRepository repository = mock(ShopifyBridgeGovernedActionAuditRepository.class);
        AtomicReference<ShopifyBridgeGovernedActionAuditEntity> savedAudit = new AtomicReference<>();
        when(repository.save(any(ShopifyBridgeGovernedActionAuditEntity.class))).thenAnswer(invocation -> {
            ShopifyBridgeGovernedActionAuditEntity entity = invocation.getArgument(0);
            savedAudit.set(entity);
            return entity;
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(savedAudit.get()));
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(eliteSummary());

        ShopifyStorefrontGovernedActionService service = new ShopifyStorefrontGovernedActionService(
            platformClient,
            emptyCredentialService(),
            billingService,
            repository,
            tokenService(),
            usageService,
            fixedClock()
        );

        var grant = service.grant(
            "alpha.myshopify.com",
            new ShopifyStorefrontGovernedActionGrantRequest(
                "ADD_TO_CART",
                "product-insight",
                "product",
                "101",
                "travel-pack",
                "Travel Pack",
                "202",
                2,
                null,
                null,
                true
            ),
            "shopper-session-1"
        );

        assertThat(grant.operationKind()).isEqualTo("CART_ADD");
        assertThat(grant.confirmationRequired()).isTrue();
        assertThat(grant.variantId()).isEqualTo("202");
        assertThat(savedAudit.get()).isNotNull();
        assertThat(savedAudit.get().getStatus()).isEqualTo("GRANTED");
        assertThat(savedAudit.get().getRequestedQuantity()).isEqualTo(2);

        var completed = service.complete(
            "alpha.myshopify.com",
            new ShopifyStorefrontGovernedActionCompletionRequest(
                grant.auditId(),
                grant.token(),
                "COMPLETED",
                "Guided add-to-cart completed.",
                "line-1",
                2
            ),
            "shopper-session-1"
        );

        assertThat(completed.id()).isEqualTo(grant.auditId());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.resultingQuantity()).isEqualTo(2);
        assertThat(savedAudit.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(savedAudit.get().getCartLineKey()).isEqualTo("line-1");
        verify(usageService).recordEvent("alpha.myshopify.com", "STOREFRONT_ACTION_GRANTED_PRODUCT_INSIGHT");
        verify(usageService).recordEvent("alpha.myshopify.com", "STOREFRONT_ACTION_COMPLETED_PRODUCT_INSIGHT");
    }

    private ShopifyBridgeInstallCredentialService emptyCredentialService() {
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        return installCredentialService;
    }

    private ShopifyStorefrontGovernedActionTokenService tokenService() {
        return new ShopifyStorefrontGovernedActionTokenService(
            new ShopifyBridgeProperties(
                "Shopify Bridge",
                "shopify-bridge-prod",
                "SHOPIFY",
                "SHOPIFY_BRIDGE_SERVICE",
                "production",
                "https://bridge.example.com",
                "2026-04",
                "shopify-api-key",
                "shopify-secret",
                "https://platform.example.com",
                "platform-admin-key",
                "X-PLATFORM-API-KEY",
                "webhook-secret",
                "bridge-admin-key",
                "X-BRIDGE-API-KEY"
            ),
            new ObjectMapper(),
            fixedClock()
        );
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-04-23T12:00:00Z"), ZoneOffset.UTC);
    }

    private ShopifyBridgeBillingSummary eliteSummary() {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "ELITE",
            "Loom Companion Elite",
            "ACTIVE",
            false,
            false,
            true,
            true,
            null,
            "REALTIME",
            false,
            true,
            true,
            true,
            List.of("guided-commerce"),
            List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison", "order-lookup"),
            List.of(
                new ShopifyBridgeBillingPlanSummary(
                    "ELITE",
                    "Loom Companion Elite",
                    "179.00",
                    "USD",
                    "EVERY_30_DAYS",
                    true,
                    true,
                    true,
                    true,
                    null,
                    "REALTIME",
                    false,
                    true,
                    true,
                    true,
                    List.of("guided-commerce"),
                    List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison", "order-lookup"),
                    "Elite tier is active."
                )
            ),
            "Elite tier is active."
        );
    }
}
