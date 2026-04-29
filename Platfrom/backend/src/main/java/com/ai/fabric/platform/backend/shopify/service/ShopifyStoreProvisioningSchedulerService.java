package com.ai.fabric.platform.backend.shopify.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(prefix = "platform.shopify.provisioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShopifyStoreProvisioningSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyStoreProvisioningSchedulerService.class);

    private final ShopifyStoreProvisioningService provisioningService;

    public ShopifyStoreProvisioningSchedulerService(ShopifyStoreProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Scheduled(
        initialDelayString = "${platform.shopify.provisioning.initial-delay:PT15S}",
        fixedDelayString = "${platform.shopify.provisioning.fixed-delay:PT20S}"
    )
    public void processNextDueJob() {
        try {
            provisioningService.processNextDueJob();
        } catch (RuntimeException ex) {
            log.warn("Shopify store provisioning scheduler cycle failed: {}", ex.getMessage());
        }
    }
}
