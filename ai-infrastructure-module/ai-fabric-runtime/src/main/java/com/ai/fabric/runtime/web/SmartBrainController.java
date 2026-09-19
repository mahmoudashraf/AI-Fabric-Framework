package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationService;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationView;
import com.ai.fabric.runtime.smartbrain.SmartBrainRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/smart-brain/v1")
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainController {

    private final RuntimeRequestAuthResolver authResolver;
    private final SmartBrainOperationService operationService;

    public SmartBrainController(
        RuntimeRequestAuthResolver authResolver,
        SmartBrainOperationService operationService
    ) {
        this.authResolver = authResolver;
        this.operationService = operationService;
    }

    @PostMapping(
        value = "/triggers/{triggerCode}",
        consumes = {"application/cloudevents+json", MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<SmartBrainOperationView> trigger(
        @PathVariable String triggerCode,
        @RequestBody String cloudEvent,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.SMART_BRAIN_TRIGGER,
            "/api/smart-brain/v1/triggers/{triggerCode}"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            operationService.submit(identity, triggerCode, cloudEvent, idempotencyKey)
        );
    }

    @GetMapping("/operations/{operationId}")
    public SmartBrainOperationView operation(
        @PathVariable String operationId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.SMART_BRAIN_READ,
            "/api/smart-brain/v1/operations/{operationId}"
        );
        return operationService.status(identity, operationId);
    }

    @PostMapping("/operations/{operationId}/cancel")
    public SmartBrainOperationView cancel(
        @PathVariable String operationId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.SMART_BRAIN_CANCEL,
            "/api/smart-brain/v1/operations/{operationId}/cancel"
        );
        return operationService.cancel(identity, operationId);
    }

    @PostMapping("/operations/{operationId}/replay")
    public SmartBrainOperationView replay(
        @PathVariable String operationId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.SMART_BRAIN_REPLAY,
            "/api/smart-brain/v1/operations/{operationId}/replay"
        );
        return operationService.replay(identity, operationId);
    }

    @ExceptionHandler(SmartBrainRequestException.class)
    public ResponseEntity<Map<String, Object>> invalidRequest(SmartBrainRequestException exception) {
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "code", exception.code(),
            "message", exception.getMessage()
        ));
    }

    private RuntimeResolvedIdentity authorize(HttpServletRequest request, String scope, String surface) {
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedPrivateContext(request, surface);
        authResolver.requireScope(identity, scope, surface);
        return identity;
    }
}
