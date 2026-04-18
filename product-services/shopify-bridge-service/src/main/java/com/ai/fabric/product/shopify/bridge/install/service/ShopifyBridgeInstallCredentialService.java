package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

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
        return acquireAndPersistMaterial(merchantSession, authorizationHeader).store();
    }

    public ShopifyBridgeCredentialAcquisition acquireAndPersistMaterial(ShopifyMerchantSession merchantSession,
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
        return new ShopifyBridgeCredentialAcquisition(store, exchanged);
    }

    public ShopifyInstallRecordSummary clearPersistedCredentials(String shopDomain) {
        platformShopifyStoreClient.clearCredentials(shopDomain);
        return installRecordService.clearCredentials(shopDomain).orElse(null);
    }

    public Optional<ShopifyBridgeCredentialAcquisition> resolvePersistedMaterial(String shopDomain) {
        try {
            ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
            ShopifyBridgeResolvedStoreCredentials resolved = platformShopifyStoreClient.resolveCredentialMaterial(shopDomain);
            if (resolved == null || resolved.accessToken() == null || resolved.accessToken().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ShopifyBridgeCredentialAcquisition(
                store,
                new ShopifyTokenExchangeMaterial(
                    resolved.accessToken(),
                    resolved.refreshToken(),
                    resolved.accessTokenExpiresAt(),
                    resolved.refreshTokenExpiresAt(),
                    resolved.scopesText(),
                    resolved.expiring()
                )
            ));
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 409) {
                return Optional.empty();
            }
            throw ex;
        }
    }
}
