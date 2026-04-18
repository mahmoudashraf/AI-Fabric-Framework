package com.ai.fabric.product.shopify.bridge.billing.service;

import com.ai.fabric.product.shopify.bridge.billing.config.ShopifyBridgeBillingProperties;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalResponse;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyBridgeBillingService {

    private static final String ACTIVE_SUBSCRIPTIONS_QUERY = """
        query ShopifyBridgeActiveSubscriptions {
          currentAppInstallation {
            activeSubscriptions {
              id
              name
              status
            }
          }
        }
        """;

    private static final String CREATE_SUBSCRIPTION_MUTATION = """
        mutation ShopifyBridgeCreateAppSubscription($name: String!, $returnUrl: URL!, $lineItems: [AppSubscriptionLineItemInput!]!, $trialDays: Int, $test: Boolean) {
          appSubscriptionCreate(name: $name, returnUrl: $returnUrl, lineItems: $lineItems, trialDays: $trialDays, test: $test) {
            confirmationUrl
            appSubscription {
              id
            }
            userErrors {
              field
              message
            }
          }
        }
        """;

    private final ShopifyBridgeBillingProperties billingProperties;
    private final ShopifyBridgeProperties bridgeProperties;
    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;

    public ShopifyBridgeBillingService(ShopifyBridgeBillingProperties billingProperties,
                                       ShopifyBridgeProperties bridgeProperties,
                                       ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        this.billingProperties = billingProperties;
        this.bridgeProperties = bridgeProperties;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
    }

    public ShopifyBridgeBillingSummary summarize() {
        String mode = normalizeMode(billingProperties.mode());
        if ("FREE".equals(mode)) {
            return new ShopifyBridgeBillingSummary(
                "FREE",
                billingProperties.planName(),
                "ACTIVE",
                false,
                false,
                "The Shopify Companion app is currently running in free mode. No merchant billing approval is required."
            );
        }

        boolean configured = billingConfigReady();
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            billingProperties.planName(),
            configured ? "READY_FOR_APPROVAL" : "SETUP_REQUIRED",
            true,
            true,
            configured
                ? "Paid launch posture is configured. Merchant approval is required before Shopify Companion can go live."
                : "Paid launch posture is selected, but Shopify app subscription configuration is incomplete."
        );
    }

    public ShopifyBridgeBillingSummary summarizeForShop(String shopDomain, String accessToken) {
        ShopifyBridgeBillingSummary posture = summarize();
        if ("FREE".equals(posture.mode())) {
            return posture;
        }
        if ("SETUP_REQUIRED".equalsIgnoreCase(posture.status())) {
            return posture;
        }
        if (!hasText(shopDomain) || !hasText(accessToken)) {
            return new ShopifyBridgeBillingSummary(
                posture.mode(),
                posture.planName(),
                "CONNECT_REQUIRED",
                true,
                true,
                "Connect the store and persist Shopify credentials before requesting billing approval."
            );
        }
        try {
            BillingSubscriptionState state = resolveSubscriptionState(shopDomain, accessToken);
            return switch (state.status()) {
                case "ACTIVE" -> new ShopifyBridgeBillingSummary(
                    posture.mode(),
                    posture.planName(),
                    "ACTIVE",
                    false,
                    false,
                    "Shopify billing is active for this store."
                );
                case "FROZEN" -> new ShopifyBridgeBillingSummary(
                    posture.mode(),
                    posture.planName(),
                    "PAYMENT_ISSUE",
                    true,
                    true,
                    "Shopify billing is on hold for this store because payment is frozen. Resolve billing in Shopify admin before go-live."
                );
                default -> new ShopifyBridgeBillingSummary(
                    posture.mode(),
                    posture.planName(),
                    "READY_FOR_APPROVAL",
                    true,
                    true,
                    "Merchant approval is still required before Shopify Companion can go live."
                );
            };
        } catch (ResponseStatusException ex) {
            return new ShopifyBridgeBillingSummary(
                posture.mode(),
                posture.planName(),
                "CHECK_FAILED",
                true,
                true,
                OptionalText.reasonOrFallback(ex.getReason(), "Shopify billing status could not be verified right now.")
            );
        }
    }

    public ShopifyBridgeBillingApprovalResponse createApproval(String shopDomain, String accessToken) {
        String mode = normalizeMode(billingProperties.mode());
        if ("FREE".equals(mode)) {
            throw new ResponseStatusException(CONFLICT, "Shopify billing approval is not required while the bridge runs in free mode.");
        }
        if (!billingConfigReady()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify billing configuration is incomplete for this bridge environment.");
        }
        if (!hasText(shopDomain) || !hasText(accessToken)) {
            throw new ResponseStatusException(CONFLICT, "Shopify billing approval requires a connected store with resolved credentials.");
        }

        BillingSubscriptionState current = resolveSubscriptionState(shopDomain, accessToken);
        if ("ACTIVE".equalsIgnoreCase(current.status())) {
            return new ShopifyBridgeBillingApprovalResponse(
                "ACTIVE",
                null,
                "Shopify billing is already active for this store."
            );
        }
        if ("FROZEN".equalsIgnoreCase(current.status())) {
            throw new ResponseStatusException(CONFLICT, "Shopify billing is frozen for this store. Resolve the merchant billing issue before requesting a new approval.");
        }

        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            CREATE_SUBSCRIPTION_MUTATION,
            Map.of(
                "name", billingProperties.planName(),
                "returnUrl", returnUrl(shopDomain),
                "trialDays", billingProperties.appSubscriptionTrialDays(),
                "test", billingProperties.appSubscriptionTest(),
                "lineItems", new Object[] {
                    Map.of(
                        "plan", Map.of(
                            "appRecurringPricingDetails", Map.of(
                                "price", Map.of(
                                    "amount", requiredAmount().doubleValue(),
                                    "currencyCode", requiredCurrencyCode()
                                ),
                                "interval", requiredInterval()
                            )
                        )
                    )
                }
            )
        );
        failOnGraphQlErrors(response, "Shopify billing approval creation failed.");
        Map<String, Object> data = requireMap(response.get("data"), "Shopify billing approval creation returned no data.");
        Map<String, Object> payload = requireMap(
            data.get("appSubscriptionCreate"),
            "Shopify billing approval creation returned no appSubscriptionCreate payload."
        );
        failOnUserErrors(payload.get("userErrors"), "Shopify billing approval creation failed.");
        String confirmationUrl = requiredText(
            payload.get("confirmationUrl"),
            "Shopify billing approval creation did not return a confirmation URL."
        );
        return new ShopifyBridgeBillingApprovalResponse(
            "READY_FOR_APPROVAL",
            confirmationUrl,
            "Redirect the merchant to Shopify to approve the app subscription."
        );
    }

    private String normalizeMode(String mode) {
        return hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : "FREE";
    }

    private BillingSubscriptionState resolveSubscriptionState(String shopDomain, String accessToken) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(shopDomain, accessToken, ACTIVE_SUBSCRIPTIONS_QUERY);
        failOnGraphQlErrors(response, "Shopify billing status lookup failed.");
        Map<String, Object> data = requireMap(response.get("data"), "Shopify billing status lookup returned no data.");
        Map<String, Object> installation = requireMap(
            data.get("currentAppInstallation"),
            "Shopify billing status lookup returned no currentAppInstallation payload."
        );
        Object subscriptionsValue = installation.get("activeSubscriptions");
        if (!(subscriptionsValue instanceof Iterable<?> subscriptions)) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify billing status lookup returned no activeSubscriptions payload.");
        }
        for (Object entry : subscriptions) {
            if (!(entry instanceof Map<?, ?> subscription)) {
                continue;
            }
            String status = text(subscription.get("status"));
            if (!hasText(status)) {
                continue;
            }
            return new BillingSubscriptionState(status.trim().toUpperCase(Locale.ROOT));
        }
        return new BillingSubscriptionState("NONE");
    }

    private boolean billingConfigReady() {
        return hasText(bridgeProperties.publicBaseUrl())
            && hasText(bridgeProperties.shopifyApiKey())
            && requiredAmountOrNull() != null
            && hasText(requiredCurrencyCode())
            && hasText(requiredInterval());
    }

    private BigDecimal requiredAmount() {
        BigDecimal amount = requiredAmountOrNull();
        if (amount == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify billing amount is not configured.");
        }
        return amount;
    }

    private BigDecimal requiredAmountOrNull() {
        if (!hasText(billingProperties.appSubscriptionAmount())) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(billingProperties.appSubscriptionAmount().trim());
            return amount.signum() > 0 ? amount : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String requiredCurrencyCode() {
        String currencyCode = text(billingProperties.appSubscriptionCurrencyCode());
        return currencyCode == null ? null : currencyCode.toUpperCase(Locale.ROOT);
    }

    private String requiredInterval() {
        String interval = text(billingProperties.appSubscriptionInterval());
        return interval == null ? null : interval.toUpperCase(Locale.ROOT);
    }

    private String returnUrl(String shopDomain) {
        String baseUrl = bridgeProperties.publicBaseUrl();
        if (!hasText(baseUrl)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge public base URL is not configured.");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "?shop=" + normalizeShopDomain(shopDomain) + "&billing=return";
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!hasText(shopDomain)) {
            throw new ResponseStatusException(CONFLICT, "Shopify shop domain is required.");
        }
        return shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    private String requiredText(Object value, String message) {
        String text = text(value);
        if (!hasText(text)) {
            throw new ResponseStatusException(BAD_GATEWAY, message);
        }
        return text;
    }

    private void failOnGraphQlErrors(Map<String, Object> response, String fallbackMessage) {
        Object errorsValue = response.get("errors");
        if (!(errorsValue instanceof Iterable<?> errors)) {
            return;
        }
        for (Object error : errors) {
            if (error instanceof Map<?, ?> errorMap) {
                String message = text(errorMap.get("message"));
                if (hasText(message)) {
                    throw new ResponseStatusException(BAD_GATEWAY, message);
                }
            }
        }
        throw new ResponseStatusException(BAD_GATEWAY, fallbackMessage);
    }

    private void failOnUserErrors(Object userErrorsValue, String fallbackMessage) {
        if (!(userErrorsValue instanceof Iterable<?> userErrors)) {
            return;
        }
        for (Object userError : userErrors) {
            if (userError instanceof Map<?, ?> userErrorMap) {
                String message = text(userErrorMap.get("message"));
                if (hasText(message)) {
                    throw new ResponseStatusException(CONFLICT, message);
                }
            }
        }
    }

    private record BillingSubscriptionState(String status) {
    }

    private static final class OptionalText {
        private OptionalText() {
        }

        private static String reasonOrFallback(String reason, String fallback) {
            return reason == null || reason.isBlank() ? fallback : reason;
        }
    }
}
