package com.ai.fabric.product.shopify.bridge.storefront.model;

import java.util.List;

public record ShopifyStorefrontOrderLookupSummary(
    String orderName,
    String confirmationNumber,
    String financialStatus,
    String fulfillmentStatus,
    String createdAt,
    String totalAmount,
    String currencyCode,
    String trackingCompany,
    String trackingNumberMasked,
    String trackingUrl,
    String statusPageUrl,
    List<ShopifyStorefrontOrderLookupLineItemSummary> lineItems
) {
}
