package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeMerchantStoreServiceTest {

    @Test
    void sessionReturnsStoreWhenPresent() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            installRecordService,
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installRecordService.recordAuthenticatedSession(session(), "host-token")).thenReturn(new ShopifyInstallRecordSummary(
            "alpha.myshopify.com",
            "INSTALLED",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            "host-token",
            "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
            "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
            "read_products",
            Instant.parse("2026-04-18T01:00:00Z"),
            Instant.parse("2026-07-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            null
        ));

        var response = service.session(session(), "host-token");

        assertThat(response.installRecord()).isNotNull();
        assertThat(response.store()).isNotNull();
        assertThat(response.store().shopDomain()).isEqualTo("alpha.myshopify.com");
    }

    @Test
    void connectUpsertsWhenStoreDoesNotExist() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenThrow(notFound());
        when(client.upsertStore(any())).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));

        ShopifyBridgeStoreSummary response = service.connect(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).upsertStore(new ShopifyBridgeUpsertStoreRequest(
            "alpha.myshopify.com",
            "alpha.myshopify.com",
            "shopify-bridge-prod",
            null,
            null,
            null,
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            true,
            true,
            true,
            true
        ));
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
    }

    @Test
    void bootstrapReusesExistingStoreWhenAlreadyConnected() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));
        when(client.bootstrap("alpha.myshopify.com")).thenReturn(new ShopifyBridgeStoreBootstrapResponse(
            "alpha.myshopify.com",
            "cust-1",
            "dep-1",
            "consumer-1",
            false,
            false,
            false,
            List.of("mkp-template-commerce-shell"),
            store("alpha.myshopify.com")
        ));

        ShopifyBridgeStoreBootstrapResponse response = service.bootstrap(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client, never()).upsertStore(any());
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(client).bootstrap("alpha.myshopify.com");
    }

    @Test
    void goLiveUsesConnectedCredentialsAndDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));
        when(client.goLive("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.goLive(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(client).goLive("alpha.myshopify.com");
    }

    @Test
    void runSourcePreflightUsesConnectedCredentials() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeSourcePreflightService sourcePreflightService = mock(ShopifyBridgeSourcePreflightService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            sourcePreflightService
        );
        ShopifyBridgeCredentialAcquisition acquisition = acquisition(store("alpha.myshopify.com"));
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token")).thenReturn(acquisition);
        when(sourcePreflightService.run(acquisition)).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.runSourcePreflight(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(sourcePreflightService).run(acquisition);
    }

    private ShopifyMerchantSession session() {
        return new ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z")
        );
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
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
        );
    }

    private ShopifyBridgeStoreSummary store(String shopDomain) {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            shopDomain,
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
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
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
                "read_products,read_content",
                true
            )
        );
    }

    private HttpClientErrorException notFound() {
        return HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
    }
}
