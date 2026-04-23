package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyProductReviewSignals;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShopifyStorefrontBootstrapService {

    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";
    private static final String DEFAULT_SHELL_MODE_PROFILE = "SHOPIFY_COMPANION";
    private static final List<String> DEFAULT_ENABLED_SURFACES = List.of("ai-search");

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyStorefrontGovernedActionService governedActionService;
    private final ShopifyBridgeProperties properties;

    public ShopifyStorefrontBootstrapService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                             ShopifyBridgeInstallCredentialService installCredentialService,
                                             ShopifyBridgeBillingService billingService,
                                             ShopifyStorefrontGovernedActionService governedActionService,
                                             ShopifyBridgeProperties properties) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.billingService = billingService;
        this.governedActionService = governedActionService;
        this.properties = properties;
    }

    public ShopifyStorefrontBootstrapResponse bootstrap(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (store.readiness() == null || !store.readiness().storefrontReady()) {
            return unavailable(store, firstStorefrontBlockingReason(store));
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

        String bridgeQueryUrl = storefrontUrl(updated.shopDomain(), "/chat/query");
        String bridgeSuggestionsUrl = storefrontUrl(updated.shopDomain(), "/chat/suggestions");
        String bridgeReadActionUrl = storefrontUrl(updated.shopDomain(), "/actions/read");
        String bridgeEventUrl = storefrontUrl(updated.shopDomain(), "/events");
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
        List<String> enabledSurfaces = updated.widgetDetail() != null
            && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().enabledSurfaces() != null
            && !updated.widgetDetail().settings().enabledSurfaces().isEmpty()
            ? List.copyOf(updated.widgetDetail().settings().enabledSurfaces())
            : billingSummary.allowedSurfaces();
        enabledSurfaces = billingService.effectiveAllowedSurfaces(updated.shopDomain(), null, enabledSurfaces);
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
            enabledSurfaces,
            groundingSignals,
            supportedReviewProviders,
            preferredIntegrationMode,
            runtimeAuthMode,
            bridgeQueryUrl,
            bridgeSuggestionsUrl,
            bridgeReadActionUrl,
            bridgeEventUrl,
            actionCapability,
            guidance,
            "Storefront bootstrap resolved. Theme app extension can now call the bridge-backed shopper endpoints."
        );
    }

    private ShopifyStorefrontBootstrapResponse unavailable(ShopifyBridgeStoreSummary store, String message) {
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
            DEFAULT_ENABLED_SURFACES,
            List.of("Catalog product grounding", "Policy grounding"),
            supportedReviewProviders(store),
            null,
            null,
            null,
            null,
            null,
            null,
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
        if (store.readiness() != null && !store.readiness().storefrontBlockingReasons().isEmpty()) {
            return store.readiness().storefrontBlockingReasons().get(0);
        }
        return "Storefront bootstrap is not available for this store yet.";
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
}
