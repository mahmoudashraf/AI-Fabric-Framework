package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @GetMapping("/{shopDomain}/mcp/readiness")
    public ResponseEntity<Map<String, Object>> mcpReadiness(@PathVariable String shopDomain) {
        Map<String, Object> result = actionExecutionService.mcpReadiness(shopDomain);
        return ResponseEntity.status(Boolean.TRUE.equals(result.get("ready")) ? HttpStatus.OK : HttpStatus.CONFLICT).body(result);
    }

    private HttpStatus statusFor(ShopifyBridgeActionResult result) {
        if (result == null || result.success()) {
            return HttpStatus.OK;
        }
        return switch (result.errorCode() == null ? "" : result.errorCode()) {
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "NOT_FOUND", "ACTION_NOT_SUPPORTED" -> HttpStatus.NOT_FOUND;
            case "NOT_CONNECTED",
                 "PRODUCT_SELECTION_REQUIRED",
                 "VARIANT_SELECTION_REQUIRED",
                 "SHOPPER_SESSION_REQUIRED",
                 "GOVERNED_ACTION_REJECTED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }
}
