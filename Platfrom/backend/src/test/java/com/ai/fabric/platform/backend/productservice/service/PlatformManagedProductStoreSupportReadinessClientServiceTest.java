package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportReadinessSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformManagedProductStoreSupportReadinessClientServiceTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Test
    void retriesTransientConcurrentStreamFailureBeforeSurfacingSupportReadiness() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/support-readiness", exchange -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 409, """
                    {
                      "message": "too many concurrent streams"
                    }
                    """);
                return;
            }
            respond(exchange, 200, """
                {
                  "status": "READY",
                  "message": "Customer-safe order lookup is ready for this store.",
                  "lifecycleStage": "READY",
                  "orderLookupSupported": true,
                  "orderLookupScopeGranted": true,
                  "allOrdersScopeGranted": false,
                  "appScopesUpdateWebhookReady": true,
                  "merchantHandoffConfigured": true,
                  "verificationMethods": ["ORDER_NUMBER_AND_EMAIL"],
                  "supportedCapabilities": ["order-status", "tracking-link"]
                }
                """);
        });
        httpServer.start();

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");

        ShopifyStoreConnectionEntity storeConnection = new ShopifyStoreConnectionEntity();
        storeConnection.setId("shp-123");
        storeConnection.setProductServiceId(service.getId());
        storeConnection.setShopDomain("demo.myshopify.com");

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(Optional.of(storeConnection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");

        PlatformManagedProductStoreSupportReadinessClientService client =
            new PlatformManagedProductStoreSupportReadinessClientService(
                serviceService,
                shopifyStoreConnectionRepository,
                platformSecretService,
                new ObjectMapper()
            );

        PlatformManagedProductServiceStoreSupportReadinessSummary summary =
            client.getStoreSupportReadiness("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.orderLookupSupported()).isTrue();
        assertThat(summary.appScopesUpdateWebhookReady()).isTrue();
        assertThat(summary.merchantHandoffConfigured()).isTrue();
        assertThat(requestCount.get()).isEqualTo(2);
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
