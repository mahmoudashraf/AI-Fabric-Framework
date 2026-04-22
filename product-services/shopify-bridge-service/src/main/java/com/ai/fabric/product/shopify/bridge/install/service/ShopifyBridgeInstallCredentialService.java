package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyBridgeInstallCredentialService {

    private static final Duration ACCESS_TOKEN_REFRESH_SKEW = Duration.ofMinutes(5);

    private final ShopifyTokenExchangeService tokenExchangeService;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyWebhookSubscriptionService webhookSubscriptionService;

    public ShopifyBridgeInstallCredentialService(ShopifyTokenExchangeService tokenExchangeService,
                                                 PlatformShopifyStoreClient platformShopifyStoreClient,
                                                 ShopifyInstallRecordService installRecordService,
                                                 ShopifyWebhookSubscriptionService webhookSubscriptionService) {
        this.tokenExchangeService = tokenExchangeService;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installRecordService = installRecordService;
        this.webhookSubscriptionService = webhookSubscriptionService;
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
        webhookSubscriptionService.reconcileContentSubscriptions(merchantSession.shopDomain(), exchanged.accessToken());
        return new ShopifyBridgeCredentialAcquisition(store, exchanged);
    }

    public ShopifyInstallRecordSummary clearPersistedCredentials(String shopDomain) {
        platformShopifyStoreClient.clearCredentials(shopDomain);
        return clearLocalPersistedCredentials(shopDomain);
    }

    public ShopifyInstallRecordSummary clearLocalPersistedCredentials(String shopDomain) {
        return installRecordService.clearCredentials(shopDomain).orElse(null);
    }

    public Optional<ShopifyBridgeCredentialAcquisition> resolvePersistedMaterial(String shopDomain) {
        try {
            ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
            ShopifyBridgeResolvedStoreCredentials resolved = platformShopifyStoreClient.resolveCredentialMaterial(shopDomain);
            if (resolved == null || resolved.accessToken() == null || resolved.accessToken().isBlank()) {
                return Optional.empty();
            }
            ShopifyTokenExchangeMaterial material = new ShopifyTokenExchangeMaterial(
                resolved.accessToken(),
                resolved.refreshToken(),
                resolved.accessTokenExpiresAt(),
                resolved.refreshTokenExpiresAt(),
                resolved.scopesText(),
                resolved.expiring()
            );
            if (shouldRefresh(material)) {
                material = refreshPersistedMaterial(shopDomain, material);
                store = platformShopifyStoreClient.getStore(shopDomain);
            }
            return Optional.of(new ShopifyBridgeCredentialAcquisition(store, material));
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 409) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private boolean shouldRefresh(ShopifyTokenExchangeMaterial material) {
        if (material == null || !material.expiring()) {
            return false;
        }
        if (material.accessToken() == null || material.accessToken().isBlank()) {
            return false;
        }
        if (material.accessTokenExpiresAt() == null) {
            return false;
        }
        return !material.accessTokenExpiresAt().isAfter(java.time.Instant.now().plus(ACCESS_TOKEN_REFRESH_SKEW));
    }

    private ShopifyTokenExchangeMaterial refreshPersistedMaterial(String shopDomain,
                                                                  ShopifyTokenExchangeMaterial current) {
        if (current.refreshToken() == null || current.refreshToken().isBlank()) {
            throw new ResponseStatusException(
                CONFLICT,
                "Persisted Shopify offline token expired and no refresh token is available. Reconnect the app."
            );
        }
        if (current.refreshTokenExpiresAt() != null && !current.refreshTokenExpiresAt().isAfter(java.time.Instant.now())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Persisted Shopify refresh token has expired. Reconnect the app to continue background Shopify access."
            );
        }
        ShopifyTokenExchangeMaterial refreshed =
            tokenExchangeService.refreshExpiringOfflineToken(shopDomain, current.refreshToken());
        ShopifyBridgeStoreSummary updatedStore = platformShopifyStoreClient.upsertCredentials(
            shopDomain,
            new com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest(
                refreshed.accessToken(),
                refreshed.refreshToken(),
                refreshed.accessTokenExpiresAt(),
                refreshed.refreshTokenExpiresAt(),
                refreshed.scopesText(),
                refreshed.expiring()
            )
        );
        if (updatedStore.credentials() != null) {
            installRecordService.recordCredentials(
                shopDomain,
                updatedStore.credentials().accessTokenSecretRef(),
                updatedStore.credentials().refreshTokenSecretRef(),
                updatedStore.credentials().accessTokenExpiresAt(),
                updatedStore.credentials().refreshTokenExpiresAt(),
                updatedStore.credentials().scopesText()
            );
        }
        return refreshed;
    }
}
