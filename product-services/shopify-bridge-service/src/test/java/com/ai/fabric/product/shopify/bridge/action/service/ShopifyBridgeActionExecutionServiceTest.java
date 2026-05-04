package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantResponse;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopifyBridgeActionExecutionServiceTest {

    @Test
    void listProductsReturnsProductItems() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenReturn(Map.of(
            "data", Map.of(
                "products", Map.of(
                    "edges", List.of(
                        Map.of(
                            "node", Map.of(
                                "id", "gid://shopify/Product/1",
                                "title", "Travel Bag",
                                "handle", "travel-bag",
                                "descriptionHtml", "<p>Carry on bag</p>",
                                "vendor", "Loom",
                                "productType", "Bags",
                                "metafields", Map.of(
                                    "edges", List.of(
                                        Map.of("node", Map.of(
                                            "namespace", "judgeme",
                                            "key", "rating",
                                            "type", "number_decimal",
                                            "value", "4.9"
                                        )),
                                        Map.of("node", Map.of(
                                            "namespace", "judgeme",
                                            "key", "review_count",
                                            "type", "number_integer",
                                            "value", "215"
                                        ))
                                    )
                                ),
                                "updatedAt", "2026-04-19T00:00:00Z",
                                "variants", Map.of(
                                    "nodes", List.of(
                                        Map.of(
                                            "id", "gid://shopify/ProductVariant/11",
                                            "title", "Black",
                                            "sku", "SKU-1",
                                            "availableForSale", true,
                                            "inventoryQuantity", 5
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("list_products", Map.of(), null, Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Products");
        assertThat(result.data()).containsEntry("count", 1);
        assertThat(((List<?>) result.data().get("items"))).hasSize(1);
        Map<?, ?> firstItem = (Map<?, ?>) ((List<?>) result.data().get("items")).getFirst();
        assertThat(firstItem.get("reviewSignalsPresent")).isEqualTo(true);
        assertThat(firstItem.get("reviewProvider")).isEqualTo("Judge.me");
        assertThat(firstItem.get("reviewAverage")).isEqualTo("4.9");
        assertThat(firstItem.get("reviewCount")).isEqualTo(215);
    }

    @Test
    void listProductsFallsBackToExtractedCatalogTermForNaturalLanguageQuery() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);
        List<String> attemptedQueries = new ArrayList<>();

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenAnswer(invocation -> {
            Map<?, ?> variables = invocation.getArgument(3);
            String query = variables.get("query") == null ? null : variables.get("query").toString();
            attemptedQueries.add(query);
            return "snowboard".equals(query) ? productsPayload() : emptyProductsPayload();
        });

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "list_products",
                Map.of("query", "The Collection Snowboard: Liquid and similar snowboards"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Products");
        assertThat(result.data()).containsEntry("count", 3);
        assertThat(attemptedQueries).contains("The Collection Snowboard: Liquid and similar snowboards", "snowboard");
        assertThat(result.data()).containsEntry("productFallbackToRecentCatalog", false);
    }

    @Test
    void listProductsAppliesTypedProductFilters() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenReturn(Map.of(
            "data", Map.of(
                "products", Map.of(
                    "edges", List.of(
                        productEdge("gid://shopify/Product/1", "Travel Bag", "travel-bag", "Carry on bag", "Loom", "Bags", "2026-04-19T00:00:00Z", "gid://shopify/ProductVariant/11", "Black", "SKU-1", true, 5, "99.00"),
                        productEdge("gid://shopify/Product/2", "Budget Bag", "budget-bag", "Unavailable bag", "Loom", "Bags", "2026-04-19T00:00:00Z", "gid://shopify/ProductVariant/22", "Grey", "SKU-2", false, 0, "79.00"),
                        productEdge("gid://shopify/Product/3", "Premium Bag", "premium-bag", "Premium bag", "Loom", "Bags", "2026-04-19T00:00:00Z", "gid://shopify/ProductVariant/33", "White", "SKU-3", true, 3, "129.00")
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "list_products",
                Map.of("query", "bags", "maxPrice", 100, "availableOnly", true),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("count", 1);
        List<?> items = (List<?>) result.data().get("items");
        assertThat(((Map<?, ?>) items.getFirst()).get("title")).isEqualTo("Travel Bag");
        @SuppressWarnings("unchecked")
        Map<String, Object> filtersApplied = (Map<String, Object>) result.data().get("filtersApplied");
        assertThat(filtersApplied)
            .containsEntry("maxPrice", 100.0)
            .containsEntry("availableOnly", true);
    }

    @Test
    void checkAvailabilityRequiresSku() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("check_availability", Map.of(), null, Map.of())
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(result.message()).contains("params.sku");
    }

    @Test
    void checkAvailabilityFallsBackToTitleLookupWhenPlannerSuppliesTitleAsSku() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any()))
            .thenReturn(Map.of(
                "data", Map.of(
                    "products", Map.of(
                        "edges", List.of(
                            productEdge(
                                "gid://shopify/Product/99",
                                "The Minimal Snowboard",
                                "the-minimal-snowboard",
                                "",
                                "Hydrogen Vendor",
                                "",
                                "2026-04-19T00:00:00Z",
                                "gid://shopify/ProductVariant/99",
                                "Default Title",
                                "",
                                true,
                                4,
                                "885.95"
                            )
                        )
                    )
                )
            ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "check_availability",
                Map.of("sku", "The Minimal Snowboard"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Availability");
        assertThat(result.data()).containsEntry("lookupMethod", "TITLE_OR_HANDLE");
        assertThat(result.data()).containsEntry("productTitle", "The Minimal Snowboard");
        assertThat(result.data()).containsEntry("productHandle", "the-minimal-snowboard");
        assertThat(result.data()).containsEntry("available", true);
        assertThat(result.data()).containsEntry("inventoryQuantity", 4);
    }

    @Test
    void unsupportedActionReturnsFailure() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("delete_everything", Map.of(), null, Map.of())
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACTION_NOT_SUPPORTED");
    }

    @Test
    void shopifySearchCatalogRoutesThroughMcpAdapterWithoutAdminGraphql() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyStorefrontGovernedActionService governedActionService = mock(ShopifyStorefrontGovernedActionService.class);
        ShopifyStorefrontMcpActionAdapter mcpActionAdapter = mock(ShopifyStorefrontMcpActionAdapter.class);
        ShopifyBridgeCredentialAcquisition acquisition = acquisition("alpha.myshopify.com");
        ShopifyBridgeActionExecuteRequest request = new ShopifyBridgeActionExecuteRequest(
            "shopify_search_catalog",
            Map.of("query", "coffee"),
            null,
            Map.of("sessionId", "shopper-session-1")
        );
        ShopifyBridgeActionResult adapterResult = ShopifyBridgeActionResult.ok(
            "Catalog search",
            Map.of(
                "adapterType", "mcp-tool",
                "mcpServerRef", "shopify-storefront-ucp",
                "mcpToolName", "search_catalog",
                "evidenceType", "SHOPIFY_MCP_TOOL_RESULT"
            )
        );
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            governedActionService,
            mcpActionAdapter
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition));
        when(mcpActionAdapter.searchCatalog(acquisition, request)).thenReturn(adapterResult);

        ShopifyBridgeActionResult result = service.execute("alpha.myshopify.com", request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpToolName", "search_catalog");
        assertThat(result.data()).containsEntry("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
        verify(mcpActionAdapter).searchCatalog(acquisition, request);
        verifyNoInteractions(graphqlClient, governedActionService);
    }

    @Test
    void shopifySearchPoliciesRoutesThroughMcpAdapterWithoutAdminGraphql() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyStorefrontGovernedActionService governedActionService = mock(ShopifyStorefrontGovernedActionService.class);
        ShopifyStorefrontMcpActionAdapter mcpActionAdapter = mock(ShopifyStorefrontMcpActionAdapter.class);
        ShopifyBridgeCredentialAcquisition acquisition = acquisition("alpha.myshopify.com");
        ShopifyBridgeActionExecuteRequest request = new ShopifyBridgeActionExecuteRequest(
            "shopify_search_policies",
            Map.of("query", "return policy"),
            null,
            Map.of("sessionId", "shopper-session-1")
        );
        ShopifyBridgeActionResult adapterResult = ShopifyBridgeActionResult.ok(
            "Policy search",
            Map.of(
                "adapterType", "mcp-tool",
                "mcpServerRef", "shopify-storefront",
                "mcpToolName", "search_shop_policies_and_faqs",
                "evidenceType", "SHOPIFY_MCP_TOOL_RESULT"
            )
        );
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            governedActionService,
            mcpActionAdapter
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition));
        when(mcpActionAdapter.searchPolicies(acquisition, request)).thenReturn(adapterResult);

        ShopifyBridgeActionResult result = service.execute("alpha.myshopify.com", request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpToolName", "search_shop_policies_and_faqs");
        assertThat(result.data()).containsEntry("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
        verify(mcpActionAdapter).searchPolicies(acquisition, request);
        verifyNoInteractions(graphqlClient, governedActionService);
    }

    @Test
    void shopifyGetProductDetailsRoutesThroughMcpAdapterWithoutAdminGraphql() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyStorefrontGovernedActionService governedActionService = mock(ShopifyStorefrontGovernedActionService.class);
        ShopifyStorefrontMcpActionAdapter mcpActionAdapter = mock(ShopifyStorefrontMcpActionAdapter.class);
        ShopifyBridgeCredentialAcquisition acquisition = acquisition("alpha.myshopify.com");
        ShopifyBridgeActionExecuteRequest request = new ShopifyBridgeActionExecuteRequest(
            "shopify_get_product_details",
            Map.of("product_id", "gid://shopify/Product/123"),
            null,
            Map.of("sessionId", "shopper-session-1")
        );
        ShopifyBridgeActionResult adapterResult = ShopifyBridgeActionResult.ok(
            "Product details",
            Map.of(
                "adapterType", "mcp-tool",
                "mcpServerRef", "shopify-storefront",
                "mcpToolName", "get_product_details",
                "evidenceType", "SHOPIFY_MCP_TOOL_RESULT"
            )
        );
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            governedActionService,
            mcpActionAdapter
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition));
        when(mcpActionAdapter.getProductDetails(acquisition, request)).thenReturn(adapterResult);

        ShopifyBridgeActionResult result = service.execute("alpha.myshopify.com", request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("adapterType", "mcp-tool");
        assertThat(result.data()).containsEntry("mcpToolName", "get_product_details");
        assertThat(result.data()).containsEntry("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
        verify(mcpActionAdapter).getProductDetails(acquisition, request);
        verifyNoInteractions(graphqlClient, governedActionService);
    }

    @Test
    void getPolicyFiltersFetchedPoliciesByQuery() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any())).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/1",
                            "title", "Refund Policy",
                            "type", "REFUND_POLICY",
                            "body", "<p>Refunds within 30 days</p>",
                            "url", "https://alpha.myshopify.com/policies/refund-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        ),
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/2",
                            "title", "Shipping Policy",
                            "type", "SHIPPING_POLICY",
                            "body", "<p>Ships in 2-3 days</p>",
                            "url", "https://alpha.myshopify.com/policies/shipping-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("get_policy", Map.of("query", "refund", "limit", 1), null, Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Policies");
        assertThat(result.data()).containsEntry("count", 1);
        assertThat(result.data()).containsEntry("query", "refund");
        List<?> items = (List<?>) result.data().get("items");
        assertThat(items).hasSize(1);
        Map<?, ?> first = (Map<?, ?>) items.getFirst();
        assertThat(first.get("title")).isEqualTo("Refund Policy");
        assertThat(first.get("body")).isEqualTo("Refunds within 30 days");
    }

    @Test
    void getPolicyMatchesPolicyIntentInsidePlannerNaturalLanguageQuery() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any())).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/1",
                            "title", "Refund Policy",
                            "type", "REFUND_POLICY",
                            "body", "<p>Refunds within 30 days</p>",
                            "url", "https://alpha.myshopify.com/policies/refund-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        ),
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/2",
                            "title", "Shipping Policy",
                            "type", "SHIPPING_POLICY",
                            "body", "<p>Ships in 2-3 days</p>",
                            "url", "https://alpha.myshopify.com/policies/shipping-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "get_policy",
                Map.of("query", "return policy for product handle the-collection-snowboard-liquid"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Policies");
        assertThat(result.data()).containsEntry("count", 1);
        List<?> items = (List<?>) result.data().get("items");
        assertThat(items).hasSize(1);
        assertThat(((Map<?, ?>) items.getFirst()).get("title")).isEqualTo("Refund Policy");
    }

    @Test
    void getPolicyDoesNotReturnUnrelatedPolicyBodyForKnownIntent() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any())).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/1",
                            "title", "Privacy Policy",
                            "type", "PRIVACY_POLICY",
                            "body", "<p>Customers may return to this page to review privacy settings.</p>",
                            "url", "https://alpha.myshopify.com/policies/privacy-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "get_policy",
                Map.of("query", "return policy for product handle the-collection-snowboard-liquid"),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("No policies found.");
        assertThat(result.data()).containsEntry("count", 0);
        assertThat((List<?>) result.data().get("items")).isEmpty();
    }

    @Test
    void getPolicyStillAllowsBodySearchForUnknownPolicyQueries() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any())).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/1",
                            "title", "Store Policy",
                            "type", "LEGAL_NOTICE",
                            "body", "<p>Safety notices are provided by the manufacturer.</p>",
                            "url", "https://alpha.myshopify.com/policies/store-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("get_policy", Map.of("query", "safety notices"), null, Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Policies");
        assertThat(result.data()).containsEntry("count", 1);
        List<?> items = (List<?>) result.data().get("items");
        assertThat(items).hasSize(1);
        assertThat(((Map<?, ?>) items.getFirst()).get("title")).isEqualTo("Store Policy");
    }

    @Test
    void getPolicyAcceptsPolicyTypeAndOmitsNullQuery() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any())).thenReturn(Map.of(
            "data", Map.of(
                "shop", Map.of(
                    "shopPolicies", List.of(
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/1",
                            "title", "Refund Policy",
                            "type", "REFUND_POLICY",
                            "body", "<p>Refunds within 30 days</p>",
                            "url", "https://alpha.myshopify.com/policies/refund-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        ),
                        Map.of(
                            "id", "gid://shopify/ShopPolicy/2",
                            "title", "Shipping Policy",
                            "type", "SHIPPING_POLICY",
                            "body", "<p>Ships in 2-3 days</p>",
                            "url", "https://alpha.myshopify.com/policies/shipping-policy",
                            "updatedAt", "2026-04-19T00:00:00Z"
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult typedResult = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("get_policy", Map.of("policyType", "refund"), null, Map.of())
        );

        assertThat(typedResult.success()).isTrue();
        assertThat(typedResult.data()).containsEntry("query", "refund");
        List<?> typedItems = (List<?>) typedResult.data().get("items");
        assertThat(typedItems).hasSize(1);
        assertThat(((Map<?, ?>) typedItems.getFirst()).get("title")).isEqualTo("Refund Policy");

        ShopifyBridgeActionResult unfilteredResult = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("get_policy", Map.of("limit", 2), null, Map.of())
        );

        assertThat(unfilteredResult.success()).isTrue();
        assertThat(unfilteredResult.data()).doesNotContainKey("query");
        assertThat((List<?>) unfilteredResult.data().get("items")).hasSize(2);
    }

    @Test
    void relationshipQueryIsDisabledForDirectBridgeExecution() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "relationship_query",
                Map.of("query", "compare bags with similar products", "entityTypes", List.of("product"), "limit", 2),
                null,
                Map.of()
            )
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACTION_NOT_SUPPORTED");
        assertThat(result.message()).isEqualTo("Action is not supported.");
        verifyNoInteractions(graphqlClient);
    }

    @Test
    void addProductToCartCreatesGovernedCommerceGrant() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyStorefrontGovernedActionService governedActionService = mock(ShopifyStorefrontGovernedActionService.class);
        AtomicReference<ShopifyStorefrontGovernedActionGrantRequest> grantRequest = new AtomicReference<>();
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            governedActionService
        );

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenReturn(Map.of(
            "data", Map.of(
                "products", Map.of(
                    "edges", List.of(
                        productEdge(
                            "gid://shopify/Product/99",
                            "The Minimal Snowboard",
                            "the-minimal-snowboard",
                            "",
                            "Hydrogen Vendor",
                            "",
                            "2026-04-19T00:00:00Z",
                            "gid://shopify/ProductVariant/123456789",
                            "Default Title",
                            "",
                            true,
                            4,
                            "885.95"
                        )
                    )
                )
            )
        ));
        when(governedActionService.grant(eq("alpha.myshopify.com"), any(), eq("shopper-session-1")))
            .thenAnswer(invocation -> {
                grantRequest.set(invocation.getArgument(1));
                return new ShopifyStorefrontGovernedActionGrantResponse(
                    "sga-1",
                    "signed-token",
                    "ADD_TO_CART",
                    "guided-commerce",
                    "CART_ADD",
                    "123456789",
                    2,
                    null,
                    null,
                    true,
                    Instant.parse("2026-04-23T12:05:00Z"),
                    "Guided add-to-cart approval granted."
                );
            });

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "add_product_to_cart",
                Map.of("query", "The Minimal Snowboard", "quantity", 2, "surfaceId", "max-mode"),
                "idem-1",
                Map.of("sessionId", "shopper-session-1")
            )
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Guided add-to-cart grant created.");
        assertThat(result.data()).containsEntry("auditId", "sga-1");
        assertThat(result.data()).containsEntry("operationKind", "CART_ADD");
        assertThat(result.data()).containsEntry("variantId", "123456789");
        assertThat(result.data()).containsEntry("clientCompletionRequired", true);
        assertThat(grantRequest.get()).isNotNull();
        assertThat(grantRequest.get().actionType()).isEqualTo("ADD_TO_CART");
        assertThat(grantRequest.get().variantId()).isEqualTo("123456789");
        assertThat(grantRequest.get().requestedQuantity()).isEqualTo(2);
        assertThat(grantRequest.get().confirmationAccepted()).isTrue();

        when(governedActionService.grant(eq("alpha.myshopify.com"), any(), eq("param-session-1")))
            .thenReturn(new ShopifyStorefrontGovernedActionGrantResponse(
                "sga-2",
                "signed-token-2",
                "ADD_TO_CART",
                "guided-commerce",
                "CART_ADD",
                "123456789",
                1,
                null,
                null,
                true,
                Instant.parse("2026-04-23T12:06:00Z"),
                "Guided add-to-cart approval granted."
            ));

        ShopifyBridgeActionResult fallbackSessionResult = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest(
                "add_product_to_cart",
                Map.of("query", "The Minimal Snowboard", "shopperSessionId", "param-session-1"),
                null,
                null
            )
        );

        assertThat(fallbackSessionResult.success()).isTrue();
        assertThat(fallbackSessionResult.data()).containsEntry("auditId", "sga-2");
    }

    @Test
    void addProductToCartRequiresShopperSessionForAuditBinding() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = service(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenReturn(Map.of(
            "data", Map.of(
                "products", Map.of(
                    "edges", List.of(
                        productEdge(
                            "gid://shopify/Product/99",
                            "The Minimal Snowboard",
                            "the-minimal-snowboard",
                            "",
                            "Hydrogen Vendor",
                            "",
                            "2026-04-19T00:00:00Z",
                            "gid://shopify/ProductVariant/123456789",
                            "Default Title",
                            "",
                            true,
                            4,
                            "885.95"
                        )
                    )
                )
            )
        ));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("add_product_to_cart", Map.of("query", "The Minimal Snowboard"), null, Map.of())
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SHOPPER_SESSION_REQUIRED");
    }

    private ShopifyBridgeCredentialAcquisition acquisition(String shopDomain) {
        return new ShopifyBridgeCredentialAcquisition(
            new ShopifyBridgeStoreSummary(
                "shp-1",
                shopDomain,
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
                "SYNCED",
                "READY",
                "ENABLED",
                "LIVE",
                true,
                true,
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
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:00:00Z")
            ),
            new ShopifyTokenExchangeMaterial(
                "token-1",
                null,
                null,
                null,
                "read_products,read_content,read_legal_policies",
                false
            )
        );
    }

    private ShopifyBridgeActionExecutionService service(ShopifyBridgeInstallCredentialService credentialService,
                                                        ShopifyAdminGraphqlClient graphqlClient) {
        return new ShopifyBridgeActionExecutionService(
            credentialService,
            graphqlClient,
            mock(ShopifyStorefrontGovernedActionService.class)
        );
    }

    private Map<String, Object> productsPayload() {
        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(productEdge(
            "gid://shopify/Product/1",
            "Travel Bag",
            "travel-bag",
            "Carry on bag",
            "Loom",
            "Bags",
            "2026-04-19T00:00:00Z",
            "gid://shopify/ProductVariant/11",
            "Black",
            "SKU-1",
            true,
            5,
            "99.00"
        ));
        edges.add(productEdge(
            "gid://shopify/Product/2",
            "Commuter Bag",
            "commuter-bag",
            "Everyday commuter bag",
            "Loom",
            "Bags",
            "2026-04-19T00:00:00Z",
            "gid://shopify/ProductVariant/22",
            "Grey",
            "SKU-2",
            true,
            9,
            "109.00"
        ));
        edges.add(productEdge(
            "gid://shopify/Product/3",
            "Desk Lamp",
            "desk-lamp",
            "Office lamp",
            "Loom Home",
            "Lighting",
            "2026-04-19T00:00:00Z",
            "gid://shopify/ProductVariant/33",
            "White",
            "SKU-3",
            true,
            3,
            "89.00"
        ));
        return Map.of(
            "data", Map.of(
                "products", Map.of(
                    "edges", edges
                )
            )
        );
    }

    private Map<String, Object> emptyProductsPayload() {
        return Map.of(
            "data", Map.of(
                "products", Map.of(
                    "edges", List.of()
                )
            )
        );
    }

    private Map<String, Object> productEdge(String id,
                                            String title,
                                            String handle,
                                            String descriptionHtml,
                                            String vendor,
                                            String productType,
                                            String updatedAt,
                                            String variantId,
                                            String variantTitle,
                                            String sku,
                                            boolean availableForSale,
                                            int inventoryQuantity,
                                            String price) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("id", variantId);
        variant.put("title", variantTitle);
        variant.put("sku", sku);
        variant.put("availableForSale", availableForSale);
        variant.put("inventoryQuantity", inventoryQuantity);
        variant.put("price", price);

        Map<String, Object> variants = new LinkedHashMap<>();
        variants.put("nodes", List.of(variant));

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("title", title);
        node.put("handle", handle);
        node.put("descriptionHtml", "<p>" + descriptionHtml + "</p>");
        node.put("vendor", vendor);
        node.put("productType", productType);
        if ("SKU-1".equals(sku)) {
            node.put("metafields", Map.of(
                "edges", List.of(
                    Map.of("node", Map.of(
                        "namespace", "judgeme",
                        "key", "rating",
                        "type", "number_decimal",
                        "value", "4.8"
                    )),
                    Map.of("node", Map.of(
                        "namespace", "judgeme",
                        "key", "review_count",
                        "type", "number_integer",
                        "value", "128"
                    ))
                )
            ));
        } else {
            node.put("metafields", Map.of("edges", List.of()));
        }
        node.put("updatedAt", updatedAt);
        node.put("variants", variants);

        return Map.of("node", node);
    }
}
