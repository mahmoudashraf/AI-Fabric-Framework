package com.ai.fabric.product.shopify.bridge.diagnostics.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

@Service
public class ShopifyBridgeDiagnosticsService {

    private final ShopifyBridgeProperties properties;
    private final Instant serverStartedAt;

    public ShopifyBridgeDiagnosticsService(ShopifyBridgeProperties properties) {
        this.properties = properties;
        this.serverStartedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    public ShopifyBridgeOverviewResponse overview() {
        return new ShopifyBridgeOverviewResponse(
            properties.appName(),
            properties.serviceRef(),
            properties.productFamily(),
            properties.serviceKind(),
            properties.environmentScope(),
            emptyToNull(properties.platformBaseUrl()),
            emptyToNull(properties.publicBaseUrl()),
            !properties.adminApiKey().isBlank(),
            properties.adminApiKey().isBlank() ? "DEGRADED" : "READY",
            serverStartedAt,
            List.of(
                "managed-service-health",
                "platform-bridge-auth",
                "shopify-auth-shell",
                "sync-shell",
                "storefront-bridge-shell"
            ),
            List.of(
                "shopify-oauth-install-flow",
                "merchant-admin-ui",
                "source-preflight-jobs",
                "platform-bootstrap-client",
                "shopify-webhook-ingestion"
            )
        );
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
