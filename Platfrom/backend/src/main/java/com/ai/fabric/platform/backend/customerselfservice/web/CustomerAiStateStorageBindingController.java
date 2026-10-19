package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerAiStateStorageBindingService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Summary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/ai-state-storage-binding")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerAiStateStorageBindingController {
    private final CustomerAiStateStorageBindingService service;
    private final CustomerOfferCodec codec;

    public CustomerAiStateStorageBindingController(CustomerAiStateStorageBindingService service, CustomerOfferCodec codec) {
        this.service = service; this.codec = codec;
    }

    @PostMapping
    public Result bind(@PathVariable String offerId, @PathVariable String deploymentId, @RequestBody JsonNode body) {
        if (body == null || !body.isObject() || !body.path("expectedEntitlementRevision").isIntegralNumber()
            || !body.path("expectedEntitlementRevision").canConvertToLong()) throw invalid();
        JsonNode connection = body.path("connection");
        if (!connection.isObject() || !connection.path("migrationAuthorized").isBoolean()) throw invalid();
        for (String field : java.util.List.of("versionId", "host", "databaseName", "runtimeSchema", "connectorSchema", "username", "password")) {
            if (!connection.path(field).isTextual()) throw invalid();
        }
        if (connection.hasNonNull("port") && (!connection.path("port").isIntegralNumber()
            || !connection.path("port").canConvertToInt())) throw invalid();
        return service.bind(offerId, deploymentId, codec.read(codec.write(body), Request.class));
    }

    @GetMapping
    public Summary get(@PathVariable String offerId, @PathVariable String deploymentId,
                       @RequestParam String targetProfileId, @RequestParam String bindingSlotId, @RequestParam String versionId) {
        return service.get(offerId, deploymentId, targetProfileId, bindingSlotId, versionId);
    }
}
