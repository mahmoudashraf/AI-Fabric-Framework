package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
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
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);

        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com"))
            .thenReturn(Optional.of(acquisition(productsAndCollectionsStore())));
        when(billingService.catalogProductCap("alpha.myshopify.com", "access-token")).thenReturn(null);
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
                                "metafields", Map.of(
                                    "edges", List.of(
                                        Map.of("node", Map.of(
                                            "namespace", "custom",
                                            "key", "materials",
                                            "type", "multi_line_text_field",
                                            "value", "Breathable mesh upper"
                                        )),
                                        Map.of("node", Map.of(
                                            "namespace", "judgeme",
                                            "key", "rating",
                                            "type", "number_decimal",
                                            "value", "4.7"
                                        )),
                                        Map.of("node", Map.of(
                                            "namespace", "judgeme",
                                            "key", "review_count",
                                            "type", "number_integer",
                                            "value", "64"
                                        )),
                                        Map.of("node", Map.of(
                                            "namespace", "custom",
                                            "key", "fit_notes",
                                            "type", "single_line_text_field",
                                            "value", "Runs true to size"
                                        ))
                                    )
                                ),
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
            new ShopifyBridgeVectorizationSourceService(installCredentialService, shopifyAdminGraphqlClient, billingService);

        ShopifyBridgeVectorizationSourcePageResponse firstPage = service.page("alpha.myshopify.com", "product", null, 25);
        ShopifyBridgeVectorizationSourcePageResponse secondPage = service.page("alpha.myshopify.com", "product", "collections||0", 25);

        assertThat(firstPage.totalCount()).isEqualTo(3);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("collections||0");
        assertThat(firstPage.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceCategory()).isEqualTo("products");
            assertThat(item.documentType()).isEqualTo("product");
            assertThat(item.title()).isEqualTo("Trail Shoe");
            assertThat(item.content()).contains("Average rating: 4.7 / 5 from 64 reviews (Judge.me).");
            assertThat(item.content()).contains("Materials: Breathable mesh upper");
            assertThat(item.content()).contains("Fit notes: Runs true to size");
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
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);

        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com"))
            .thenReturn(Optional.of(acquisition(policiesOnlyStore())));
        when(billingService.catalogProductCap("alpha.myshopify.com", "access-token")).thenReturn(null);
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
            new ShopifyBridgeVectorizationSourceService(installCredentialService, shopifyAdminGraphqlClient, billingService);

        ShopifyBridgeVectorizationSourcePageResponse firstPage = service.page("alpha.myshopify.com", "support-policy", null, 1);
        ShopifyBridgeVectorizationSourcePageResponse secondPage = service.page("alpha.myshopify.com", "support-policy", "policies|1|1", 1);

        assertThat(firstPage.totalCount()).isEqualTo(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("policies|1|1");
        assertThat(firstPage.items().getFirst().policyType()).isEqualTo("REFUND_POLICY");

        assertThat(secondPage.totalCount()).isEqualTo(2);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.items().getFirst().policyType()).isEqualTo("PRIVACY_POLICY");
    }

    @Test
    void pageReturnsPublishedArticlesForSupportPolicyEntityType() {
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);

        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com"))
            .thenReturn(Optional.of(acquisition(articlesOnlyStore())));
        when(billingService.catalogProductCap("alpha.myshopify.com", "access-token")).thenReturn(null);
        when(shopifyAdminGraphqlClient.execute(
            eq("alpha.myshopify.com"),
            eq("access-token"),
            contains("ArticlesVectorizationPage"),
            anyMap()
        )).thenReturn(Map.of(
            "data",
            Map.of(
                "articles",
                Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(
                        Map.of("node", articleNode(
                            "gid://shopify/Article/1",
                            "Shipping guide",
                            "shipping-guide",
                            "<p>Ships in two days.</p>",
                            "<p>Fast shipping.</p>",
                            "2026-04-22T12:00:00Z",
                            "2026-04-22T12:05:00Z"
                        )),
                        Map.of("node", articleNode(
                            "gid://shopify/Article/2",
                            "Draft article",
                            "draft-article",
                            "<p>Not published.</p>",
                            "<p>Draft.</p>",
                            "2026-04-22T12:06:00Z",
                            null
                        ))
                    )
                )
            )
        ));

        ShopifyBridgeVectorizationSourceService service =
            new ShopifyBridgeVectorizationSourceService(installCredentialService, shopifyAdminGraphqlClient, billingService);

        ShopifyBridgeVectorizationSourcePageResponse page = service.page("alpha.myshopify.com", "support-policy", null, 25);

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceCategory()).isEqualTo("articles");
            assertThat(item.documentType()).isEqualTo("article");
            assertThat(item.title()).isEqualTo("Shipping guide");
            assertThat(item.storefrontUrl()).isEqualTo("https://alpha.myshopify.com/blogs/news/shipping-guide");
        });
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

    private Map<String, Object> articleNode(String id,
                                            String title,
                                            String handle,
                                            String body,
                                            String summary,
                                            String updatedAt,
                                            String publishedAt) {
        LinkedHashMap<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("title", title);
        node.put("handle", handle);
        node.put("body", body);
        node.put("summary", summary);
        node.put("updatedAt", updatedAt);
        node.put("publishedAt", publishedAt);
        node.put("blog", Map.of("title", "News", "handle", "news"));
        node.put("author", Map.of("name", "Loom Team"));
        return node;
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

    private ShopifyBridgeStoreSummary articlesOnlyStore() {
        return new ShopifyBridgeStoreSummary(
            "shp-3",
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
