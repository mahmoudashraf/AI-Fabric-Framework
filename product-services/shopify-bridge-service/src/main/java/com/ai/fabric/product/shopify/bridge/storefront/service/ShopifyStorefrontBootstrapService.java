package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyProductReviewSignals;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShopifyStorefrontBootstrapService {

    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";
    private static final String DEFAULT_SHELL_MODE_PROFILE = "SHOPIFY_COMPANION";
    private static final String DEFAULT_CONVERSATION_MODE = "navigator";
    private static final List<String> DEFAULT_ENABLED_SURFACES = List.of("ai-search");
    private static final List<String> DEFAULT_ALLOWED_CONVERSATION_MODES = List.of(DEFAULT_CONVERSATION_MODE);
    private static final Map<String, String> DEFAULT_PAGE_MODE_MAPPINGS = Map.of();

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyStorefrontGovernedActionService governedActionService;
    private final ShopifyBridgeProperties properties;

    public ShopifyStorefrontBootstrapService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                             ShopifyBridgeInstallCredentialService installCredentialService,
                                             ShopifyInstallRecordService installRecordService,
                                             ShopifyBridgeBillingService billingService,
                                             ShopifyStorefrontGovernedActionService governedActionService,
                                             ShopifyBridgeProperties properties) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.installRecordService = installRecordService;
        this.billingService = billingService;
        this.governedActionService = governedActionService;
        this.properties = properties;
    }

    public ShopifyStorefrontBootstrapResponse bootstrap(String shopDomain, String pageType) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        String bridgeQueryUrl = storefrontUrl(store.shopDomain(), "/chat/query");
        String bridgeSuggestionsUrl = storefrontUrl(store.shopDomain(), "/chat/suggestions");
        String bridgeOrderLookupUrl = storefrontUrl(store.shopDomain(), "/support/order-lookup");
        String bridgeEventUrl = storefrontUrl(store.shopDomain(), "/events");
        if (!ShopifyStorefrontInteractionReadinessSupport.isReady(store)) {
            return unavailable(
                store,
                firstStorefrontBlockingReason(store),
                pageType,
                bridgeQueryUrl,
                bridgeSuggestionsUrl,
                bridgeOrderLookupUrl,
                bridgeEventUrl
            );
        }

        PlatformPublicConsumerDeploymentCredentialsResponse credentials =
            platformShopifyStoreClient.getConsumerCredentials(store.consumerId());
        ShopifyBridgeStoreSummary updated = platformShopifyStoreClient.recordWidgetStatus(
            store.shopDomain(),
            new ShopifyBridgeRecordWidgetStatusRequest(
                "ENABLED",
                "THEME_APP_EXTENSION",
                "Theme app extension resolved storefront bootstrap."
            )
        );

        String actionGrantUrl = storefrontUrl(updated.shopDomain(), "/actions/grant");
        String actionCompleteUrl = storefrontUrl(updated.shopDomain(), "/actions/complete");
        String preferredIntegrationMode = credentials.integration() == null ? null : credentials.integration().preferredIntegrationMode();
        String runtimeAuthMode = credentials.integration() == null || credentials.integration().posture() == null
            ? null
            : credentials.integration().posture().runtimeAuthMode();
        String guidance = credentials.integration() == null ? null : credentials.integration().guidance();
        ShopifyBridgeBillingSummary billingSummary = installCredentialService.resolvePersistedMaterial(updated.shopDomain())
            .map(acquisition -> billingService.summarizeForShop(updated.shopDomain(), acquisition.tokenExchangeMaterial().accessToken()))
            .orElseGet(() -> billingService.summarizeForShop(updated.shopDomain(), null));
        String launcherLabel = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().launcherLabel() != null && !updated.widgetDetail().settings().launcherLabel().isBlank()
            ? updated.widgetDetail().settings().launcherLabel().trim()
            : DEFAULT_LAUNCHER_LABEL;
        String welcomeMessage = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().welcomeMessage() != null && !updated.widgetDetail().settings().welcomeMessage().isBlank()
            ? updated.widgetDetail().settings().welcomeMessage().trim()
            : DEFAULT_WELCOME_MESSAGE;
        String shellModeProfile = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().shellModeProfile() != null && !updated.widgetDetail().settings().shellModeProfile().isBlank()
            ? updated.widgetDetail().settings().shellModeProfile().trim()
            : DEFAULT_SHELL_MODE_PROFILE;
        String defaultConversationMode = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().defaultConversationMode() != null
            && !updated.widgetDetail().settings().defaultConversationMode().isBlank()
            ? updated.widgetDetail().settings().defaultConversationMode().trim()
            : defaultConversationModeForShellProfile(shellModeProfile);
        List<String> allowedConversationModes = normalizeAllowedConversationModes(
            updated.widgetDetail() != null && updated.widgetDetail().settings() != null
                ? updated.widgetDetail().settings().allowedConversationModes()
                : null,
            defaultConversationMode
        );
        Map<String, String> pageModeMappings = normalizePageModeMappings(
            updated.widgetDetail() != null && updated.widgetDetail().settings() != null
                ? updated.widgetDetail().settings().pageModeMappings()
                : null,
            allowedConversationModes
        );
        String effectiveConversationMode = resolveEffectiveConversationMode(
            pageType,
            defaultConversationMode,
            allowedConversationModes,
            pageModeMappings
        );
        List<String> enabledSurfaces = updated.widgetDetail() != null
            && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().enabledSurfaces() != null
            && !updated.widgetDetail().settings().enabledSurfaces().isEmpty()
            ? List.copyOf(updated.widgetDetail().settings().enabledSurfaces())
            : billingSummary.allowedSurfaces();
        enabledSurfaces = billingService.effectiveAllowedSurfaces(updated.shopDomain(), null, enabledSurfaces);
        String scopesText = installRecordService.findByShopDomain(updated.shopDomain())
            .map(summary -> summary.scopesText() == null ? null : summary.scopesText())
            .orElseGet(() -> installCredentialService.resolvePersistedMaterial(updated.shopDomain())
                .map(acquisition -> acquisition.tokenExchangeMaterial().scopesText())
                .orElse(null));
        boolean orderLookupEnabled = ShopifyScopeSupport.hasScope(scopesText, "read_orders");
        boolean olderOrdersRequireBroaderScope = !ShopifyScopeSupport.hasScope(scopesText, "read_all_orders");
        if (orderLookupEnabled && !enabledSurfaces.contains("order-lookup")) {
            List<String> mergedSurfaces = new ArrayList<>(enabledSurfaces);
            mergedSurfaces.add("order-lookup");
            enabledSurfaces = List.copyOf(mergedSurfaces);
        }
        List<String> groundingSignals = groundingSignals(updated);
        List<String> supportedReviewProviders = supportedReviewProviders(updated);
        ShopifyStorefrontGovernedActionCapability actionCapability =
            governedActionService.capability(updated.shopDomain(), actionGrantUrl, actionCompleteUrl);

        return new ShopifyStorefrontBootstrapResponse(
            true,
            updated.shopDomain(),
            updated.consumerId(),
            updated.deploymentId(),
            updated.widgetStatus(),
            updated.sourceReadinessStatus(),
            billingSummary.tierKey(),
            billingSummary.status(),
            billingSummary.catalogProductCap(),
            billingSummary.poweredByBadgeRequired(),
            billingSummary.chatFallbackEnabled(),
            launcherLabel,
            welcomeMessage,
            shellModeProfile,
            defaultConversationMode,
            effectiveConversationMode,
            allowedConversationModes,
            pageModeMappings,
            enabledSurfaces,
            groundingSignals,
            supportedReviewProviders,
            preferredIntegrationMode,
            runtimeAuthMode,
            bridgeQueryUrl,
            bridgeSuggestionsUrl,
            bridgeOrderLookupUrl,
            bridgeEventUrl,
            orderLookupEnabled,
            olderOrdersRequireBroaderScope,
            orderLookupEnabled
                ? (olderOrdersRequireBroaderScope
                    ? "Order lookup is available with the exact order number and checkout email. Orders older than Shopify's default window still require broader order access."
                    : "Order lookup is available with the exact order number and checkout email.")
                : "Order lookup is waiting for Shopify order-read scope approval on this store.",
            actionCapability,
            guidance,
            "Storefront bootstrap resolved. Theme app extension can now call the bridge-backed shopper endpoints."
        );
    }

    private ShopifyStorefrontBootstrapResponse unavailable(ShopifyBridgeStoreSummary store,
                                                           String message,
                                                           String pageType,
                                                           String bridgeQueryUrl,
                                                           String bridgeSuggestionsUrl,
                                                           String bridgeOrderLookupUrl,
                                                           String bridgeEventUrl) {
        String defaultConversationMode = defaultConversationModeForShellProfile(DEFAULT_SHELL_MODE_PROFILE);
        return new ShopifyStorefrontBootstrapResponse(
            false,
            store.shopDomain(),
            store.consumerId(),
            store.deploymentId(),
            store.widgetStatus(),
            store.sourceReadinessStatus(),
            "FREE",
            "ACTIVE",
            50,
            true,
            false,
            DEFAULT_LAUNCHER_LABEL,
            DEFAULT_WELCOME_MESSAGE,
            DEFAULT_SHELL_MODE_PROFILE,
            defaultConversationMode,
            resolveEffectiveConversationMode(
                pageType,
                defaultConversationMode,
                DEFAULT_ALLOWED_CONVERSATION_MODES,
                DEFAULT_PAGE_MODE_MAPPINGS
            ),
            DEFAULT_ALLOWED_CONVERSATION_MODES,
            DEFAULT_PAGE_MODE_MAPPINGS,
            DEFAULT_ENABLED_SURFACES,
            groundingSignals(store),
            supportedReviewProviders(store),
            null,
            null,
            bridgeQueryUrl,
            bridgeSuggestionsUrl,
            bridgeOrderLookupUrl,
            bridgeEventUrl,
            false,
            true,
            "Order lookup is unavailable until the store is storefront-ready.",
            new ShopifyStorefrontGovernedActionCapability(
                false,
                false,
                false,
                List.of(),
                List.of(),
                null,
                null,
                "Guided commerce actions are unavailable until the store is storefront-ready."
            ),
            null,
            message
        );
    }

    private List<String> groundingSignals(ShopifyBridgeStoreSummary store) {
        List<String> signals = new ArrayList<>();
        if (store.productsEnabled()) {
            signals.add("Catalog product grounding");
            signals.add("Review-aware product grounding");
        }
        if (store.collectionsEnabled()) {
            signals.add("Collection grounding");
        }
        if (store.pagesEnabled()) {
            signals.add("Store page grounding");
        }
        if (store.policiesEnabled()) {
            signals.add("Policy grounding");
        }
        if (store.articlesEnabled()) {
            signals.add("Published article grounding");
        }
        if (store.metaobjectsEnabled()) {
            signals.add("Metaobject grounding");
        }
        return List.copyOf(signals);
    }

    private List<String> supportedReviewProviders(ShopifyBridgeStoreSummary store) {
        if (!store.productsEnabled()) {
            return List.of();
        }
        if (store.sourcePreflight() != null && store.sourcePreflight().categories() != null) {
            List<String> detected = store.sourcePreflight().categories().stream()
                .filter(category -> "products".equalsIgnoreCase(category.category()))
                .flatMap(category -> category.signals() == null ? java.util.stream.Stream.empty() : category.signals().stream())
                .filter(signal -> signal != null && !signal.isBlank())
                .toList();
            if (!detected.isEmpty()) {
                return List.copyOf(detected);
            }
        }
        return ShopifyProductReviewSignals.supportedProviderLabels();
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        return ShopifyStorefrontInteractionReadinessSupport.firstBlockingReason(
            store,
            "Storefront bootstrap is not available for this store yet."
        );
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private String storefrontUrl(String shopDomain, String suffix) {
        String path = "/api/storefront/shops/" + encodePathSegment(shopDomain) + suffix;
        if (properties.publicBaseUrl().isBlank()) {
            return path;
        }
        String base = properties.publicBaseUrl().endsWith("/")
            ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
            : properties.publicBaseUrl();
        return base + path;
    }

    private List<String> normalizeAllowedConversationModes(List<String> configured, String defaultConversationMode) {
        if (configured == null || configured.isEmpty()) {
            return List.of(defaultConversationMode);
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        configured.forEach(mode -> {
            if (mode != null && !mode.isBlank()) {
                normalized.add(mode.trim().toLowerCase(Locale.ROOT));
            }
        });
        normalized.add(defaultConversationMode.trim().toLowerCase(Locale.ROOT));
        return List.copyOf(normalized);
    }

    private Map<String, String> normalizePageModeMappings(Map<String, String> configured, List<String> allowedConversationModes) {
        if (configured == null || configured.isEmpty()) {
            return DEFAULT_PAGE_MODE_MAPPINGS;
        }
        java.util.LinkedHashMap<String, String> normalized = new java.util.LinkedHashMap<>();
        configured.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                return;
            }
            String normalizedMode = value.trim().toLowerCase(Locale.ROOT);
            if (!allowedConversationModes.contains(normalizedMode)) {
                return;
            }
            normalized.put(key.trim().toLowerCase(Locale.ROOT), normalizedMode);
        });
        return normalized.isEmpty() ? DEFAULT_PAGE_MODE_MAPPINGS : Map.copyOf(normalized);
    }

    private String resolveEffectiveConversationMode(String rawPageType,
                                                    String defaultConversationMode,
                                                    List<String> allowedConversationModes,
                                                    Map<String, String> pageModeMappings) {
        String pageModeKey = pageModeKey(rawPageType);
        String candidate = pageModeMappings.get(pageModeKey);
        if (candidate != null && allowedConversationModes.contains(candidate)) {
            return candidate;
        }
        return allowedConversationModes.contains(defaultConversationMode)
            ? defaultConversationMode
            : allowedConversationModes.getFirst();
    }

    private String defaultConversationModeForShellProfile(String shellModeProfile) {
        if ("GUIDED_SUPPORT".equalsIgnoreCase(shellModeProfile)) {
            return "navigator_deep";
        }
        return DEFAULT_CONVERSATION_MODE;
    }

    private String pageModeKey(String rawPageType) {
        String normalized = rawPageType == null ? "" : rawPageType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "product" -> "product";
            case "collection", "list-collections" -> "collection";
            case "search" -> "search";
            case "cart" -> "cart";
            case "article", "blog", "page" -> "content";
            case "customers/account", "customers/login", "customers/register", "customers/order", "account", "orders" -> "account";
            case "index", "home", "landing" -> "landing";
            default -> "landing";
        };
    }
}
