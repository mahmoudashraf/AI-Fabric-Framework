package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSourceSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontPreviewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShopifyBridgeMerchantStoreService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeProperties properties;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeSourcePreflightService sourcePreflightService;
    private final ShopifyBridgeStoreSyncService storeSyncService;
    private final ShopifyStorefrontPreviewService storefrontPreviewService;

    public ShopifyBridgeMerchantStoreService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                             ShopifyBridgeProperties properties,
                                             ShopifyInstallRecordService installRecordService,
                                             ShopifyBridgeInstallCredentialService installCredentialService,
                                             ShopifyBridgeSourcePreflightService sourcePreflightService,
                                             ShopifyBridgeStoreSyncService storeSyncService,
                                             ShopifyStorefrontPreviewService storefrontPreviewService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.properties = properties;
        this.installRecordService = installRecordService;
        this.installCredentialService = installCredentialService;
        this.sourcePreflightService = sourcePreflightService;
        this.storeSyncService = storeSyncService;
        this.storefrontPreviewService = storefrontPreviewService;
    }

    public ShopifyBridgeMerchantSessionResponse session(ShopifyMerchantSession merchantSession,
                                                        String appBridgeHost) {
        ShopifyInstallRecordSummary installRecord = installRecordService.recordAuthenticatedSession(merchantSession, appBridgeHost);
        return new ShopifyBridgeMerchantSessionResponse(
            merchantSession.shopDomain(),
            merchantSession.destination(),
            merchantSession.userId(),
            merchantSession.expiresAt(),
            installRecord,
            findStoreOrNull(merchantSession.shopDomain())
        );
    }

    public ShopifyBridgeStoreSummary connect(ShopifyMerchantSession merchantSession,
                                             String authorizationHeader) {
        return acquireConnectedCredentials(merchantSession, authorizationHeader).store();
    }

    public ShopifyBridgeStoreSummary runSourcePreflight(ShopifyMerchantSession merchantSession,
                                                        String authorizationHeader) {
        return sourcePreflightService.run(acquireConnectedCredentials(merchantSession, authorizationHeader));
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(ShopifyMerchantSession merchantSession,
                                                         String authorizationHeader) {
        acquireConnectedCredentials(merchantSession, authorizationHeader);
        return platformShopifyStoreClient.bootstrap(merchantSession.shopDomain());
    }

    public ShopifyBridgeStoreSummary goLive(ShopifyMerchantSession merchantSession,
                                            String authorizationHeader) {
        acquireConnectedCredentials(merchantSession, authorizationHeader);
        return platformShopifyStoreClient.goLive(merchantSession.shopDomain());
    }

    public ShopifyBridgeStoreSummary syncNow(ShopifyMerchantSession merchantSession,
                                             String authorizationHeader) {
        return storeSyncService.sync(acquireConnectedCredentials(merchantSession, authorizationHeader));
    }

    public ShopifyStorefrontPreviewResponse storefrontPreview(ShopifyMerchantSession merchantSession) {
        return storefrontPreviewService.preview(merchantSession.shopDomain());
    }

    public ShopifyBridgeStoreSummary updateWidgetSettings(ShopifyMerchantSession merchantSession,
                                                          ShopifyBridgeUpdateWidgetSettingsRequest request) {
        return platformShopifyStoreClient.updateWidgetSettings(merchantSession.shopDomain(), request);
    }

    public ShopifyBridgeStoreSummary updateSourceSettings(ShopifyMerchantSession merchantSession,
                                                          ShopifyBridgeUpdateSourceSettingsRequest request) {
        ShopifyBridgeStoreSummary current = findStoreOrNull(merchantSession.shopDomain());
        boolean productsEnabled = request.productsEnabled() == null || request.productsEnabled();
        boolean collectionsEnabled = request.collectionsEnabled() == null || request.collectionsEnabled();
        boolean pagesEnabled = request.pagesEnabled() == null || request.pagesEnabled();
        boolean policiesEnabled = request.policiesEnabled() == null || request.policiesEnabled();

        if (current == null) {
            return platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                merchantSession.shopDomain(),
                defaultDisplayName(merchantSession.shopDomain()),
                properties.serviceRef(),
                null,
                null,
                null,
                "INSTALLED",
                "NOT_SYNCED",
                "NOT_RUN",
                "NOT_ENABLED",
                "CONNECTED",
                productsEnabled,
                collectionsEnabled,
                pagesEnabled,
                policiesEnabled
            ));
        }

        boolean togglesChanged = current.productsEnabled() != productsEnabled
            || current.collectionsEnabled() != collectionsEnabled
            || current.pagesEnabled() != pagesEnabled
            || current.policiesEnabled() != policiesEnabled;

        if (!togglesChanged) {
            return current;
        }

        return platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
            current.shopDomain(),
            current.displayName(),
            current.productServiceRef(),
            current.customerId(),
            current.deploymentId(),
            current.consumerId(),
            current.installStatus(),
            "NOT_SYNCED",
            "NOT_RUN",
            current.widgetStatus(),
            hasPlatformBindings(current) ? "PLATFORM_BOOTSTRAPPED" : "CONNECTED",
            productsEnabled,
            collectionsEnabled,
            pagesEnabled,
            policiesEnabled
        ));
    }

    private ShopifyBridgeCredentialAcquisition acquireConnectedCredentials(ShopifyMerchantSession merchantSession,
                                                                           String authorizationHeader) {
        ShopifyBridgeStoreSummary current = findStoreOrNull(merchantSession.shopDomain());
        if (current == null) {
            platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                merchantSession.shopDomain(),
                defaultDisplayName(merchantSession.shopDomain()),
                properties.serviceRef(),
                null,
                null,
                null,
                "INSTALLED",
                "NOT_SYNCED",
                "NOT_RUN",
                "NOT_ENABLED",
                "CONNECTED",
                true,
                true,
                true,
                true
            ));
        }
        return installCredentialService.acquireAndPersistMaterial(merchantSession, authorizationHeader);
    }

    private ShopifyBridgeStoreSummary findStoreOrNull(String shopDomain) {
        try {
            return platformShopifyStoreClient.getStore(shopDomain);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Platform denied Shopify store access.", ex);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Platform Shopify admin credentials were rejected.", ex);
        }
    }

    private String defaultDisplayName(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            return "Shopify store";
        }
        return shopDomain.trim().toLowerCase();
    }

    private boolean hasPlatformBindings(ShopifyBridgeStoreSummary store) {
        return store.customerId() != null && !store.customerId().isBlank()
            && store.deploymentId() != null && !store.deploymentId().isBlank()
            && store.consumerId() != null && !store.consumerId().isBlank();
    }
}
