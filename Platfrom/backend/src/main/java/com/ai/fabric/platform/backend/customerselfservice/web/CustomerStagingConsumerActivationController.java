package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.CreateRequest;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerStagingConsumerActivationService;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}")
public class CustomerStagingConsumerActivationController {
    private final CustomerStagingConsumerActivationService activations;

    public CustomerStagingConsumerActivationController(CustomerStagingConsumerActivationService activations) {
        this.activations = activations;
    }

    @PostMapping("/staging-consumer-activations")
    public PlatformConsumerSummary activate(@PathVariable String offerId, @PathVariable String deploymentId,
                                            @RequestBody Request request) {
        return activations.activate(offerId, deploymentId, request);
    }

    @PostMapping("/staging-consumer-assignments")
    public PlatformConsumerSummary createAndActivate(@PathVariable String offerId, @PathVariable String deploymentId,
                                                     @RequestBody CreateRequest request) {
        return activations.createAndActivate(offerId, deploymentId, request);
    }
}
