package com.ai.fabric.product.shopify.bridge.install.model;

import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;

public record ShopifyBridgeCredentialAcquisition(
    ShopifyBridgeStoreSummary store,
    ShopifyTokenExchangeMaterial tokenExchangeMaterial
) {
}
