package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerExistingVectorBindingService;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Summary;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customer-self-service/deployments/{deploymentId}/targets/{targetId}/existing-vector-binding")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerExistingVectorBindingController {
    private final CustomerExistingVectorBindingService service;

    public CustomerExistingVectorBindingController(CustomerExistingVectorBindingService service) { this.service = service; }

    @GetMapping
    public Summary get(@PathVariable String deploymentId, @PathVariable String targetId) {
        return service.get(deploymentId, targetId);
    }

    @PostMapping
    public Summary bind(@PathVariable String deploymentId, @PathVariable String targetId, @RequestBody Request request) {
        return service.bind(deploymentId, targetId, request);
    }

    @RestControllerAdvice(assignableTypes = CustomerExistingVectorBindingController.class)
    public static class InvalidRequestHandler {
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> invalidJson() {
            return ResponseEntity.badRequest().body(Map.of("success", false, "errorCode", "EXTERNAL_VECTOR_REQUEST_INVALID"));
        }
    }
}
