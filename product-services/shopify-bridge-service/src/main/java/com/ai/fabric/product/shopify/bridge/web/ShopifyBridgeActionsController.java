package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
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
    public ShopifyBridgeActionResult execute(@PathVariable String shopDomain,
                                             @RequestBody(required = false) ShopifyBridgeActionExecuteRequest request) {
        return actionExecutionService.execute(shopDomain, request);
    }
}
