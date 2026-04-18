package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest;
import org.springframework.stereotype.Service;

@Service
public class ShopifyBridgeInstallCredentialService {

    private final ShopifyTokenExchangeService tokenExchangeService;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyInstallRecordService installRecordService;

    public ShopifyBridgeInstallCredentialService(ShopifyTokenExchangeService tokenExchangeService,
                                                 PlatformShopifyStoreClient platformShopifyStoreClient,
                                                 ShopifyInstallRecordService installRecordService) {
        this.tokenExchangeService = tokenExchangeService;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installRecordService = installRecordService;
    }

    public ShopifyBridgeStoreSummary acquireAndPersist(ShopifyMerchantSession merchantSession,
                                                       String authorizationHeader) {
        ShopifyTokenExchangeMaterial exchanged = tokenExchangeService.exchangeExpiringOfflineToken(merchantSession, authorizationHeader);
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.upsertCredentials(
            merchantSession.shopDomain(),
            new ShopifyBridgeUpsertStoreCredentialsRequest(
                exchanged.accessToken(),
                exchanged.refreshToken(),
                exchanged.accessTokenExpiresAt(),
                exchanged.refreshTokenExpiresAt(),
                exchanged.scopesText(),
                exchanged.expiring()
            )
        );
        if (store.credentials() != null) {
            installRecordService.recordCredentials(
                merchantSession.shopDomain(),
                store.credentials().accessTokenSecretRef(),
                store.credentials().refreshTokenSecretRef(),
                store.credentials().accessTokenExpiresAt(),
                store.credentials().refreshTokenExpiresAt(),
                store.credentials().scopesText()
            );
        }
        return store;
    }

    public ShopifyInstallRecordSummary clearPersistedCredentials(String shopDomain) {
        platformShopifyStoreClient.clearCredentials(shopDomain);
        return installRecordService.clearCredentials(shopDomain).orElse(null);
    }
}
