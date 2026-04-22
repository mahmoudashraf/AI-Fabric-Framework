package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreResolvedCredentialsSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreCredentialsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreCredentialService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService connectionService;
    private final ShopifyStoreSourcePreflightSupport sourcePreflightSupport;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreCredentialService(ShopifyStoreConnectionRepository repository,
                                         ShopifyStoreConnectionService connectionService,
                                         ShopifyStoreSourcePreflightSupport sourcePreflightSupport,
                                         PlatformSecretService platformSecretService,
                                         PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.connectionService = connectionService;
        this.sourcePreflightSupport = sourcePreflightSupport;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary upsert(String shopDomain, UpsertShopifyStoreCredentialsRequest request) {
        ShopifyStoreConnectionEntity entity = requireConnection(shopDomain);
        String accessToken = requireText(request.accessToken(), "accessToken is required.");
        String refreshToken = normalize(request.refreshToken());
        boolean expiring = request.expiring() == null || request.expiring();
        if (expiring && refreshToken == null) {
            throw new ResponseStatusException(CONFLICT, "refreshToken is required when expiring offline credentials are used.");
        }

        String accessTokenSecretRef = accessTokenSecretRef(entity.getShopDomain());
        String refreshTokenSecretRef = refreshToken == null ? null : refreshTokenSecretRef(entity.getShopDomain());
        Map<String, Object> auditDetails = Map.of(
            "shopDomain", entity.getShopDomain(),
            "deploymentId", normalize(entity.getDeploymentId()) == null ? "" : entity.getDeploymentId(),
            "customerId", normalize(entity.getCustomerId()) == null ? "" : entity.getCustomerId(),
            "productServiceId", entity.getProductServiceId(),
            "purpose", "SHOPIFY_STORE_CREDENTIALS"
        );

        platformSecretService.upsertManagedSecret(accessTokenSecretRef, accessToken, auditDetails);
        if (refreshTokenSecretRef != null) {
            platformSecretService.upsertManagedSecret(refreshTokenSecretRef, refreshToken, auditDetails);
        }

        Instant now = Instant.now();
        ObjectNode details = sourcePreflightSupport.mutableDetails(entity.getDetailsJson());
        ObjectNode credentials = details.with("credentials");
        credentials.put("status", "READY");
        credentials.put("checkedAt", now.toString());
        credentials.put("accessTokenSecretRef", accessTokenSecretRef);
        if (refreshTokenSecretRef != null) {
            credentials.put("refreshTokenSecretRef", refreshTokenSecretRef);
        } else {
            credentials.remove("refreshTokenSecretRef");
        }
        putOrRemove(credentials, "accessTokenExpiresAt", request.accessTokenExpiresAt());
        putOrRemove(credentials, "refreshTokenExpiresAt", request.refreshTokenExpiresAt());
        putOrRemove(credentials, "scopesText", request.scopesText());
        credentials.put("expiring", expiring);

        if (!"INSTALLED".equalsIgnoreCase(entity.getInstallStatus())) {
            entity.setInstallStatus("INSTALLED");
            entity.setSyncStatus("NOT_SYNCED");
            entity.setSourceReadinessStatus("NOT_RUN");
            entity.setWidgetStatus("NOT_ENABLED");
        }
        if (shouldRestoreOnboarding(entity.getOnboardingStatus())) {
            entity.setOnboardingStatus(hasPlatformBindings(entity) ? "PLATFORM_BOOTSTRAPPED" : "CONNECTED");
        }

        entity.setDetailsJson(sourcePreflightSupport.writeJson(details));
        entity.setUpdatedAt(now);
        repository.save(entity);

        platformAuditService.record(
            "SHOPIFY_STORE_CREDENTIALS_UPSERTED",
            "SHOPIFY_STORE_CONNECTION",
            entity.getShopDomain(),
            Map.of(
                "shopDomain", entity.getShopDomain(),
                "accessTokenSecretRef", accessTokenSecretRef,
                "refreshTokenSecretRef", refreshTokenSecretRef == null ? "" : refreshTokenSecretRef,
                "expiring", Boolean.toString(expiring)
            )
        );
        return connectionService.getConnection(entity.getShopDomain());
    }

    @Transactional
    public ShopifyStoreConnectionSummary clear(String shopDomain) {
        ShopifyStoreConnectionEntity entity = requireConnection(shopDomain);
        ObjectNode details = sourcePreflightSupport.mutableDetails(entity.getDetailsJson());
        String accessTokenSecretRef = normalize(details.path("credentials").path("accessTokenSecretRef").asText(null));
        String refreshTokenSecretRef = normalize(details.path("credentials").path("refreshTokenSecretRef").asText(null));
        if (accessTokenSecretRef != null && platformSecretService.isManagedSecretName(accessTokenSecretRef)) {
            platformSecretService.clearManagedSecret(accessTokenSecretRef, Map.of("shopDomain", entity.getShopDomain(), "purpose", "SHOPIFY_STORE_CREDENTIALS"));
        }
        if (refreshTokenSecretRef != null && platformSecretService.isManagedSecretName(refreshTokenSecretRef)) {
            platformSecretService.clearManagedSecret(refreshTokenSecretRef, Map.of("shopDomain", entity.getShopDomain(), "purpose", "SHOPIFY_STORE_CREDENTIALS"));
        }

        ObjectNode credentials = details.with("credentials");
        credentials.put("status", "MISSING");
        credentials.put("checkedAt", Instant.now().toString());
        credentials.remove("accessTokenSecretRef");
        credentials.remove("refreshTokenSecretRef");
        credentials.remove("accessTokenExpiresAt");
        credentials.remove("refreshTokenExpiresAt");
        credentials.remove("scopesText");
        credentials.put("expiring", false);

        entity.setDetailsJson(sourcePreflightSupport.writeJson(details));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        platformAuditService.record(
            "SHOPIFY_STORE_CREDENTIALS_CLEARED",
            "SHOPIFY_STORE_CONNECTION",
            entity.getShopDomain(),
            Map.of("shopDomain", entity.getShopDomain())
        );
        return connectionService.getConnection(entity.getShopDomain());
    }

    @Transactional(readOnly = true)
    public ShopifyStoreResolvedCredentialsSummary resolveMaterial(String shopDomain) {
        ShopifyStoreConnectionEntity entity = requireConnection(shopDomain);
        JsonNode details = sourcePreflightSupport.readJsonNode(entity.getDetailsJson());
        JsonNode credentials = details == null ? null : details.path("credentials");
        String accessTokenSecretRef = normalize(credentials == null ? null : credentials.path("accessTokenSecretRef").asText(null));
        String refreshTokenSecretRef = normalize(credentials == null ? null : credentials.path("refreshTokenSecretRef").asText(null));
        String accessToken = normalize(accessTokenSecretRef == null ? null : platformSecretService.resolveSecret(accessTokenSecretRef));
        if (accessToken == null) {
            throw new ResponseStatusException(CONFLICT, "Shopify store credentials are not available for " + entity.getShopDomain() + ".");
        }
        String refreshToken = normalize(refreshTokenSecretRef == null ? null : platformSecretService.resolveSecret(refreshTokenSecretRef));
        Instant accessTokenExpiresAt = parseInstant(credentials, "accessTokenExpiresAt");
        Instant refreshTokenExpiresAt = parseInstant(credentials, "refreshTokenExpiresAt");
        String scopesText = normalize(credentials == null ? null : credentials.path("scopesText").asText(null));
        boolean expiring = credentials != null && credentials.path("expiring").asBoolean(refreshToken != null);
        return new ShopifyStoreResolvedCredentialsSummary(
            accessToken,
            refreshToken,
            accessTokenExpiresAt,
            refreshTokenExpiresAt,
            scopesText,
            expiring
        );
    }

    public static String accessTokenSecretRef(String shopDomain) {
        return managedSecretName("MANAGED_SHOPIFY_ACCESS_TOKEN", shopDomain);
    }

    public static String refreshTokenSecretRef(String shopDomain) {
        return managedSecretName("MANAGED_SHOPIFY_REFRESH_TOKEN", shopDomain);
    }

    private static String managedSecretName(String prefix, String shopDomain) {
        String normalized = normalizeShopDomain(shopDomain);
        String slug = normalized.replaceAll("[^a-z0-9]+", "_");
        if (slug.length() > 32) {
            slug = slug.substring(0, 32);
        }
        String hash = sha256(normalized).substring(0, 12).toUpperCase(Locale.ROOT);
        return (prefix + "_" + slug + "_" + hash).toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive Shopify managed secret name.", ex);
        }
    }

    private ShopifyStoreConnectionEntity requireConnection(String shopDomain) {
        return repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
    }

    private static void putOrRemove(ObjectNode node, String field, Instant value) {
        if (value == null) {
            node.remove(field);
        } else {
            node.put(field, value.toString());
        }
    }

    private static void putOrRemove(ObjectNode node, String field, String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            node.remove(field);
        } else {
            node.put(field, normalized);
        }
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ResponseStatusException(CONFLICT, message);
        }
        return normalized;
    }

    private static String normalizeShopDomain(String shopDomain) {
        return normalize(shopDomain) == null ? "" : normalize(shopDomain).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant parseInstant(JsonNode node, String field) {
        String value = normalize(node == null ? null : node.path(field).asText(null));
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(CONFLICT, "Shopify store credentials contain an invalid " + field + " value.");
        }
    }

    private boolean shouldRestoreOnboarding(String onboardingStatus) {
        String normalized = normalize(onboardingStatus);
        return normalized == null
            || "BLOCKED".equalsIgnoreCase(normalized)
            || "NOT_STARTED".equalsIgnoreCase(normalized)
            || "UNINSTALLED".equalsIgnoreCase(normalized);
    }

    private boolean hasPlatformBindings(ShopifyStoreConnectionEntity entity) {
        return normalize(entity.getCustomerId()) != null
            && normalize(entity.getDeploymentId()) != null
            && normalize(entity.getConsumerId()) != null;
    }
}
