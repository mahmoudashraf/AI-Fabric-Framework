package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSyncStoreDocumentsRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeStoreSyncServiceTest {

    @Test
    void syncCollectsEnabledStoreDocumentsAndCallsPlatformSync() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService service = new ShopifyBridgeStoreSyncService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionProductsSync($cursor: String) {
          products(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                title
                handle
                descriptionHtml
                vendor
                productType
                tags
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
                updatedAt
              }
            }
          }
        }
        """), eq(cursorVariables(null)))).thenReturn(Map.of(
            "data", Map.of(
                "products", Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(Map.of("node", Map.of(
                        "id", "gid://shopify/Product/1",
                        "title", "Travel Backpack",
                        "handle", "travel-backpack",
                        "descriptionHtml", "<p>Carry-on sized backpack.</p>",
                        "vendor", "Loom",
                        "productType", "Bag",
                        "tags", List.of("travel", "carry-on"),
                        "metafields", Map.of(
                            "edges", List.of(
                                Map.of("node", Map.of(
                                    "namespace", "custom",
                                    "key", "materials",
                                    "type", "multi_line_text_field",
                                    "value", "Recycled nylon"
                                )),
                                Map.of("node", Map.of(
                                    "namespace", "custom",
                                    "key", "fit_notes",
                                    "type", "single_line_text_field",
                                    "value", "Designed for airline personal-item sizing."
                                ))
                            )
                        ),
                        "updatedAt", "2026-04-18T12:00:00Z"
                    )))
                )
            )
        ));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionPoliciesSync {
          shop {
            shopPolicies {
              id
              title
              type
              body
              url
              updatedAt
            }
          }
        }
        """))).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(Map.of(
                        "id", "gid://shopify/ShopPolicy/1",
                        "title", "Refund policy",
                        "type", "REFUND_POLICY",
                        "body", "<p>Refund within 30 days.</p>",
                        "url", "https://alpha.myshopify.com/policies/refund-policy",
                        "updatedAt", "2026-04-18T12:05:00Z"
                    ))
                )
            )
        ));
        when(platformClient.syncDocuments(eq("alpha.myshopify.com"), any())).thenReturn(store(true, false, false, true));
        when(platformClient.recordSyncStatus(eq("alpha.myshopify.com"), any())).thenReturn(store(true, false, false, true));

        ShopifyBridgeStoreSummary response = service.sync(acquisition(store(true, false, false, true)));

        ArgumentCaptor<ShopifyBridgeSyncStoreDocumentsRequest> syncCaptor =
            ArgumentCaptor.forClass(ShopifyBridgeSyncStoreDocumentsRequest.class);
        verify(platformClient).syncDocuments(eq("alpha.myshopify.com"), syncCaptor.capture());
        assertThat(syncCaptor.getValue().documents()).hasSize(2);
        assertThat(syncCaptor.getValue().documents())
            .extracting(document -> document.sourceCategory() + ":" + document.entityType())
            .containsExactly("products:product", "policies:support-policy");
        assertThat(syncCaptor.getValue().documents().getFirst().content())
            .contains("Materials: Recycled nylon")
            .contains("Fit notes: Designed for airline personal-item sizing.");
        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");

        ArgumentCaptor<ShopifyBridgeRecordSyncStatusRequest> statusCaptor =
            ArgumentCaptor.forClass(ShopifyBridgeRecordSyncStatusRequest.class);
        verify(platformClient).recordSyncStatus(eq("alpha.myshopify.com"), statusCaptor.capture());
        assertThat(statusCaptor.getValue().status()).isEqualTo("SYNCED");
        assertThat(statusCaptor.getValue().documentCount()).isEqualTo(2);
    }

    @Test
    void syncMapsPagesIntoSupportPolicyDocuments() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService service = new ShopifyBridgeStoreSyncService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionPagesSync($cursor: String) {
          pages(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                title
                handle
                body
                updatedAt
              }
            }
          }
        }
        """), eq(cursorVariables(null)))).thenReturn(Map.of(
            "data", Map.of(
                "pages", Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(Map.of("node", Map.of(
                        "id", "gid://shopify/Page/1",
                        "title", "Shipping FAQ",
                        "handle", "shipping-faq",
                        "body", "<p>Shipping takes 3 to 5 business days.</p>",
                        "updatedAt", "2026-04-18T12:00:00Z"
                    )))
                )
            )
        ));
        when(platformClient.syncDocuments(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, true, false));
        when(platformClient.recordSyncStatus(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, true, false));

        service.sync(acquisition(store(false, false, true, false)));

        ArgumentCaptor<ShopifyBridgeSyncStoreDocumentsRequest> syncCaptor =
            ArgumentCaptor.forClass(ShopifyBridgeSyncStoreDocumentsRequest.class);
        verify(platformClient).syncDocuments(eq("alpha.myshopify.com"), syncCaptor.capture());
        assertThat(syncCaptor.getValue().documents()).hasSize(1);
        assertThat(syncCaptor.getValue().documents().getFirst().sourceCategory()).isEqualTo("pages");
        assertThat(syncCaptor.getValue().documents().getFirst().entityType()).isEqualTo("support-policy");
    }

    @Test
    void syncNormalizesAndBoundsShopifyPolicyBodies() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService service = new ShopifyBridgeStoreSyncService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        String repeatedClause = String.join(" ", Collections.nCopies(2_000, "policy-clause"));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionPoliciesSync {
          shop {
            shopPolicies {
              id
              title
              type
              body
              url
              updatedAt
            }
          }
        }
        """))).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(Map.of(
                        "id", "gid://shopify/ShopPolicy/1",
                        "title", "Privacy policy",
                        "type", "PRIVACY_POLICY",
                        "body", "<p>Last updated: {{ last_updated }}</p><p>{% if data_sale_opt_out_enabled %}" + repeatedClause + "</p>",
                        "url", "https://alpha.myshopify.com/policies/privacy-policy",
                        "updatedAt", "2026-04-18T12:05:00Z"
                    ))
                )
            )
        ));
        when(platformClient.syncDocuments(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, false, true));
        when(platformClient.recordSyncStatus(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, false, true));

        service.sync(acquisition(store(false, false, false, true)));

        ArgumentCaptor<ShopifyBridgeSyncStoreDocumentsRequest> syncCaptor =
            ArgumentCaptor.forClass(ShopifyBridgeSyncStoreDocumentsRequest.class);
        verify(platformClient).syncDocuments(eq("alpha.myshopify.com"), syncCaptor.capture());
        assertThat(syncCaptor.getValue().documents()).hasSize(1);
        assertThat(syncCaptor.getValue().documents().getFirst().content())
            .doesNotContain("{{")
            .doesNotContain("}}")
            .doesNotContain("{%")
            .doesNotContain("%}");
        assertThat(syncCaptor.getValue().documents().getFirst().content().length()).isLessThanOrEqualTo(5_600);
    }

    @Test
    void syncMapsPublishedArticlesIntoSupportPolicyDocuments() {
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService service = new ShopifyBridgeStoreSyncService(graphqlClient, platformClient, billingService);
        when(billingService.catalogProductCap("alpha.myshopify.com", "shpat_access")).thenReturn(null);

        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("shpat_access"), eq("""
        query ShopifyCompanionArticlesSync($cursor: String) {
          articles(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                title
                handle
                body
                summary
                updatedAt
                publishedAt
                blog {
                  title
                  handle
                }
                author {
                  name
                }
              }
            }
          }
        }
        """), eq(cursorVariables(null)))).thenReturn(Map.of(
            "data", Map.of(
                "articles", Map.of(
                    "pageInfo", pageInfo(false, null),
                    "edges", List.of(
                        Map.of("node", articleNode(
                            "gid://shopify/Article/1",
                            "Shipping guide",
                            "shipping-guide",
                            "<p>Shipping within 2 days.</p>",
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
        when(platformClient.syncDocuments(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, false, false, true));
        when(platformClient.recordSyncStatus(eq("alpha.myshopify.com"), any())).thenReturn(store(false, false, false, false, true));

        service.sync(acquisition(store(false, false, false, false, true)));

        ArgumentCaptor<ShopifyBridgeSyncStoreDocumentsRequest> syncCaptor =
            ArgumentCaptor.forClass(ShopifyBridgeSyncStoreDocumentsRequest.class);
        verify(platformClient).syncDocuments(eq("alpha.myshopify.com"), syncCaptor.capture());
        assertThat(syncCaptor.getValue().documents()).hasSize(1);
        assertThat(syncCaptor.getValue().documents().getFirst().sourceCategory()).isEqualTo("articles");
        assertThat(syncCaptor.getValue().documents().getFirst().entityType()).isEqualTo("support-policy");
        assertThat(syncCaptor.getValue().documents().getFirst().metadata())
            .containsEntry("documentType", "article")
            .containsEntry("storefrontUrl", "https://alpha.myshopify.com/blogs/news/shipping-guide");
    }

    private Map<String, Object> cursorVariables(String cursor) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("cursor", cursor);
        return variables;
    }

    private Map<String, Object> pageInfo(boolean hasNextPage, String endCursor) {
        LinkedHashMap<String, Object> pageInfo = new LinkedHashMap<>();
        pageInfo.put("hasNextPage", hasNextPage);
        pageInfo.put("endCursor", endCursor);
        return pageInfo;
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
            "ACTIVE",
            "consumer-1",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "READY",
            "NOT_ENABLED",
            "GO_LIVE_REQUESTED",
            productsEnabled,
            collectionsEnabled,
            pagesEnabled,
            policiesEnabled,
            articlesEnabled,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products",
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
