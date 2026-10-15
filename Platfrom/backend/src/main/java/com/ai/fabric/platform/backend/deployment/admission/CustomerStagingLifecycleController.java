package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.ExtensionRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/staging-lifecycle")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerStagingLifecycleController {
    private final CustomerStagingLifecycleService service;
    private final CustomerOfferCodec codec;

    public CustomerStagingLifecycleController(CustomerStagingLifecycleService service, CustomerOfferCodec codec) {
        this.service = service; this.codec = codec;
    }

    @PostMapping("/extensions")
    public Summary extend(@PathVariable String offerId, @PathVariable String deploymentId, @RequestBody JsonNode body) {
        return service.extend(offerId, deploymentId, codec.read(codec.write(body), ExtensionRequest.class));
    }
}
