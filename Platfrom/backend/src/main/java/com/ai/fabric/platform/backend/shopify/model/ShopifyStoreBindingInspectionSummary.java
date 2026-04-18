package com.ai.fabric.platform.backend.shopify.model;

import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;

import java.util.List;

public record ShopifyStoreBindingInspectionSummary(
    String shopDomain,
    String productServiceRef,
    ShopifyStoreLinkedCustomerSummary customer,
    ShopifyStoreLinkedDeploymentSummary deployment,
    ShopifyStoreLinkedConsumerSummary consumer,
    DeploymentVersionSummary latestVersion,
    DeploymentReleaseSummary latestRelease,
    List<String> warnings
) {
}
