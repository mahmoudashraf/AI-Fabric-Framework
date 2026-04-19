package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourcePageResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyBridgeVectorizationSourceServiceTest {

    @Test
    void pageReturnsProductsThenCollectionsUsingCompositeCursor() {
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com"))
            .thenReturn(Optional.of(acquisition(productsAndCollectionsStore())));
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), contains("ProductsVectorizationCount")))
            .thenReturn(Map.of("data", Map.of("productsCount", Map.of("count", 1))));
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), contains("CollectionsVectorizationCount")))
            .thenReturn(Map.of("data", Map.of("collectionsCount", Map.of("count", 2))));
        when(shopifyAdminGraphqlClient.execute(
            eq("alpha.myshopify.com"),
            eq("access-token"),
            contains("ProductsVectorizationPage"),
            anyMap()
        )).thenReturn(Map.of(
            "data",
            Map.of(
                "products",
                Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(
                        Map.of(
                            "node",
                            Map.of(
                                "id", "gid://shopify/Product/1",
                                "title", "Trail Shoe",
                                "handle", "trail-shoe",
                                "descriptionHtml", "<p>Breathable trail shoe</p>",
                                "vendor", "Loom",
                                "productType", "Shoes",
                                "updatedAt", "2026-04-19T10:00:00Z"
                            )
                        )
                    )
                )
            )
        ));
        when(shopifyAdminGraphqlClient.execute(
            eq("alpha.myshopify.com"),
            eq("access-token"),
            contains("CollectionsVectorizationPage"),
            argThat(variables -> "".equals(variables.get("cursor")) || variables.get("cursor") == null)
        )).thenReturn(Map.of(
            "data",
            Map.of(
                "collections",
                Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(
                        Map.of(
                            "node",
                            Map.of(
                                "id", "gid://shopify/Collection/1",
                                "title", "Summer Sale",
                                "handle", "summer-sale",
                                "descriptionHtml", "<p>Seasonal highlights</p>",
                                "updatedAt", "2026-04-19T11:00:00Z"
                            )
                        )
                    )
                )
            )
        ));

        ShopifyBridgeVectorizationSourceService service =
            new ShopifyBridgeVectorizationSourceService(installCredentialService, shopifyAdminGraphqlClient);

        ShopifyBridgeVectorizationSourcePageResponse firstPage = service.page("alpha.myshopify.com", "product", null, 25);
        ShopifyBridgeVectorizationSourcePageResponse secondPage = service.page("alpha.myshopify.com", "product", "collections|", 25);

        assertThat(firstPage.totalCount()).isEqualTo(3);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("collections|");
        assertThat(firstPage.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceCategory()).isEqualTo("products");
            assertThat(item.documentType()).isEqualTo("product");
            assertThat(item.title()).isEqualTo("Trail Shoe");
        });

        assertThat(secondPage.totalCount()).isEqualTo(3);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(secondPage.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceCategory()).isEqualTo("collections");
            assertThat(item.documentType()).isEqualTo("collection");
            assertThat(item.title()).isEqualTo("Summer Sale");
        });
    }

    @Test
    void pageReturnsPoliciesWithOffsetCursor() {
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com"))
            .thenReturn(Optional.of(acquisition(policiesOnlyStore())));
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), contains("PoliciesVectorizationPage")))
            .thenReturn(Map.of(
                "data",
                Map.of(
                    "shop",
                    Map.of(
                        "shopPolicies",
                        List.of(
                            Map.of(
                                "id", "gid://shopify/ShopPolicy/1",
                                "title", "Refund policy",
                                "type", "REFUND_POLICY",
                                "body", "<p>30-day returns</p>",
                                "url", "https://alpha.myshopify.com/policies/refund-policy",
                                "updatedAt", "2026-04-19T12:00:00Z"
                            ),
                            Map.of(
                                "id", "gid://shopify/ShopPolicy/2",
                                "title", "Privacy policy",
                                "type", "PRIVACY_POLICY",
                                "body", "<p>Privacy details</p>",
                                "url", "https://alpha.myshopify.com/policies/privacy-policy",
                                "updatedAt", "2026-04-19T12:05:00Z"
                            )
                        )
                    )
                )
            ));

        ShopifyBridgeVectorizationSourceService service =
            new ShopifyBridgeVectorizationSourceService(installCredentialService, shopifyAdminGraphqlClient);

        ShopifyBridgeVectorizationSourcePageResponse firstPage = service.page("alpha.myshopify.com", "support-policy", null, 1);
        ShopifyBridgeVectorizationSourcePageResponse secondPage = service.page("alpha.myshopify.com", "support-policy", "policies|1", 1);

        assertThat(firstPage.totalCount()).isEqualTo(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("policies|1");
        assertThat(firstPage.items().getFirst().policyType()).isEqualTo("REFUND_POLICY");

        assertThat(secondPage.totalCount()).isEqualTo(2);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.items().getFirst().policyType()).isEqualTo("PRIVACY_POLICY");
    }

    private ShopifyBridgeCredentialAcquisition acquisition(ShopifyBridgeStoreSummary store) {
        return new ShopifyBridgeCredentialAcquisition(
            store,
            new ShopifyTokenExchangeMaterial(
                "access-token",
                "refresh-token",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "read_products,read_content,read_legal_policies",
                false
            )
        );
    }

    private Map<String, Object> pageInfo(boolean hasNextPage, String endCursor) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("hasNextPage", hasNextPage);
        value.put("endCursor", endCursor);
        return value;
    }

    private ShopifyBridgeStoreSummary productsAndCollectionsStore() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Service",
            "cus-1",
            "Customer",
            "dep-1",
            "Deployment",
            "ACTIVE",
            "consumer-1",
            "Consumer",
            "INSTALLED",
            "SYNCED",
            "READY",
            "ENABLED",
            "READY_FOR_ONBOARDING",
            true,
            true,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
    }

    private ShopifyBridgeStoreSummary policiesOnlyStore() {
        return new ShopifyBridgeStoreSummary(
            "shp-2",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Service",
            "cus-1",
            "Customer",
            "dep-1",
            "Deployment",
            "ACTIVE",
            "consumer-1",
            "Consumer",
            "INSTALLED",
            "SYNCED",
            "READY",
            "ENABLED",
            "READY_FOR_ONBOARDING",
            false,
            false,
            false,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
    }
}
