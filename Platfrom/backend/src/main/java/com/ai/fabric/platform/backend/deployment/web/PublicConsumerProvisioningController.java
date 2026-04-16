package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentStatusResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.PublicProvisioningApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/consumers")
public class PublicConsumerProvisioningController {

    private final PublicProvisioningApiService publicProvisioningApiService;

    public PublicConsumerProvisioningController(PublicProvisioningApiService publicProvisioningApiService) {
        this.publicProvisioningApiService = publicProvisioningApiService;
    }

    @GetMapping("/{consumerId}")
    public PublicConsumerDeploymentSummary getConsumer(@PathVariable String consumerId) {
        return publicProvisioningApiService.getConsumerDeployment(consumerId);
    }

    @GetMapping("/{consumerId}/status")
    public PublicConsumerDeploymentStatusResponse getConsumerStatus(@PathVariable String consumerId) {
        return publicProvisioningApiService.getConsumerDeploymentStatus(consumerId);
    }

    @GetMapping("/{consumerId}/credentials")
    public PublicConsumerDeploymentCredentialsResponse getConsumerCredentials(@PathVariable String consumerId) {
        return publicProvisioningApiService.getConsumerDeploymentCredentials(consumerId);
    }
}
