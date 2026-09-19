package com.ai.fabric.runtime.smartbrain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainOperationWorker {

    private final SmartBrainOperationService operationService;

    public SmartBrainOperationWorker(SmartBrainOperationService operationService) {
        this.operationService = operationService;
    }

    @Scheduled(fixedDelayString = "${loomai.smart-brain.processing-delay-ms:2000}")
    public void process() {
        operationService.readyForProcessing().forEach(operationService::process);
    }
}
