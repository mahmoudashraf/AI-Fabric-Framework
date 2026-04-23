package com.ai.fabric.product.shopify.bridge.billing.service;

import com.ai.fabric.product.shopify.bridge.billing.config.ShopifyBridgeBillingProperties;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalResponse;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyBridgeBillingService {

    private static final List<String> FREE_ALLOWED_SURFACES = List.of("ai-search");
    private static final List<String> STARTER_ALLOWED_SURFACES = List.of(
        "ai-search",
        "contextual-pill",
        "product-insight",
        "policy-strip",
        "product-faq",
        "comparison"
    );
    private static final List<String> ELITE_ALLOWED_SURFACES = STARTER_ALLOWED_SURFACES;

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
        BillingMode billingMode = billingMode();
        TierEntitlements currentTier = entitlementsFor(CompanionTier.FREE);
        return buildSummary(
            billingMode,
            currentTier,
            "ACTIVE",
            false,
            false,
            availablePlans(currentTier.tier()),
            billingMode == BillingMode.FREE
                ? "Free tier is active. No Shopify billing approval is required in this bridge environment."
                : "Free tier is active. Merchants can upgrade to paid Loom Companion plans when Shopify billing is configured."
        );
    }

    public ShopifyBridgeBillingSummary summarizeForShop(String shopDomain, String accessToken) {
        BillingMode billingMode = billingMode();
        TierEntitlements fallbackTier = entitlementsFor(CompanionTier.FREE);
        List<ShopifyBridgeBillingPlanSummary> plans = availablePlans(fallbackTier.tier());
        if (billingMode == BillingMode.FREE || !hasText(shopDomain) || !hasText(accessToken)) {
            return buildSummary(
                billingMode,
                fallbackTier,
                "ACTIVE",
                false,
                false,
                plans,
                billingMode == BillingMode.FREE
                    ? "Free tier is active for this store."
                    : "Free tier is active. Connect the store with persisted Shopify credentials before requesting a paid upgrade."
            );
        }
        try {
            BillingSubscriptionState subscriptionState = resolveSubscriptionState(shopDomain, accessToken);
            if ("ACTIVE".equalsIgnoreCase(subscriptionState.status()) && subscriptionState.tier() != null) {
                TierEntitlements currentTier = entitlementsFor(subscriptionState.tier());
                return buildSummary(
                    billingMode,
                    currentTier,
                    "ACTIVE",
                    false,
                    false,
                    availablePlans(currentTier.tier()),
                    currentTier.tier() == CompanionTier.ELITE
                        ? "Elite tier is active for this store."
                        : "Starter tier is active for this store."
                );
            }
            if ("FROZEN".equalsIgnoreCase(subscriptionState.status())) {
                return buildSummary(
                    billingMode,
                    fallbackTier,
                    "PAYMENT_ISSUE",
                    false,
                    false,
                    plans,
                    "Paid billing is frozen for this store. Free tier remains active until billing is resolved."
                );
            }
            return buildSummary(
                billingMode,
                fallbackTier,
                "ACTIVE",
                false,
                false,
                plans,
                "Free tier is active. Upgrade to Starter or Elite to unlock broader Loom Companion surfaces."
            );
        } catch (ResponseStatusException ex) {
            return buildSummary(
                billingMode,
                fallbackTier,
                "CHECK_FAILED",
                false,
                false,
                plans,
                OptionalText.reasonOrFallback(ex.getReason(), "Shopify billing status could not be verified right now. Free tier remains active.")
            );
        }
    }

    public ShopifyBridgeBillingApprovalResponse createApproval(String shopDomain, String accessToken, String requestedTierKey) {
        BillingMode billingMode = billingMode();
        if (billingMode == BillingMode.FREE) {
            throw new ResponseStatusException(CONFLICT, "This bridge environment is running in free-only mode. Paid Shopify approval is not available.");
        }
        if (!hasText(shopDomain) || !hasText(accessToken)) {
            throw new ResponseStatusException(CONFLICT, "Shopify billing approval requires a connected store with resolved credentials.");
        }

        CompanionTier requestedTier = normalizeRequestedTier(requestedTierKey);
        if (requestedTier == CompanionTier.FREE) {
            throw new ResponseStatusException(CONFLICT, "Free tier does not require Shopify billing approval.");
        }
        BillingPlanDefinition requestedPlan = requiredPlanDefinition(requestedTier);
        if (!requestedPlan.commerciallyAvailable()) {
            throw new ResponseStatusException(CONFLICT, requestedPlan.unavailableMessage());
        }
        if (!billingConfigReady(requestedPlan)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify billing configuration is incomplete for the requested Loom Companion tier.");
        }

        BillingSubscriptionState current = resolveSubscriptionState(shopDomain, accessToken);
        if ("ACTIVE".equalsIgnoreCase(current.status()) && current.tier() == requestedTier) {
            return new ShopifyBridgeBillingApprovalResponse(
                "ACTIVE",
                null,
                requestedTier == CompanionTier.ELITE
                    ? "Elite tier is already active for this store."
                    : "Starter tier is already active for this store."
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
                "name", requestedPlan.planName(),
                "returnUrl", returnUrl(shopDomain),
                "trialDays", requestedPlan.trialDays(),
                "test", requestedPlan.testMode(),
                "lineItems", new Object[] {
                    Map.of(
                        "plan", Map.of(
                            "appRecurringPricingDetails", Map.of(
                                "price", Map.of(
                                    "amount", requestedPlan.amount().doubleValue(),
                                    "currencyCode", requestedPlan.currencyCode()
                                ),
                                "interval", requestedPlan.interval()
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
            requestedTier == CompanionTier.ELITE
                ? "Redirect the merchant to Shopify to approve the Loom Companion Elite subscription."
                : "Redirect the merchant to Shopify to approve the Loom Companion Starter subscription."
        );
    }

    public List<String> effectiveAllowedSurfaces(String shopDomain, String accessToken, List<String> configuredSurfaces) {
        List<String> requested = configuredSurfaces == null ? List.of() : configuredSurfaces.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
        List<String> allowed = summarizeForShop(shopDomain, accessToken).allowedSurfaces();
        if (requested.isEmpty()) {
            return allowed;
        }
        List<String> filtered = requested.stream()
            .filter(allowed::contains)
            .distinct()
            .toList();
        return filtered.isEmpty() ? allowed : filtered;
    }

    public Integer catalogProductCap(String shopDomain, String accessToken) {
        return summarizeForShop(shopDomain, accessToken).catalogProductCap();
    }

    private ShopifyBridgeBillingSummary buildSummary(BillingMode billingMode,
                                                     TierEntitlements tier,
                                                     String status,
                                                     boolean merchantApprovalRequired,
                                                     boolean launchBlocked,
                                                     List<ShopifyBridgeBillingPlanSummary> availablePlans,
                                                     String message) {
        return new ShopifyBridgeBillingSummary(
            billingMode.externalValue(),
            tier.tier().externalKey(),
            tier.planName(),
            status,
            merchantApprovalRequired,
            launchBlocked,
            tier.paidTier(),
            tier.actionCapable(),
            tier.catalogProductCap(),
            tier.syncCadence(),
            tier.poweredByBadgeRequired(),
            tier.chatFallbackEnabled(),
            tier.allowedSurfaces(),
            availablePlans,
            message
        );
    }

    private List<ShopifyBridgeBillingPlanSummary> availablePlans(CompanionTier activeTier) {
        return List.of(
            planSummary(CompanionTier.FREE, activeTier),
            planSummary(CompanionTier.STARTER, activeTier),
            planSummary(CompanionTier.ELITE, activeTier)
        );
    }

    private ShopifyBridgeBillingPlanSummary planSummary(CompanionTier tier, CompanionTier activeTier) {
        TierEntitlements entitlements = entitlementsFor(tier);
        BillingPlanDefinition plan = planDefinition(tier);
        boolean active = tier == activeTier;
        return new ShopifyBridgeBillingPlanSummary(
            tier.externalKey(),
            entitlements.planName(),
            plan == null || plan.amount() == null ? null : plan.amount().toPlainString(),
            plan == null ? null : plan.currencyCode(),
            plan == null ? null : plan.interval(),
            active,
            tier == CompanionTier.FREE || (plan != null && plan.commerciallyAvailable()),
            tier != CompanionTier.FREE && plan != null && plan.commerciallyAvailable(),
            entitlements.actionCapable(),
            entitlements.catalogProductCap(),
            entitlements.syncCadence(),
            entitlements.poweredByBadgeRequired(),
            entitlements.chatFallbackEnabled(),
            entitlements.allowedSurfaces(),
            tier == CompanionTier.FREE
                ? "Free tier is always available."
                : plan == null
                    ? "No Shopify billing plan is configured for this tier."
                    : plan.commerciallyAvailable()
                        ? "Available for merchant approval through Shopify billing."
                        : plan.unavailableMessage()
        );
    }

    private BillingMode billingMode() {
        String mode = normalizeMode(billingProperties.mode());
        return "SHOPIFY_APP_SUBSCRIPTION".equals(mode) ? BillingMode.SHOPIFY_APP_SUBSCRIPTION : BillingMode.FREE;
    }

    private String normalizeMode(String mode) {
        return hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : "FREE";
    }

    private CompanionTier normalizeRequestedTier(String tierKey) {
        if (!hasText(tierKey)) {
            return CompanionTier.STARTER;
        }
        return switch (tierKey.trim().toUpperCase(Locale.ROOT)) {
            case "FREE" -> CompanionTier.FREE;
            case "STARTER" -> CompanionTier.STARTER;
            case "ELITE" -> CompanionTier.ELITE;
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported Shopify Companion tier: " + tierKey);
        };
    }

    private BillingPlanDefinition requiredPlanDefinition(CompanionTier tier) {
        BillingPlanDefinition definition = planDefinition(tier);
        if (definition == null) {
            throw new ResponseStatusException(CONFLICT, "No Shopify billing plan is configured for " + tier.externalKey() + ".");
        }
        return definition;
    }

    private BillingPlanDefinition planDefinition(CompanionTier tier) {
        return switch (tier) {
            case FREE -> null;
            case STARTER -> new BillingPlanDefinition(
                CompanionTier.STARTER,
                normalizePlanName(billingProperties.starterPlanName(), "Loom Companion Starter"),
                normalizePlanHandle(billingProperties.starterPlanHandle(), "loom-companion-starter"),
                amountOrNull(billingProperties.starterAmount()),
                currencyCodeOrNull(billingProperties.starterCurrencyCode()),
                intervalOrNull(billingProperties.starterInterval()),
                billingProperties.starterTrialDays(),
                billingProperties.starterTest(),
                billingProperties.starterEnabled(),
                billingProperties.starterEnabled()
                    ? "Starter is not fully configured in this bridge environment."
                    : "Starter is not enabled in this bridge environment yet."
            );
            case ELITE -> new BillingPlanDefinition(
                CompanionTier.ELITE,
                normalizePlanName(billingProperties.elitePlanName(), "Loom Companion Elite"),
                normalizePlanHandle(billingProperties.elitePlanHandle(), "loom-companion-elite"),
                amountOrNull(billingProperties.eliteAmount()),
                currencyCodeOrNull(billingProperties.eliteCurrencyCode()),
                intervalOrNull(billingProperties.eliteInterval()),
                billingProperties.eliteTrialDays(),
                billingProperties.eliteTest(),
                billingProperties.eliteEnabled(),
                billingProperties.eliteEnabled()
                    ? "Elite is not fully configured in this bridge environment."
                    : "Elite is not enabled until read-write action governance is ready."
            );
        };
    }

    private TierEntitlements entitlementsFor(CompanionTier tier) {
        return switch (tier) {
            case FREE -> new TierEntitlements(
                CompanionTier.FREE,
                "Loom Companion Free",
                false,
                false,
                50,
                "DAILY",
                true,
                false,
                FREE_ALLOWED_SURFACES
            );
            case STARTER -> new TierEntitlements(
                CompanionTier.STARTER,
                normalizePlanName(billingProperties.starterPlanName(), "Loom Companion Starter"),
                true,
                false,
                null,
                "TWO_HOURS",
                false,
                true,
                STARTER_ALLOWED_SURFACES
            );
            case ELITE -> new TierEntitlements(
                CompanionTier.ELITE,
                normalizePlanName(billingProperties.elitePlanName(), "Loom Companion Elite"),
                true,
                true,
                null,
                "HOURLY",
                false,
                true,
                ELITE_ALLOWED_SURFACES
            );
        };
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
            String name = text(subscription.get("name"));
            return new BillingSubscriptionState(status.trim().toUpperCase(Locale.ROOT), resolveTierFromSubscription(name), name);
        }
        return new BillingSubscriptionState("NONE", null, null);
    }

    private CompanionTier resolveTierFromSubscription(String subscriptionName) {
        if (!hasText(subscriptionName)) {
            return CompanionTier.STARTER;
        }
        String normalized = subscriptionName.trim().toLowerCase(Locale.ROOT);
        BillingPlanDefinition elite = planDefinition(CompanionTier.ELITE);
        if (elite != null) {
            if (normalized.equalsIgnoreCase(elite.planName()) || normalized.equalsIgnoreCase(elite.planHandle()) || normalized.contains("elite")) {
                return CompanionTier.ELITE;
            }
        }
        BillingPlanDefinition starter = planDefinition(CompanionTier.STARTER);
        if (starter != null) {
            if (normalized.equalsIgnoreCase(starter.planName()) || normalized.equalsIgnoreCase(starter.planHandle()) || normalized.contains("starter")) {
                return CompanionTier.STARTER;
            }
        }
        return CompanionTier.STARTER;
    }

    private boolean billingConfigReady(BillingPlanDefinition definition) {
        return definition != null
            && definition.enabled()
            && hasText(bridgeProperties.publicBaseUrl())
            && hasText(bridgeProperties.shopifyApiKey())
            && definition.amount() != null
            && hasText(definition.currencyCode())
            && hasText(definition.interval());
    }

    private BigDecimal amountOrNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            return amount.signum() > 0 ? amount : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String currencyCodeOrNull(String value) {
        String currencyCode = text(value);
        return currencyCode == null ? null : currencyCode.toUpperCase(Locale.ROOT);
    }

    private String intervalOrNull(String value) {
        String interval = text(value);
        return interval == null ? null : interval.toUpperCase(Locale.ROOT);
    }

    private String normalizePlanName(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String normalizePlanHandle(String value, String fallback) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : fallback;
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

    private enum BillingMode {
        FREE("FREE"),
        SHOPIFY_APP_SUBSCRIPTION("SHOPIFY_APP_SUBSCRIPTION");

        private final String externalValue;

        BillingMode(String externalValue) {
            this.externalValue = externalValue;
        }

        public String externalValue() {
            return externalValue;
        }
    }

    private enum CompanionTier {
        FREE("FREE"),
        STARTER("STARTER"),
        ELITE("ELITE");

        private final String externalKey;

        CompanionTier(String externalKey) {
            this.externalKey = externalKey;
        }

        public String externalKey() {
            return externalKey;
        }
    }

    private record TierEntitlements(
        CompanionTier tier,
        String planName,
        boolean paidTier,
        boolean actionCapable,
        Integer catalogProductCap,
        String syncCadence,
        boolean poweredByBadgeRequired,
        boolean chatFallbackEnabled,
        List<String> allowedSurfaces
    ) {
    }

    private record BillingPlanDefinition(
        CompanionTier tier,
        String planName,
        String planHandle,
        BigDecimal amount,
        String currencyCode,
        String interval,
        Integer trialDays,
        boolean testMode,
        boolean enabled,
        String unavailableMessage
    ) {
        boolean commerciallyAvailable() {
            return enabled && amount != null && currencyCode != null && interval != null;
        }
    }

    private record BillingSubscriptionState(
        String status,
        CompanionTier tier,
        String subscriptionName
    ) {
    }

    private static final class OptionalText {
        private OptionalText() {
        }

        private static String reasonOrFallback(String reason, String fallback) {
            return reason == null || reason.isBlank() ? fallback : reason;
        }
    }
}
