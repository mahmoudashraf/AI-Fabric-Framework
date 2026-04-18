package com.ai.fabric.product.shopify.bridge.shell.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.shell.model.ShopifyBridgeShellResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopifyBridgeShellService {

    private final ShopifyBridgeProperties properties;

    public ShopifyBridgeShellService(ShopifyBridgeProperties properties) {
        this.properties = properties;
    }

    public ShopifyBridgeShellResponse shell() {
        return new ShopifyBridgeShellResponse(
            properties.appName(),
            properties.serviceRef(),
            properties.environmentScope(),
            properties.adminApiKey().isBlank() ? "SETUP_REQUIRED" : "READY_FOR_ONBOARDING",
            List.of(
                "Install and identity",
                "Product provisioning",
                "Source readiness and preflight",
                "Publish, apply, sync, and verify",
                "Storefront enablement"
            ),
            List.of(
                "Shopify embedded admin shell",
                "Managed product service diagnostics",
                "Read-first companion rollout",
                "Theme app extension delivery"
            )
        );
    }
}
