package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSourcePreflightCategorySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeSourcePreflightServiceTest {

    @Test
    void runCollectsEnabledCategoriesAndRecordsPreflight() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeSourcePreflightService service = new ShopifyBridgeSourcePreflightService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionProductsPreflight {
          productsCount(limit: null) {
            count
          }
          products(first: 12, sortKey: UPDATED_AT, reverse: true) {
            edges {
              node {
                id
                metafields(first: 12) {
                  edges {
                    node {
                      namespace
                      key
                      type
                      value
                    }
                  }
                }
              }
            }
          }
        }
        """))).thenReturn(Map.of(
            "data", Map.of(
                "productsCount", Map.of("count", 12),
                "products", Map.of(
                    "edges", List.of(
                        Map.of("node", productNodeWithMetafields(List.of(
                            metafield("judgeme", "rating", "number_decimal", "4.8"),
                            metafield("judgeme", "review_count", "number_integer", "27")
                        ))),
                        Map.of("node", productNodeWithMetafields(List.of(
                            metafield("custom", "subtitle", "single_line_text_field", "Layered comfort")
                        )))
                    )
                )
            )
        ));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionPagesPreflight {
          pagesCount(limit: null) {
            count
          }
        }
        """))).thenReturn(Map.of("data", Map.of("pagesCount", Map.of("count", 0))));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionPoliciesPreflight {
          shop {
            shopPolicies {
              id
            }
          }
        }
        """))).thenReturn(Map.of("data", Map.of("shop", Map.of("shopPolicies", List.of(Map.of("id", "p1"), Map.of("id", "p2"))))));
        when(platformClient.recordSourcePreflight(eq("alpha.myshopify.com"), any())).thenReturn(store(true, false, true, true));

        ShopifyBridgeStoreSummary response = service.run(acquisition(store(true, false, true, true)));

        ArgumentCaptor<ShopifyBridgeRecordSourcePreflightRequest> captor =
            ArgumentCaptor.forClass(ShopifyBridgeRecordSourcePreflightRequest.class);
        verify(platformClient).recordSourcePreflight(eq("alpha.myshopify.com"), captor.capture());
        List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories = captor.getValue().categories();
        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(categories).extracting(ShopifyBridgeStoreSourcePreflightCategorySummary::category)
            .containsExactly("products", "collections", "pages", "policies", "articles", "metaobjects");
        assertThat(categories).extracting(ShopifyBridgeStoreSourcePreflightCategorySummary::status)
            .containsExactly("READY", "PENDING", "READY", "READY", "PENDING", "PENDING");
        assertThat(categories).extracting(ShopifyBridgeStoreSourcePreflightCategorySummary::itemCount)
            .containsExactly(12, 0, 0, 2, 0, 0);
        assertThat(categories.getFirst().signals()).containsExactly("Judge.me");
    }

    @Test
    void runMarksBlockedCategoryWhenShopifyDeniesAccess() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeSourcePreflightService service = new ShopifyBridgeSourcePreflightService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionProductsPreflight {
          productsCount(limit: null) {
            count
          }
          products(first: 12, sortKey: UPDATED_AT, reverse: true) {
            edges {
              node {
                id
                metafields(first: 12) {
                  edges {
                    node {
                      namespace
                      key
                      type
                      value
                    }
                  }
                }
              }
            }
          }
        }
        """))).thenReturn(Map.of(
            "errors", List.of(Map.of("message", "Access denied for productsCount field. Required access scope."))
        ));
        when(platformClient.recordSourcePreflight(eq("alpha.myshopify.com"), any())).thenReturn(store(true, false, false, false));

        service.run(acquisition(store(true, false, false, false)));

        ArgumentCaptor<ShopifyBridgeRecordSourcePreflightRequest> captor =
            ArgumentCaptor.forClass(ShopifyBridgeRecordSourcePreflightRequest.class);
        verify(platformClient).recordSourcePreflight(eq("alpha.myshopify.com"), captor.capture());
        assertThat(captor.getValue().categories())
            .extracting(ShopifyBridgeStoreSourcePreflightCategorySummary::status)
            .containsExactly("BLOCKED", "PENDING", "PENDING", "PENDING", "PENDING", "PENDING");
    }

    @Test
    void runCountsOnlyPublishedArticlesWhenEnabled() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeSourcePreflightService service = new ShopifyBridgeSourcePreflightService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionArticlesPreflight($cursor: String) {
          articles(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                publishedAt
              }
            }
          }
        }
        """), eq(cursorVariables(null)))).thenReturn(Map.of(
            "data", Map.of(
                "articles", Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(
                        Map.of("node", preflightArticleNode("gid://shopify/Article/1", "2026-04-21T12:00:00Z")),
                        Map.of("node", preflightArticleNode("gid://shopify/Article/2", null)),
                        Map.of("node", preflightArticleNode("gid://shopify/Article/3", "2026-04-22T12:00:00Z"))
                    )
                )
            )
        ));
        when(platformClient.recordSourcePreflight(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, false, false, true));

        ShopifyBridgeStoreSummary response = service.run(acquisition(store(false, false, false, false, true)));

        ArgumentCaptor<ShopifyBridgeRecordSourcePreflightRequest> captor =
            ArgumentCaptor.forClass(ShopifyBridgeRecordSourcePreflightRequest.class);
        verify(platformClient).recordSourcePreflight(eq("alpha.myshopify.com"), captor.capture());
        List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories = captor.getValue().categories();
        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(categories).extracting(ShopifyBridgeStoreSourcePreflightCategorySummary::category)
            .containsExactly("products", "collections", "pages", "policies", "articles", "metaobjects");
        assertThat(categories.get(4).status()).isEqualTo("READY");
        assertThat(categories.get(4).itemCount()).isEqualTo(2);
    }

    @Test
    void runCapturesMetaobjectSignalsWhenDefinitionsExist() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeSourcePreflightService service = new ShopifyBridgeSourcePreflightService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionMetaobjectDefinitions($cursor: String) {
          metaobjectDefinitions(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                type
                name
                description
                displayNameKey
                metaobjectsCount
                access {
                  admin
                  storefront
                }
                fieldDefinitions {
                  key
                  name
                }
              }
            }
          }
        }
        """), eq(cursorVariables(null)))).thenReturn(Map.of(
            "data", Map.of(
                "metaobjectDefinitions", Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(
                        Map.of("node", metaobjectDefinitionNode("gid://shopify/MetaobjectDefinition/1", "faq", "FAQ", 8)),
                        Map.of("node", metaobjectDefinitionNode("gid://shopify/MetaobjectDefinition/2", "size_chart", "Size chart", 5)),
                        Map.of("node", metaobjectDefinitionNode("gid://shopify/MetaobjectDefinition/3", "returns", "Returns policy", 3)),
                        Map.of("node", metaobjectDefinitionNode("gid://shopify/MetaobjectDefinition/4", "care", "Care guide", 2))
                    )
                )
            )
        ));
        when(platformClient.recordSourcePreflight(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, false, false, false, true));

        ShopifyBridgeStoreSummary response = service.run(acquisition(store(false, false, false, false, false, true)));

        ArgumentCaptor<ShopifyBridgeRecordSourcePreflightRequest> captor =
            ArgumentCaptor.forClass(ShopifyBridgeRecordSourcePreflightRequest.class);
        verify(platformClient).recordSourcePreflight(eq("alpha.myshopify.com"), captor.capture());
        List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories = captor.getValue().categories();
        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(categories.get(5).signals()).containsExactly("FAQ (8)", "Size chart (5)", "Returns policy (3)", "+1 more types");
    }

    private Map<String, Object> cursorVariables(String cursor) {
        java.util.LinkedHashMap<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("cursor", cursor);
        return variables;
    }

    private Map<String, Object> pageInfo(boolean hasNextPage, String endCursor) {
        java.util.LinkedHashMap<String, Object> pageInfo = new java.util.LinkedHashMap<>();
        pageInfo.put("hasNextPage", hasNextPage);
        pageInfo.put("endCursor", endCursor);
        return pageInfo;
    }

    private Map<String, Object> preflightArticleNode(String id, String publishedAt) {
        java.util.LinkedHashMap<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("id", id);
        node.put("publishedAt", publishedAt);
        return node;
    }

    private Map<String, Object> productNodeWithMetafields(List<Map<String, Object>> metafields) {
        java.util.LinkedHashMap<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("id", "gid://shopify/Product/1");
        node.put("metafields", Map.of(
            "edges", metafields.stream()
                .map(item -> Map.of("node", item))
                .toList()
        ));
        return node;
    }

    private Map<String, Object> metafield(String namespace, String key, String type, String value) {
        java.util.LinkedHashMap<String, Object> metafield = new java.util.LinkedHashMap<>();
        metafield.put("namespace", namespace);
        metafield.put("key", key);
        metafield.put("type", type);
        metafield.put("value", value);
        return metafield;
    }

    private Map<String, Object> metaobjectDefinitionNode(String id, String type, String name, int count) {
        return Map.of(
            "id", id,
            "type", type,
            "name", name,
            "description", name + " content",
            "displayNameKey", "title",
            "metaobjectsCount", count,
            "access", Map.of("admin", "MERCHANT_READ", "storefront", "PUBLIC_READ"),
            "fieldDefinitions", List.of(Map.of("key", "title", "name", "Title"))
        );
    }

    private ShopifyBridgeCredentialAcquisition acquisition(ShopifyBridgeStoreSummary store) {
        return new ShopifyBridgeCredentialAcquisition(
            store,
            new ShopifyTokenExchangeMaterial(
                "shpat_access",
                "shprt_refresh",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            )
        );
    }

    private ShopifyBridgeStoreSummary store(boolean productsEnabled,
                                            boolean collectionsEnabled,
                                            boolean pagesEnabled,
                                            boolean policiesEnabled) {
        return store(productsEnabled, collectionsEnabled, pagesEnabled, policiesEnabled, false);
    }

    private ShopifyBridgeStoreSummary store(boolean productsEnabled,
                                            boolean collectionsEnabled,
                                            boolean pagesEnabled,
                                            boolean policiesEnabled,
                                            boolean articlesEnabled) {
        return store(productsEnabled, collectionsEnabled, pagesEnabled, policiesEnabled, articlesEnabled, false);
    }

    private ShopifyBridgeStoreSummary store(boolean productsEnabled,
                                            boolean collectionsEnabled,
                                            boolean pagesEnabled,
                                            boolean policiesEnabled,
                                            boolean articlesEnabled,
                                            boolean metaobjectsEnabled) {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "DRAFT",
            "consumer-1",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            productsEnabled,
            collectionsEnabled,
            pagesEnabled,
            policiesEnabled,
            articlesEnabled,
            metaobjectsEnabled,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }
}
