package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallFlowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth/shopify")
public class ShopifyInstallController {

    private final ShopifyInstallFlowService installFlowService;

    public ShopifyInstallController(ShopifyInstallFlowService installFlowService) {
        this.installFlowService = installFlowService;
    }

    @GetMapping("/install")
    public ResponseEntity<Void> install(@RequestParam("shop") String shopDomain) {
        return redirect(installFlowService.beginInstall(shopDomain));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("shop") String shopDomain,
                                         @RequestParam("code") String code,
                                         @RequestParam(name = "host", required = false) String host,
                                         @RequestParam("state") String state,
                                         HttpServletRequest request) {
        URI target = installFlowService.completeInstall(
            shopDomain,
            code,
            host,
            state,
            request.getQueryString()
        );
        return redirect(target);
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, location.toString())
            .build();
    }
}
