package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeInstallOverview;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeStoreOverview;
import com.ai.fabric.product.shopify.bridge.diagnostics.service.ShopifyBridgeDiagnosticsService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.service-ref=shopify-bridge-test",
    "shopify.bridge.platform-base-url=https://platform.example.com",
    "shopify.bridge.platform-admin-api-key=platform-admin-key"
})
@AutoConfigureMockMvc
class ShopifyBridgeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyBridgeDiagnosticsService diagnosticsService;

    @MockBean
    private ShopifyBridgeStoreAdminService storeAdminService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void adminOverviewRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOverviewReturnsDiagnosticsWhenApiKeyMatches() throws Exception {
        when(diagnosticsService.overview()).thenReturn(new ShopifyBridgeOverviewResponse(
            "Shopify Bridge Service",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://platform.example.com",
            "https://bridge.example.com",
            true,
            "READY",
            Instant.parse("2026-04-18T10:00:00Z"),
            new ShopifyBridgeInstallOverview(10, 8, 2, 7, Instant.parse("2026-04-18T10:10:00Z"), Instant.parse("2026-04-18T09:00:00Z")),
            new ShopifyBridgeStoreOverview("READY", "Platform store mappings resolved successfully.", 6, 3, 2, 1, 1, Instant.parse("2026-04-18T10:15:00Z")),
            List.of("managed-service-health"),
            List.of("shopify-webhook-ingestion")
        ));
        mockMvc.perform(get("/api/admin/overview").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceRef").value("shopify-bridge-test"))
            .andExpect(jsonPath("$.platformBaseUrl").value("https://platform.example.com"))
            .andExpect(jsonPath("$.adminApiKeyConfigured").value(true))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.installs.totalCount").value(10))
            .andExpect(jsonPath("$.stores.readyForGoLiveCount").value(3));
    }

    @Test
    void adminStoresAreReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.listStores()).thenReturn(List.of(sampleStore()));

        mockMvc.perform(get("/api/admin/stores").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$[0].productServiceRef").value("shopify-bridge-test"));
    }

    @Test
    void adminBootstrapUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.bootstrap("alpha.myshopify.com")).thenReturn(new ShopifyBridgeStoreBootstrapResponse(
            "alpha.myshopify.com",
            "cust-1",
            "dep-1",
            "consumer-1",
            true,
            true,
            true,
            List.of("mkp-template-commerce-shell"),
            sampleStore()
        ));

        mockMvc.perform(post("/api/admin/stores/alpha.myshopify.com/bootstrap").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.createdDeployment").value(true))
            .andExpect(jsonPath("$.store.consumerId").value("consumer-1"));
    }

    @Test
    void adminSourcePreflightUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordSourcePreflight(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordSourcePreflightRequest.class)
        )).thenReturn(sampleStore());

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/source-preflight")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"categories":[{"category":"products","enabled":true,"status":"READY","itemCount":120,"message":"Products reachable"}]}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    @Test
    void adminSyncStatusUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordSyncStatus(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordSyncStatusRequest.class)
        )).thenReturn(sampleStore());

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/sync-status")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"status":"SYNCED","mode":"FULL","documentCount":128,"message":"Initial import completed"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    @Test
    void adminWidgetStatusUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordWidgetStatus(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordWidgetStatusRequest.class)
        )).thenReturn(sampleStore());

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/widget-status")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"status":"ENABLED","channel":"THEME_APP_EXTENSION","message":"Theme embed enabled"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    private ShopifyBridgeStoreSummary sampleStore() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-test",
            "Shopify Bridge Test",
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
            "PLATFORM_BOOTSTRAPPED",
            true,
            true,
            true,
            true,
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
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }
}
