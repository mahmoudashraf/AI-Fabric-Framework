package com.ai.fabric.platform.backend.shopify.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "platform.shopify.provisioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShopifyStoreProvisioningSchedulerService {

    private final ShopifyStoreProvisioningService provisioningService;

    public ShopifyStoreProvisioningSchedulerService(ShopifyStoreProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Scheduled(
        initialDelayString = "${platform.shopify.provisioning.initial-delay:PT15S}",
        fixedDelayString = "${platform.shopify.provisioning.fixed-delay:PT20S}"
    )
    public void processNextDueJob() {
        provisioningService.processNextDueJob();
    }
}
