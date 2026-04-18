package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.install.entity.ShopifyInstallRecordEntity;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.repository.ShopifyInstallRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShopifyInstallRecordService {

    private final ShopifyInstallRecordRepository repository;

    public ShopifyInstallRecordService(ShopifyInstallRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ShopifyInstallRecordSummary recordAuthenticatedSession(ShopifyMerchantSession session,
                                                                 String appBridgeHost) {
        Instant now = Instant.now();
        ShopifyInstallRecordEntity entity = repository.findByShopDomainIgnoreCase(normalizeShopDomain(session.shopDomain()))
            .orElseGet(ShopifyInstallRecordEntity::new);
        if (entity.getId() == null) {
            entity.setId("sir-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setCreatedAt(now);
            entity.setInstalledAt(now);
        }
        entity.setShopDomain(normalizeShopDomain(session.shopDomain()));
        entity.setStatus("INSTALLED");
        entity.setShopUrl(session.destination());
        entity.setUserId(session.userId());
        entity.setAppBridgeHost(blankToNull(appBridgeHost));
        entity.setLastAuthenticatedAt(now);
        entity.setUpdatedAt(now);
        return toSummary(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<ShopifyInstallRecordSummary> findByShopDomain(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain)).map(this::toSummary);
    }

    @Transactional
    public ShopifyInstallRecordSummary recordInstall(String shopDomain,
                                                     String shopUrl,
                                                     String appBridgeHost,
                                                     String scopesText,
                                                     String accessTokenSecretRef,
                                                     String refreshTokenSecretRef,
                                                     Instant accessTokenExpiresAt,
                                                     Instant refreshTokenExpiresAt) {
        Instant now = Instant.now();
        ShopifyInstallRecordEntity entity = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseGet(ShopifyInstallRecordEntity::new);
        if (entity.getId() == null) {
            entity.setId("sir-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setCreatedAt(now);
            entity.setInstalledAt(now);
        } else if (entity.getInstalledAt() == null) {
            entity.setInstalledAt(now);
        }
        entity.setShopDomain(normalizeShopDomain(shopDomain));
        entity.setStatus("INSTALLED");
        entity.setShopUrl(blankToNull(shopUrl));
        entity.setAppBridgeHost(blankToNull(appBridgeHost));
        entity.setScopesText(blankToNull(scopesText));
        entity.setAccessTokenSecretRef(blankToNull(accessTokenSecretRef));
        entity.setRefreshTokenSecretRef(blankToNull(refreshTokenSecretRef));
        entity.setAccessTokenExpiresAt(accessTokenExpiresAt);
        entity.setRefreshTokenExpiresAt(refreshTokenExpiresAt);
        entity.setLastUninstalledAt(null);
        entity.setUpdatedAt(now);
        return toSummary(repository.save(entity));
    }

    @Transactional
    public Optional<ShopifyInstallRecordSummary> recordCredentials(String shopDomain,
                                                                   String accessTokenSecretRef,
                                                                   String refreshTokenSecretRef,
                                                                   Instant accessTokenExpiresAt,
                                                                   Instant refreshTokenExpiresAt,
                                                                   String scopesText) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .map(entity -> {
                entity.setAccessTokenSecretRef(blankToNull(accessTokenSecretRef));
                entity.setRefreshTokenSecretRef(blankToNull(refreshTokenSecretRef));
                entity.setAccessTokenExpiresAt(accessTokenExpiresAt);
                entity.setRefreshTokenExpiresAt(refreshTokenExpiresAt);
                entity.setScopesText(blankToNull(scopesText));
                entity.setUpdatedAt(Instant.now());
                return toSummary(repository.save(entity));
            });
    }

    @Transactional
    public Optional<ShopifyInstallRecordSummary> clearCredentials(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .map(entity -> {
                entity.setAccessTokenSecretRef(null);
                entity.setRefreshTokenSecretRef(null);
                entity.setAccessTokenExpiresAt(null);
                entity.setRefreshTokenExpiresAt(null);
                entity.setScopesText(null);
                entity.setUpdatedAt(Instant.now());
                return toSummary(repository.save(entity));
            });
    }

    @Transactional
    public Optional<ShopifyInstallRecordSummary> markUninstalled(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .map(entity -> {
                Instant now = Instant.now();
                entity.setStatus("UNINSTALLED");
                entity.setAccessTokenSecretRef(null);
                entity.setRefreshTokenSecretRef(null);
                entity.setAccessTokenExpiresAt(null);
                entity.setRefreshTokenExpiresAt(null);
                entity.setScopesText(null);
                entity.setLastUninstalledAt(now);
                entity.setUpdatedAt(now);
                return toSummary(repository.save(entity));
            });
    }

    private ShopifyInstallRecordSummary toSummary(ShopifyInstallRecordEntity entity) {
        return new ShopifyInstallRecordSummary(
            entity.getShopDomain(),
            entity.getStatus(),
            entity.getShopUrl(),
            entity.getUserId(),
            entity.getAppBridgeHost(),
            entity.getAccessTokenSecretRef(),
            entity.getRefreshTokenSecretRef(),
            entity.getScopesText(),
            entity.getAccessTokenExpiresAt(),
            entity.getRefreshTokenExpiresAt(),
            entity.getInstalledAt(),
            entity.getLastAuthenticatedAt(),
            entity.getLastUninstalledAt()
        );
    }

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
