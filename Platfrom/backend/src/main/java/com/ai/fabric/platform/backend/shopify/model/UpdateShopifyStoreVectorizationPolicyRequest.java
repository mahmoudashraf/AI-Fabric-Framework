package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record UpdateShopifyStoreVectorizationPolicyRequest(
    Long policyVersion,
    List<ShopifyStoreVectorizationSourcePolicyInput> sourcePolicies
) {
}
