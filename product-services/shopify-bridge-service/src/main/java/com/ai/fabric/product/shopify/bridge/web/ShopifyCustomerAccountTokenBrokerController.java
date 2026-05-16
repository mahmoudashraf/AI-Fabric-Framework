package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.customeraccount.model.ShopifyCustomerAccountTokenBrokerRequest;
import com.ai.fabric.product.shopify.bridge.customeraccount.model.ShopifyCustomerAccountTokenBrokerResponse;
import com.ai.fabric.product.shopify.bridge.customeraccount.service.ShopifyCustomerAccountOAuthService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customer-account")
public class ShopifyCustomerAccountTokenBrokerController {

    private final ShopifyCustomerAccountOAuthService oauthService;

    public ShopifyCustomerAccountTokenBrokerController(ShopifyCustomerAccountOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @PostMapping("/shops/{shopDomain}/token/resolve")
    public ShopifyCustomerAccountTokenBrokerResponse resolve(@PathVariable String shopDomain,
                                                            @RequestBody(required = false) ShopifyCustomerAccountTokenBrokerRequest request) {
        String shopperSessionId = request == null ? null : request.shopperSessionId();
        if (!StringUtils.hasText(shopperSessionId)) {
            return ShopifyCustomerAccountTokenBrokerResponse.failure(
                "SHOPPER_SESSION_REQUIRED",
                "A verified shopper session is required to resolve a Customer Account token."
            );
        }
        return oauthService.resolveAccessToken(shopDomain, shopperSessionId)
            .map(ShopifyCustomerAccountTokenBrokerResponse::success)
            .orElseGet(() -> ShopifyCustomerAccountTokenBrokerResponse.failure(
                "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
                "Customer Account authorization is required for this shopper session."
            ));
    }
}
