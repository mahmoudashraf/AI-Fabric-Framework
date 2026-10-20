package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerConfigurationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerStagingConsumerConfigurationService;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerStagingConsumerConfigurationController {
    private final CustomerStagingConsumerConfigurationService configurations;

    public CustomerStagingConsumerConfigurationController(CustomerStagingConsumerConfigurationService configurations) {
        this.configurations = configurations;
    }

    @PostMapping("/staging-consumer-configurations")
    public PlatformConsumerSummary configure(@PathVariable String offerId, @PathVariable String deploymentId,
                                             @RequestBody Request request) {
        return configurations.configure(offerId, deploymentId, request);
    }
}
