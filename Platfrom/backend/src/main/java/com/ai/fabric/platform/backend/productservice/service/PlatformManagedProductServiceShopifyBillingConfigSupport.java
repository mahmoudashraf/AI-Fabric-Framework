package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceShopifyBillingConfigSummary;
import com.ai.fabric.platform.backend.productservice.model.UpdatePlatformManagedProductServiceShopifyBillingConfigRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;

public final class PlatformManagedProductServiceShopifyBillingConfigSupport {

    public static final String DETAILS_KEY = "shopifyBridgeBilling";

    private static final PlatformManagedProductServiceShopifyBillingConfigSummary DEFAULTS =
        new PlatformManagedProductServiceShopifyBillingConfigSummary(
            "FREE",
            false,
            "Loom Companion Starter",
            "loom-companion-starter",
            "29.00",
            "USD",
            "EVERY_30_DAYS",
            7,
            true,
            false,
            "Loom Companion Elite",
            "loom-companion-elite",
            "179.00",
            "USD",
            "EVERY_30_DAYS",
            0,
            true
        );

    private PlatformManagedProductServiceShopifyBillingConfigSupport() {
    }

    public static boolean supports(String serviceKind) {
        return "SHOPIFY_BRIDGE_SERVICE".equals(normalizeToken(serviceKind));
    }

    public static PlatformManagedProductServiceShopifyBillingConfigSummary defaults() {
        return DEFAULTS;
    }

    public static PlatformManagedProductServiceShopifyBillingConfigSummary summaryFromDetails(ObjectMapper objectMapper,
                                                                                              String detailsJson,
                                                                                              String serviceKind) {
        if (!supports(serviceKind)) {
            return null;
        }
        JsonNode details = readDetails(objectMapper, detailsJson);
        return normalize(details.path(DETAILS_KEY));
    }

    public static ObjectNode detailsWithConfig(ObjectMapper objectMapper,
                                               String detailsJson,
                                               UpdatePlatformManagedProductServiceShopifyBillingConfigRequest request) {
        ObjectNode details = mutableDetails(objectMapper, detailsJson);
        PlatformManagedProductServiceShopifyBillingConfigSummary summary = normalize(request);
        details.set(DETAILS_KEY, objectMapper.valueToTree(summary));
        return details;
    }

    public static Map<String, String> railwayEnv(PlatformManagedProductServiceShopifyBillingConfigSummary config) {
        PlatformManagedProductServiceShopifyBillingConfigSummary normalized = config == null ? DEFAULTS : config;
        Map<String, String> env = new LinkedHashMap<>();
        env.put("SHOPIFY_BRIDGE_BILLING_MODE", normalized.mode());
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_ENABLED", Boolean.toString(normalized.starterEnabled()));
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_PLAN_NAME", normalized.starterPlanName());
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_PLAN_HANDLE", normalized.starterPlanHandle());
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_AMOUNT", normalized.starterAmount());
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_CURRENCY_CODE", normalized.starterCurrencyCode());
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_INTERVAL", normalized.starterInterval());
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_TRIAL_DAYS", String.valueOf(normalized.starterTrialDays()));
        env.put("SHOPIFY_BRIDGE_BILLING_STARTER_TEST", Boolean.toString(normalized.starterTest()));
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_ENABLED", Boolean.toString(normalized.eliteEnabled()));
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_PLAN_NAME", normalized.elitePlanName());
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_PLAN_HANDLE", normalized.elitePlanHandle());
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_AMOUNT", normalized.eliteAmount());
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_CURRENCY_CODE", normalized.eliteCurrencyCode());
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_INTERVAL", normalized.eliteInterval());
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_TRIAL_DAYS", String.valueOf(normalized.eliteTrialDays()));
        env.put("SHOPIFY_BRIDGE_BILLING_ELITE_TEST", Boolean.toString(normalized.eliteTest()));

        env.put("SHOPIFY_BRIDGE_BILLING_PLAN_NAME", normalized.starterPlanName());
        env.put("SHOPIFY_BRIDGE_BILLING_PLAN_HANDLE", normalized.starterPlanHandle());
        env.put("SHOPIFY_BRIDGE_BILLING_AMOUNT", normalized.starterAmount());
        env.put("SHOPIFY_BRIDGE_BILLING_CURRENCY_CODE", normalized.starterCurrencyCode());
        env.put("SHOPIFY_BRIDGE_BILLING_INTERVAL", normalized.starterInterval());
        env.put("SHOPIFY_BRIDGE_BILLING_TRIAL_DAYS", String.valueOf(normalized.starterTrialDays()));
        env.put("SHOPIFY_BRIDGE_BILLING_TEST", Boolean.toString(normalized.starterTest()));
        return env;
    }

    private static PlatformManagedProductServiceShopifyBillingConfigSummary normalize(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return DEFAULTS;
        }
        return new PlatformManagedProductServiceShopifyBillingConfigSummary(
            normalizeMode(text(node, "mode"), DEFAULTS.mode()),
            bool(node, "starterEnabled", DEFAULTS.starterEnabled()),
            requiredText(node, "starterPlanName", DEFAULTS.starterPlanName(), "Starter plan name"),
            normalizePlanHandle(text(node, "starterPlanHandle"), DEFAULTS.starterPlanHandle(), "Starter plan handle"),
            normalizeAmount(text(node, "starterAmount"), DEFAULTS.starterAmount(), "Starter amount"),
            normalizeCurrency(text(node, "starterCurrencyCode"), DEFAULTS.starterCurrencyCode(), "Starter currency code"),
            normalizeInterval(text(node, "starterInterval"), DEFAULTS.starterInterval(), "Starter interval"),
            nonNegativeInt(node, "starterTrialDays", DEFAULTS.starterTrialDays(), "Starter trial days"),
            bool(node, "starterTest", DEFAULTS.starterTest()),
            bool(node, "eliteEnabled", DEFAULTS.eliteEnabled()),
            requiredText(node, "elitePlanName", DEFAULTS.elitePlanName(), "Elite plan name"),
            normalizePlanHandle(text(node, "elitePlanHandle"), DEFAULTS.elitePlanHandle(), "Elite plan handle"),
            normalizeAmount(text(node, "eliteAmount"), DEFAULTS.eliteAmount(), "Elite amount"),
            normalizeCurrency(text(node, "eliteCurrencyCode"), DEFAULTS.eliteCurrencyCode(), "Elite currency code"),
            normalizeInterval(text(node, "eliteInterval"), DEFAULTS.eliteInterval(), "Elite interval"),
            nonNegativeInt(node, "eliteTrialDays", DEFAULTS.eliteTrialDays(), "Elite trial days"),
            bool(node, "eliteTest", DEFAULTS.eliteTest())
        );
    }

    private static PlatformManagedProductServiceShopifyBillingConfigSummary normalize(UpdatePlatformManagedProductServiceShopifyBillingConfigRequest request) {
        if (request == null) {
            return DEFAULTS;
        }
        return new PlatformManagedProductServiceShopifyBillingConfigSummary(
            normalizeMode(request.mode(), DEFAULTS.mode()),
            request.starterEnabled() == null ? DEFAULTS.starterEnabled() : request.starterEnabled(),
            requiredString(request.starterPlanName(), DEFAULTS.starterPlanName(), "Starter plan name"),
            normalizePlanHandle(request.starterPlanHandle(), DEFAULTS.starterPlanHandle(), "Starter plan handle"),
            normalizeAmount(request.starterAmount(), DEFAULTS.starterAmount(), "Starter amount"),
            normalizeCurrency(request.starterCurrencyCode(), DEFAULTS.starterCurrencyCode(), "Starter currency code"),
            normalizeInterval(request.starterInterval(), DEFAULTS.starterInterval(), "Starter interval"),
            nonNegativeInt(request.starterTrialDays(), DEFAULTS.starterTrialDays(), "Starter trial days"),
            request.starterTest() == null ? DEFAULTS.starterTest() : request.starterTest(),
            request.eliteEnabled() == null ? DEFAULTS.eliteEnabled() : request.eliteEnabled(),
            requiredString(request.elitePlanName(), DEFAULTS.elitePlanName(), "Elite plan name"),
            normalizePlanHandle(request.elitePlanHandle(), DEFAULTS.elitePlanHandle(), "Elite plan handle"),
            normalizeAmount(request.eliteAmount(), DEFAULTS.eliteAmount(), "Elite amount"),
            normalizeCurrency(request.eliteCurrencyCode(), DEFAULTS.eliteCurrencyCode(), "Elite currency code"),
            normalizeInterval(request.eliteInterval(), DEFAULTS.eliteInterval(), "Elite interval"),
            nonNegativeInt(request.eliteTrialDays(), DEFAULTS.eliteTrialDays(), "Elite trial days"),
            request.eliteTest() == null ? DEFAULTS.eliteTest() : request.eliteTest()
        );
    }

    private static JsonNode readDetails(ObjectMapper objectMapper, String detailsJson) {
        try {
            return hasText(detailsJson) ? objectMapper.readTree(detailsJson) : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static ObjectNode mutableDetails(ObjectMapper objectMapper, String detailsJson) {
        JsonNode parsed = readDetails(objectMapper, detailsJson);
        return parsed.isObject() ? (ObjectNode) parsed.deepCopy() : objectMapper.createObjectNode();
    }

    private static String normalizeMode(String value, String fallback) {
        String normalized = requiredString(value, fallback, "Billing mode").toUpperCase(Locale.ROOT);
        if ("FREE".equals(normalized) || "SHOPIFY_APP_SUBSCRIPTION".equals(normalized)) {
            return normalized;
        }
        throw conflict("Billing mode must be FREE or SHOPIFY_APP_SUBSCRIPTION.");
    }

    private static String normalizePlanHandle(String value, String fallback, String label) {
        String normalized = requiredString(value, fallback, label).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw conflict(label + " must be 3-128 lowercase letters, numbers, hyphen, or underscore.");
        }
        return normalized;
    }

    private static String normalizeAmount(String value, String fallback, String label) {
        String normalized = requiredString(value, fallback, label);
        try {
            BigDecimal amount = new BigDecimal(normalized);
            if (amount.signum() <= 0) {
                throw conflict(label + " must be greater than zero.");
            }
            return amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException ex) {
            throw conflict(label + " must use no more than two decimal places.");
        } catch (NumberFormatException ex) {
            throw conflict(label + " must be a valid decimal amount.");
        }
    }

    private static String normalizeCurrency(String value, String fallback, String label) {
        String normalized = requiredString(value, fallback, label).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw conflict(label + " must be a three-letter ISO currency code.");
        }
        return normalized;
    }

    private static String normalizeInterval(String value, String fallback, String label) {
        String normalized = requiredString(value, fallback, label).toUpperCase(Locale.ROOT);
        if ("EVERY_30_DAYS".equals(normalized) || "ANNUAL".equals(normalized)) {
            return normalized;
        }
        throw conflict(label + " must be EVERY_30_DAYS or ANNUAL.");
    }

    private static String requiredText(JsonNode node, String field, String fallback, String label) {
        return requiredString(text(node, field), fallback, label);
    }

    private static String requiredString(String value, String fallback, String label) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            return normalized;
        }
        String fallbackValue = trimToNull(fallback);
        if (fallbackValue != null) {
            return fallbackValue;
        }
        throw conflict(label + " is required.");
    }

    private static int nonNegativeInt(JsonNode node, String field, int fallback, String label) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        return nonNegativeInt(value.asInt(), fallback, label);
    }

    private static int nonNegativeInt(Integer value, int fallback, String label) {
        int normalized = value == null ? fallback : value;
        if (normalized < 0 || normalized > 365) {
            throw conflict(label + " must be between 0 and 365.");
        }
        return normalized;
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asBoolean();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : trimToNull(value.asText());
    }

    private static String normalizeToken(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(CONFLICT, message);
    }
}
