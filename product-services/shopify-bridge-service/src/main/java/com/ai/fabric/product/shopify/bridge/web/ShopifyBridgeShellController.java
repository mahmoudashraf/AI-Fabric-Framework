package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.shell.model.ShopifyBridgeShellResponse;
import com.ai.fabric.product.shopify.bridge.shell.service.ShopifyBridgeShellService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class ShopifyBridgeShellController {

    private final ShopifyBridgeShellService shellService;

    public ShopifyBridgeShellController(ShopifyBridgeShellService shellService) {
        this.shellService = shellService;
    }

    @GetMapping("/shell")
    public ShopifyBridgeShellResponse shell() {
        return shellService.shell();
    }
}
