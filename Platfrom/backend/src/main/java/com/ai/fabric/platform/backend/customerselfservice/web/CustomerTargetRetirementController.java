package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.PlanRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Command;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetRetirementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/retirements")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerTargetRetirementController {
    private final CustomerTargetRetirementService retirements;
    private final CustomerOfferCodec codec;

    public CustomerTargetRetirementController(CustomerTargetRetirementService retirements, CustomerOfferCodec codec) {
        this.retirements = retirements;
        this.codec = codec;
    }

    @PostMapping("/plan")
    public Summary plan(@PathVariable String offerId, @PathVariable String deploymentId, @RequestBody JsonNode body) {
        return retirements.plan(offerId, deploymentId, codec.read(codec.write(body), PlanRequest.class));
    }

    @GetMapping
    public List<Summary> list(@PathVariable String offerId, @PathVariable String deploymentId,
                              @RequestParam String targetProfileId) {
        return retirements.list(offerId, deploymentId, targetProfileId);
    }

    @GetMapping("/{operationId}")
    public Summary status(@PathVariable String offerId, @PathVariable String deploymentId, @PathVariable String operationId) {
        return retirements.status(offerId, deploymentId, operationId);
    }

    @PostMapping("/{operationId}/execute")
    public Summary execute(@PathVariable String offerId, @PathVariable String deploymentId,
                           @PathVariable String operationId, @RequestBody JsonNode body) {
        return retirements.execute(offerId, deploymentId, operationId, codec.read(codec.write(body), Command.class));
    }

    @PostMapping("/{operationId}/retry")
    public Summary retry(@PathVariable String offerId, @PathVariable String deploymentId,
                         @PathVariable String operationId, @RequestBody JsonNode body) {
        return retirements.retry(offerId, deploymentId, operationId, codec.read(codec.write(body), Command.class));
    }

    @PostMapping("/{operationId}/cancel")
    public Summary cancel(@PathVariable String offerId, @PathVariable String deploymentId,
                          @PathVariable String operationId, @RequestBody JsonNode body) {
        return retirements.cancel(offerId, deploymentId, operationId, codec.read(codec.write(body), Command.class));
    }
}
