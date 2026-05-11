package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyProductReviewSignals;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ShopifyStorefrontBootstrapService {

    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Shopping Assistant is ready. Ask about products, policies, or collections.";
    private static final String DEFAULT_SHELL_MODE_PROFILE = "SHOPIFY_COMPANION";
    private static final boolean DEFAULT_DEBUG_ENABLED = false;
    private static final boolean DEFAULT_ASSISTANT_DOCK_ENABLED = true;
    private static final boolean DEFAULT_ASK_ASSISTANT_LAUNCHER_ENABLED = false;
    private static final String BASE_NAVIGATOR_CONVERSATION_MODE = "navigator";
    private static final String THINKER_CONVERSATION_MODE = "thinker_deep";
    private static final String RESOLVER_CONVERSATION_MODE = "executor";
    private static final String DEFAULT_CONVERSATION_MODE = THINKER_CONVERSATION_MODE;
    private static final List<String> DEFAULT_ENABLED_SURFACES = List.of("ai-search");
    private static final Map<String, String> DEFAULT_PAGE_MODE_MAPPINGS = Map.of();
    private static final Set<String> CANONICAL_CONVERSATION_MODES = Set.of(
        "navigator",
        "navigator_deep",
        THINKER_CONVERSATION_MODE,
        "cart_assistant",
        "executor"
    );
    private static final Set<String> ACTION_CONVERSATION_MODES = Set.of(
        "cart_assistant",
        "executor"
    );

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
        String customerAccountAuthStartUrl = customerAuthUrl(store.shopDomain(), "/start");
        String customerAccountAuthSessionUrl = customerAuthUrl(store.shopDomain(), "/session");
        if (!ShopifyStorefrontInteractionReadinessSupport.isReady(store)) {
            return unavailable(
                store,
                firstStorefrontBlockingReason(store),
                pageType,
                bridgeQueryUrl,
                bridgeSuggestionsUrl,
                bridgeOrderLookupUrl,
                bridgeEventUrl,
                customerAccountAuthStartUrl,
                customerAccountAuthSessionUrl
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
        ShopifyBridgeCredentialAcquisition credentialAcquisition =
            installCredentialService.resolvePersistedMaterial(updated.shopDomain()).orElse(null);
        String storefrontAccessToken = credentialAcquisition == null
            ? null
            : credentialAcquisition.tokenExchangeMaterial().accessToken();
        ShopifyBridgeBillingSummary billingSummary = billingService.summarizeForShop(updated.shopDomain(), storefrontAccessToken);
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
        boolean debugEnabled = updated.widgetDetail() != null
            && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().debugEnabled();
        boolean assistantDockEnabled = updated.widgetDetail() == null
            || updated.widgetDetail().settings() == null
            || updated.widgetDetail().settings().assistantDockEnabled() == null
            || updated.widgetDetail().settings().assistantDockEnabled();
        boolean askAssistantLauncherEnabled = updated.widgetDetail() != null
            && updated.widgetDetail().settings() != null
            && Boolean.TRUE.equals(updated.widgetDetail().settings().askAssistantLauncherEnabled());
        String configuredDefaultConversationMode = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().defaultConversationMode() != null
            && !updated.widgetDetail().settings().defaultConversationMode().isBlank()
            ? updated.widgetDetail().settings().defaultConversationMode().trim()
            : defaultConversationModeForShellProfile(shellModeProfile);
        String defaultConversationMode = resolveDefaultConversationMode(
            configuredDefaultConversationMode,
            shellModeProfile,
            billingSummary,
            resolverModeEligible(updated, billingSummary)
        );
        List<String> allowedConversationModes = normalizeAllowedConversationModes(
            updated.widgetDetail() != null && updated.widgetDetail().settings() != null
                ? updated.widgetDetail().settings().allowedConversationModes()
                : null,
            defaultConversationMode,
            billingSummary,
            resolverModeEligible(updated, billingSummary)
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
        enabledSurfaces = billingService.effectiveAllowedSurfaces(updated.shopDomain(), storefrontAccessToken, enabledSurfaces);
        String scopesText = installRecordService.findByShopDomain(updated.shopDomain())
            .map(summary -> summary.scopesText() == null ? null : summary.scopesText())
            .orElseGet(() -> credentialAcquisition == null ? null : credentialAcquisition.tokenExchangeMaterial().scopesText());
        List<String> billingAllowedSurfaces = billingSummary.allowedSurfaces() == null
            ? List.of()
            : billingSummary.allowedSurfaces();
        boolean orderLookupInTier = billingAllowedSurfaces.contains("order-lookup");
        boolean orderLookupConfigured = enabledSurfaces.contains("order-lookup");
        boolean orderLookupScopeGranted = ShopifyScopeSupport.hasScope(scopesText, "read_orders");
        boolean orderLookupEnabled = orderLookupInTier && orderLookupConfigured && orderLookupScopeGranted;
        boolean olderOrdersRequireBroaderScope = orderLookupEnabled && !ShopifyScopeSupport.hasScope(scopesText, "read_all_orders");
        String orderLookupMessage = orderLookupEnabled
            ? (olderOrdersRequireBroaderScope
                ? "Order lookup is available with the exact order number and checkout email. Orders older than Shopify's default window still require broader order access."
                : "Order lookup is available with the exact order number and checkout email.")
            : !orderLookupInTier
                ? "Order lookup is available only on Elite stores with verified support access."
                : !orderLookupConfigured
                    ? "Order lookup is disabled in the storefront surface configuration."
                    : "Order lookup is waiting for Shopify order-read scope approval on this store.";
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
            debugEnabled,
            assistantDockEnabled,
            askAssistantLauncherEnabled,
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
            customerAccountAuthStartUrl,
            customerAccountAuthSessionUrl,
            orderLookupEnabled,
            olderOrdersRequireBroaderScope,
            orderLookupMessage,
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
                                                           String bridgeEventUrl,
                                                           String customerAccountAuthStartUrl,
                                                           String customerAccountAuthSessionUrl) {
        String defaultConversationMode = BASE_NAVIGATOR_CONVERSATION_MODE;
        List<String> allowedConversationModes = List.of(defaultConversationMode);
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
            DEFAULT_DEBUG_ENABLED,
            DEFAULT_ASSISTANT_DOCK_ENABLED,
            DEFAULT_ASK_ASSISTANT_LAUNCHER_ENABLED,
            defaultConversationMode,
            resolveEffectiveConversationMode(
                pageType,
                defaultConversationMode,
                allowedConversationModes,
                DEFAULT_PAGE_MODE_MAPPINGS
            ),
            allowedConversationModes,
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
            customerAccountAuthStartUrl,
            customerAccountAuthSessionUrl,
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

    private String customerAuthUrl(String shopDomain, String suffix) {
        String path = "/api/customer-auth" + suffix;
        UriComponentsBuilder builder = properties.publicBaseUrl().isBlank()
            ? UriComponentsBuilder.fromPath(path)
            : UriComponentsBuilder.fromHttpUrl(normalizedPublicBaseUrl() + path);
        return builder.queryParam("shop", shopDomain).build().toUriString();
    }

    private String normalizedPublicBaseUrl() {
        return properties.publicBaseUrl().endsWith("/")
            ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
            : properties.publicBaseUrl();
    }

    private String resolveDefaultConversationMode(String configuredDefaultConversationMode,
                                                  String shellModeProfile,
                                                  ShopifyBridgeBillingSummary billingSummary,
                                                  boolean resolverModeEligible) {
        String resolved = normalizeConversationMode(configuredDefaultConversationMode, billingSummary, resolverModeEligible)
            .or(() -> normalizeConversationMode(defaultConversationModeForShellProfile(shellModeProfile), billingSummary, resolverModeEligible))
            .orElse(fallbackConversationMode(billingSummary));
        return promoteDepthModeToThinker(resolved, billingSummary);
    }

    private String promoteDepthModeToThinker(String mode, ShopifyBridgeBillingSummary billingSummary) {
        if (billingSummary == null || !billingSummary.chatFallbackEnabled()) {
            return mode;
        }
        if (BASE_NAVIGATOR_CONVERSATION_MODE.equals(mode) || "navigator_deep".equals(mode)) {
            return THINKER_CONVERSATION_MODE;
        }
        return mode;
    }

    private Optional<String> normalizeConversationMode(String value,
                                                       ShopifyBridgeBillingSummary billingSummary,
                                                       boolean resolverModeEligible) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!CANONICAL_CONVERSATION_MODES.contains(normalized)) {
            return Optional.empty();
        }
        if (!conversationModeEntitled(normalized, billingSummary, resolverModeEligible)) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private boolean conversationModeEntitled(String mode,
                                             ShopifyBridgeBillingSummary billingSummary,
                                             boolean resolverModeEligible) {
        if (BASE_NAVIGATOR_CONVERSATION_MODE.equals(mode)) {
            return true;
        }
        if ("navigator_deep".equals(mode) || THINKER_CONVERSATION_MODE.equals(mode)) {
            return billingSummary != null && billingSummary.chatFallbackEnabled();
        }
        if (ACTION_CONVERSATION_MODES.contains(mode)) {
            return billingSummary != null && billingSummary.actionCapable() && resolverModeEligible;
        }
        return false;
    }

    private List<String> normalizeAllowedConversationModes(List<String> configured,
                                                           String defaultConversationMode,
                                                           ShopifyBridgeBillingSummary billingSummary,
                                                           boolean resolverModeEligible) {
        if (configured == null || configured.isEmpty()) {
            java.util.LinkedHashSet<String> defaults = new java.util.LinkedHashSet<>();
            defaults.add(defaultConversationMode);
            if (resolverModeEligible) {
                defaults.add(RESOLVER_CONVERSATION_MODE);
            }
            return List.copyOf(defaults);
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        configured.forEach(mode -> {
            normalizeConversationMode(mode, billingSummary, resolverModeEligible).ifPresent(normalized::add);
        });
        normalized.add(defaultConversationMode.trim().toLowerCase(Locale.ROOT));
        return List.copyOf(normalized);
    }

    private Map<String, String> normalizePageModeMappings(Map<String, String> configured, List<String> allowedConversationModes) {
        java.util.LinkedHashMap<String, String> normalized = new java.util.LinkedHashMap<>(
            defaultPageModeMappings(allowedConversationModes)
        );
        if (configured == null || configured.isEmpty()) {
            return normalized.isEmpty() ? DEFAULT_PAGE_MODE_MAPPINGS : Map.copyOf(normalized);
        }
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

    private Map<String, String> defaultPageModeMappings(List<String> allowedConversationModes) {
        if (allowedConversationModes == null || allowedConversationModes.isEmpty()) {
            return DEFAULT_PAGE_MODE_MAPPINGS;
        }
        String shoppingMode = allowedConversationModes.contains(THINKER_CONVERSATION_MODE)
            ? THINKER_CONVERSATION_MODE
            : allowedConversationModes.getFirst();
        String accountOrderMode = allowedConversationModes.contains(RESOLVER_CONVERSATION_MODE)
            ? RESOLVER_CONVERSATION_MODE
            : shoppingMode;
        return Map.ofEntries(
            Map.entry("landing", shoppingMode),
            Map.entry("product", shoppingMode),
            Map.entry("collection", shoppingMode),
            Map.entry("search", shoppingMode),
            Map.entry("content", shoppingMode),
            Map.entry("cart", accountOrderMode),
            Map.entry("account", accountOrderMode),
            Map.entry("support", accountOrderMode)
        );
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
            return THINKER_CONVERSATION_MODE;
        }
        return THINKER_CONVERSATION_MODE;
    }

    private String fallbackConversationMode(ShopifyBridgeBillingSummary billingSummary) {
        return billingSummary != null && billingSummary.chatFallbackEnabled()
            ? THINKER_CONVERSATION_MODE
            : BASE_NAVIGATOR_CONVERSATION_MODE;
    }

    private String pageModeKey(String rawPageType) {
        String normalized = rawPageType == null ? "" : rawPageType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "product" -> "product";
            case "collection", "list-collections" -> "collection";
            case "search" -> "search";
            case "cart" -> "cart";
            case "contact", "support", "help", "returns", "return" -> "support";
            case "article", "blog", "page" -> "content";
            case "customers/account", "customers/login", "customers/register", "customers/order", "account", "orders",
                "order", "order_status", "order-status" -> "account";
            case "index", "home", "landing" -> "landing";
            default -> "landing";
        };
    }

    private boolean resolverModeEligible(ShopifyBridgeStoreSummary store, ShopifyBridgeBillingSummary billingSummary) {
        return billingSummary != null
            && billingSummary.actionCapable()
            && store != null
            && store.readiness() != null
            && store.readiness().goLiveEligible()
            && store.readiness().storefrontReady();
    }
}
