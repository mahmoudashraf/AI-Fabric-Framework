package com.ai.fabric.product.shopify.bridge.customeraccount.model;

public record ShopifyCustomerAccountTokenBrokerResponse(
    boolean success,
    String accessToken,
    String tokenType,
    String errorCode,
    String message
) {

    public static ShopifyCustomerAccountTokenBrokerResponse success(String accessToken) {
        return new ShopifyCustomerAccountTokenBrokerResponse(
            true,
            accessToken,
            "Bearer",
            null,
            "Customer Account token resolved."
        );
    }

    public static ShopifyCustomerAccountTokenBrokerResponse failure(String errorCode, String message) {
        return new ShopifyCustomerAccountTokenBrokerResponse(
            false,
            null,
            null,
            errorCode,
            message
        );
    }

    @Override
    public String toString() {
        return "ShopifyCustomerAccountTokenBrokerResponse[" +
            "success=" + success +
            ", accessToken=" + (accessToken == null ? "null" : "[REDACTED]") +
            ", tokenType=" + tokenType +
            ", errorCode=" + errorCode +
            ", message=" + message +
            ']';
    }
}
