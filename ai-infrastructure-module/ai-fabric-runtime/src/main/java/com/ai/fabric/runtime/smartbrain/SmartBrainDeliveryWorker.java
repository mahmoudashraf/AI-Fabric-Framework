package com.ai.fabric.runtime.smartbrain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainDeliveryWorker {

    private final SmartBrainDeliveryService deliveryService;

    public SmartBrainDeliveryWorker(SmartBrainDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${loomai.smart-brain.delivery-delay-ms:3000}")
    public void deliver() {
        deliveryService.ready().forEach(deliveryService::deliver);
    }
}
