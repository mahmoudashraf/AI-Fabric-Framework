package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
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

    public ShopifyBridgeMerchantStoreService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                             ShopifyBridgeProperties properties,
                                             ShopifyInstallRecordService installRecordService,
                                             ShopifyBridgeInstallCredentialService installCredentialService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.properties = properties;
        this.installRecordService = installRecordService;
        this.installCredentialService = installCredentialService;
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
        return installCredentialService.acquireAndPersist(merchantSession, authorizationHeader);
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(ShopifyMerchantSession merchantSession,
                                                         String authorizationHeader) {
        connect(merchantSession, authorizationHeader);
        return platformShopifyStoreClient.bootstrap(merchantSession.shopDomain());
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
}
