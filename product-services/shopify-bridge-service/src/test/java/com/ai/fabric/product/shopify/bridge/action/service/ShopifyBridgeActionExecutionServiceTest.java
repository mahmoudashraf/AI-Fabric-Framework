package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyBridgeActionExecutionServiceTest {

    @Test
    void listProductsReturnsProductItems() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(credentialService, graphqlClient);

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
    }

    @Test
    void checkAvailabilityRequiresSku() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(credentialService, graphqlClient);

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
    void unsupportedActionReturnsFailure() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("delete_everything", Map.of(), null, Map.of())
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACTION_NOT_SUPPORTED");
    }

    @Test
    void findSimilarProductsReturnsRankedMatches() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenReturn(productsPayload());

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("find_similar_products", Map.of("sku", "SKU-1", "limit", 2), null, Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Similar products");
        assertThat(result.data()).containsEntry("count", 2);
        assertThat(((Map<?, ?>) result.data().get("referenceProduct")).get("primarySku")).isEqualTo("SKU-1");
        List<?> items = (List<?>) result.data().get("items");
        assertThat(items).hasSize(2);
        Map<?, ?> first = (Map<?, ?>) items.getFirst();
        assertThat(((Map<?, ?>) first.get("product")).get("primarySku")).isEqualTo("SKU-2");
    }

    @Test
    void compareProductsReturnsStructuredHighlights() {
        ShopifyBridgeInstallCredentialService credentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyAdminGraphqlClient graphqlClient = mock(ShopifyAdminGraphqlClient.class);
        ShopifyBridgeActionExecutionService service = new ShopifyBridgeActionExecutionService(credentialService, graphqlClient);

        when(credentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("alpha.myshopify.com")));
        when(graphqlClient.execute(eq("alpha.myshopify.com"), eq("token-1"), any(), any())).thenReturn(productsPayload());

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyBridgeActionExecuteRequest("compare_products", Map.of("referenceSku", "SKU-1", "comparisonSku", "SKU-2"), null, Map.of())
        );

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Product comparison");
        assertThat(result.data().get("cheaperSku")).isEqualTo("SKU-1");
        assertThat(result.data().get("betterStockedSku")).isEqualTo("SKU-2");
        assertThat(((List<?>) result.data().get("keyDifferences"))).isNotEmpty();
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
        node.put("updatedAt", updatedAt);
        node.put("variants", variants);

        return Map.of("node", node);
    }
}
