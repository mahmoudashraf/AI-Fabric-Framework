package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stores")
public class ShopifyBridgeActionsController {

    private final ShopifyBridgeActionExecutionService actionExecutionService;

    public ShopifyBridgeActionsController(ShopifyBridgeActionExecutionService actionExecutionService) {
        this.actionExecutionService = actionExecutionService;
    }

    @PostMapping("/{shopDomain}/actions/execute")
    public ResponseEntity<ShopifyBridgeActionResult> execute(@PathVariable String shopDomain,
                                                             @RequestBody(required = false) ShopifyBridgeActionExecuteRequest request) {
        ShopifyBridgeActionResult result = actionExecutionService.execute(shopDomain, request);
        return ResponseEntity.status(statusFor(result)).body(result);
    }

    private HttpStatus statusFor(ShopifyBridgeActionResult result) {
        if (result == null || result.success()) {
            return HttpStatus.OK;
        }
        return switch (result.errorCode() == null ? "" : result.errorCode()) {
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "NOT_FOUND", "ACTION_NOT_SUPPORTED" -> HttpStatus.NOT_FOUND;
            case "NOT_CONNECTED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }
}
