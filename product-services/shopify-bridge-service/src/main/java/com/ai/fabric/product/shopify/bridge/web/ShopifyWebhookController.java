package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookService;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/webhooks/shopify")
public class ShopifyWebhookController {

    private final ShopifyWebhookVerificationService verificationService;
    private final ShopifyWebhookService webhookService;

    public ShopifyWebhookController(ShopifyWebhookVerificationService verificationService,
                                    ShopifyWebhookService webhookService) {
        this.verificationService = verificationService;
        this.webhookService = webhookService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void receive(@RequestHeader(name = "X-Shopify-Hmac-Sha256", required = false) String hmac,
                        @RequestHeader(name = "X-Shopify-Topic", required = false) String topic,
                        @RequestHeader(name = "X-Shopify-Shop-Domain", required = false) String shopDomain,
                        @RequestBody(required = false) String rawBody,
                        HttpServletRequest request) {
        if (!verificationService.verify(rawBody, hmac)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Shopify webhook HMAC.");
        }
        webhookService.handle(topic, shopDomain, rawBody);
    }
}
