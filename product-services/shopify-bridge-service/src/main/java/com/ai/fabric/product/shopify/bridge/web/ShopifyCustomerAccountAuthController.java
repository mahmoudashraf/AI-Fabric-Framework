package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.customeraccount.model.ShopifyCustomerAccountAuthStatus;
import com.ai.fabric.product.shopify.bridge.customeraccount.service.ShopifyCustomerAccountOAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/customer-auth")
public class ShopifyCustomerAccountAuthController {

    private static final String SHOPPER_SESSION_HEADER = "X-AI-FABRIC-SHOPPER-SESSION-ID";

    private final ShopifyCustomerAccountOAuthService oauthService;

    public ShopifyCustomerAccountAuthController(ShopifyCustomerAccountOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam("shop") String shopDomain,
                                      @RequestParam(name = "shopperSessionId", required = false) String shopperSessionId,
                                      @RequestParam(name = "returnTo", required = false) String returnTo,
                                      @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false) String shopperSessionHeader) {
        URI target = oauthService.beginAuthorization(shopDomain, firstText(shopperSessionId, shopperSessionHeader), returnTo);
        return redirect(target);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
                                         @RequestParam("state") String state,
                                         @RequestParam(name = "error", required = false) String error) {
        URI target = StringUtils.hasText(error)
            ? oauthService.failedAuthorizationReturn(state)
            : oauthService.completeAuthorization(code, state);
        return redirect(target);
    }

    @GetMapping("/session")
    public ShopifyCustomerAccountAuthStatus session(@RequestParam("shop") String shopDomain,
                                                    @RequestParam(name = "shopperSessionId", required = false) String shopperSessionId,
                                                    @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false) String shopperSessionHeader) {
        return oauthService.status(shopDomain, firstText(shopperSessionId, shopperSessionHeader));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> revoke(@RequestParam("shop") String shopDomain,
                                       @RequestParam(name = "shopperSessionId", required = false) String shopperSessionId,
                                       @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false) String shopperSessionHeader) {
        oauthService.revoke(shopDomain, firstText(shopperSessionId, shopperSessionHeader));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, location.toString())
            .build();
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }
}
