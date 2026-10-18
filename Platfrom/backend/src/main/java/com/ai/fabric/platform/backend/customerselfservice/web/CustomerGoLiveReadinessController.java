package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerGoLiveReadinessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerGoLiveReadinessController {
    private final CustomerGoLiveReadinessService readiness;

    public CustomerGoLiveReadinessController(CustomerGoLiveReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/go-live-readiness")
    public Summary project(@PathVariable String offerId, @PathVariable String deploymentId,
                           @RequestParam String releaseId, @RequestParam String consumerId) {
        return readiness.project(offerId, deploymentId, releaseId, consumerId);
    }
}
