package com.ai.fabric.product.shopify.bridge.governedaction.service;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.governedaction.entity.ShopifyBridgeGovernedActionAuditEntity;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCapability;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCompletionRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantResponse;
import com.ai.fabric.product.shopify.bridge.governedaction.repository.ShopifyBridgeGovernedActionAuditRepository;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyStorefrontGovernedActionService {

    private static final Duration GRANT_TTL = Duration.ofMinutes(5);
    private static final int MIN_RECENT_ACTION_LIMIT = 1;
    private static final int MAX_RECENT_ACTION_LIMIT = 25;
    private static final String GUIDED_COMMERCE_PACKAGE = "guided-commerce";
    private static final List<String> ALLOWED_ACTION_TYPES = List.of("ADD_TO_CART", "UPDATE_CART_QUANTITY");

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeGovernedActionAuditRepository repository;
    private final ShopifyStorefrontGovernedActionTokenService tokenService;
    private final ShopifyBridgeUsageService usageService;
    private final Clock clock;

    @Autowired
    public ShopifyStorefrontGovernedActionService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                                  ShopifyBridgeInstallCredentialService installCredentialService,
                                                  ShopifyBridgeBillingService billingService,
                                                  ShopifyBridgeGovernedActionAuditRepository repository,
                                                  ShopifyStorefrontGovernedActionTokenService tokenService,
                                                  ShopifyBridgeUsageService usageService) {
        this(platformShopifyStoreClient, installCredentialService, billingService, repository, tokenService, usageService, Clock.systemUTC());
    }

    ShopifyStorefrontGovernedActionService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                           ShopifyBridgeInstallCredentialService installCredentialService,
                                           ShopifyBridgeBillingService billingService,
                                           ShopifyBridgeGovernedActionAuditRepository repository,
                                           ShopifyStorefrontGovernedActionTokenService tokenService,
                                           ShopifyBridgeUsageService usageService,
                                           Clock clock) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.billingService = billingService;
        this.repository = repository;
        this.tokenService = tokenService;
        this.usageService = usageService;
        this.clock = clock;
    }

    public ShopifyStorefrontGovernedActionCapability capability(String shopDomain,
                                                                String grantUrl,
                                                                String completeUrl) {
        ShopifyBridgeBillingSummary billingSummary = resolveBillingSummary(shopDomain);
        boolean guidedCommerceAvailable = billingSummary.actionCapable()
            && billingSummary.auditTrailAvailable()
            && billingSummary.actionPackages().contains(GUIDED_COMMERCE_PACKAGE);
        String message = guidedCommerceAvailable
            ? "Elite guided commerce actions are enabled for this store."
            : "Activate Elite to unlock governed shopper actions with explicit confirmation and audit trail.";
        return new ShopifyStorefrontGovernedActionCapability(
            guidedCommerceAvailable,
            billingSummary.requiresExplicitConfirmation(),
            billingSummary.auditTrailAvailable(),
            List.copyOf(billingSummary.actionPackages()),
            guidedCommerceAvailable ? ALLOWED_ACTION_TYPES : List.of(),
            guidedCommerceAvailable ? grantUrl : null,
            guidedCommerceAvailable ? completeUrl : null,
            message
        );
    }

    @Transactional
    public ShopifyStorefrontGovernedActionGrantResponse grant(String shopDomain,
                                                              ShopifyStorefrontGovernedActionGrantRequest request,
                                                              String shopperSessionId) {
        String normalizedShop = tokenService.normalizeShopDomain(shopDomain);
        String normalizedSession = tokenService.normalizeShopperSessionId(shopperSessionId);
        ShopifyStorefrontGovernedActionGrantRequest safeRequest = request == null
            ? new ShopifyStorefrontGovernedActionGrantRequest(null, null, null, null, null, null, null, null, null, null, false)
            : request;
        platformShopifyStoreClient.getStore(normalizedShop);
        ShopifyBridgeBillingSummary billingSummary = resolveBillingSummary(normalizedShop);
        requireGuidedCommerceEnabled(billingSummary);

        ActionSpec spec = actionSpec(normalizeActionType(safeRequest.actionType()));
        boolean confirmationRequired = billingSummary.requiresExplicitConfirmation();
        if (confirmationRequired && !safeRequest.confirmationAccepted()) {
            throw new ResponseStatusException(CONFLICT, "Elite guided commerce actions require explicit shopper confirmation.");
        }

        String surfaceId = normalizeEnumLike(safeRequest.surfaceId(), "launcher");
        String pageType = normalizeEnumLike(safeRequest.pageType(), "product");
        String productId = normalizeDigitsOrNull(safeRequest.productId());
        String productHandle = sanitizeText(safeRequest.productHandle(), 255);
        String productTitle = sanitizeText(safeRequest.productTitle(), 255);
        String variantId = normalizeDigitsOrNull(safeRequest.variantId());
        Integer requestedQuantity = normalizeQuantity(safeRequest.requestedQuantity(), 1, 10, spec == ActionSpec.ADD_TO_CART ? 1 : null);
        Integer targetQuantity = normalizeQuantity(safeRequest.targetQuantity(), 0, 25, spec == ActionSpec.UPDATE_CART_QUANTITY ? 1 : null);
        String cartLineKey = sanitizeText(safeRequest.cartLineKey(), 255);

        if (variantId == null) {
            throw new ResponseStatusException(CONFLICT, "A Shopify variant must be selected before guided commerce actions can run.");
        }
        if (spec == ActionSpec.UPDATE_CART_QUANTITY && cartLineKey == null) {
            throw new ResponseStatusException(CONFLICT, "Cart update actions require a resolved cart line key.");
        }
        if (spec == ActionSpec.ADD_TO_CART) {
            targetQuantity = null;
        } else {
            requestedQuantity = null;
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(GRANT_TTL);
        ShopifyBridgeGovernedActionAuditEntity entity = new ShopifyBridgeGovernedActionAuditEntity();
        entity.setId(nextAuditId());
        entity.setShopDomain(normalizedShop);
        entity.setShopperSessionId(normalizedSession);
        entity.setActionType(spec.name());
        entity.setActionPackage(GUIDED_COMMERCE_PACKAGE);
        entity.setSurfaceId(surfaceId);
        entity.setPageType(pageType);
        entity.setProductId(productId);
        entity.setProductHandle(productHandle);
        entity.setProductTitle(productTitle);
        entity.setVariantId(variantId);
        entity.setRequestedQuantity(requestedQuantity);
        entity.setTargetQuantity(targetQuantity);
        entity.setCartLineKey(cartLineKey);
        entity.setConfirmationRequired(confirmationRequired);
        entity.setConfirmationAccepted(safeRequest.confirmationAccepted());
        entity.setStatus("GRANTED");
        entity.setMessage(spec == ActionSpec.ADD_TO_CART
            ? "Guided add-to-cart approval granted."
            : "Guided cart update approval granted.");
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);

        String token = tokenService.issueToken(new ShopifyStorefrontGovernedActionTokenService.TokenClaims(
            entity.getId(),
            normalizedShop,
            normalizedSession,
            spec.name(),
            variantId,
            requestedQuantity,
            targetQuantity,
            cartLineKey,
            expiresAt.getEpochSecond()
        ));
        usageService.recordEvent(normalizedShop, "STOREFRONT_ACTION_GRANTED_" + surfaceId.replace('-', '_').toUpperCase(Locale.ROOT));
        return new ShopifyStorefrontGovernedActionGrantResponse(
            entity.getId(),
            token,
            entity.getActionType(),
            entity.getActionPackage(),
            spec.operationKind(),
            variantId,
            requestedQuantity,
            targetQuantity,
            cartLineKey,
            confirmationRequired,
            expiresAt,
            entity.getMessage()
        );
    }

    @Transactional
    public ShopifyBridgeGovernedActionAuditSummary complete(String shopDomain,
                                                            ShopifyStorefrontGovernedActionCompletionRequest request,
                                                            String shopperSessionId) {
        String normalizedShop = tokenService.normalizeShopDomain(shopDomain);
        String normalizedSession = tokenService.normalizeShopperSessionId(shopperSessionId);
        if (request == null || request.auditId() == null || request.auditId().isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify governed action audit identifier.");
        }
        String auditId = request.auditId().trim();
        tokenService.verify(request.token(), normalizedShop, normalizedSession, auditId);
        ShopifyBridgeGovernedActionAuditEntity entity = repository.findById(auditId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Unknown Shopify governed action audit record."));
        if (!entity.getShopDomain().equals(normalizedShop)) {
            throw new ResponseStatusException(CONFLICT, "Shopify governed action audit record does not match the requested shop.");
        }
        if (!entity.getShopperSessionId().equals(normalizedSession)) {
            throw new ResponseStatusException(CONFLICT, "Shopify governed action audit record does not match the shopper session.");
        }
        if (isTerminalStatus(entity.getStatus())) {
            return toSummary(entity);
        }

        String status = normalizeCompletionStatus(request.status());
        Instant now = clock.instant();
        entity.setStatus(status);
        entity.setMessage(sanitizeText(request.message(), 500));
        entity.setCartLineKey(sanitizeText(request.cartLineKey(), 255));
        entity.setResultingQuantity(normalizeQuantity(request.resultingQuantity(), 0, 25, null));
        entity.setCompletedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        String surfaceEventKey = normalizeEnumLike(entity.getSurfaceId(), "launcher").replace('-', '_').toUpperCase(Locale.ROOT);
        usageService.recordEvent(
            normalizedShop,
            "COMPLETED".equals(status)
                ? "STOREFRONT_ACTION_COMPLETED_" + surfaceEventKey
                : "STOREFRONT_ACTION_FAILED_" + surfaceEventKey
        );
        return toSummary(entity);
    }

    @Transactional(readOnly = true)
    public List<ShopifyBridgeGovernedActionAuditSummary> recentActions(String shopDomain, int limit) {
        String normalizedShop = tokenService.normalizeShopDomain(shopDomain);
        int boundedLimit = Math.min(MAX_RECENT_ACTION_LIMIT, Math.max(MIN_RECENT_ACTION_LIMIT, limit));
        return repository.findByShopDomainIgnoreCaseOrderByCreatedAtDesc(normalizedShop, PageRequest.of(0, boundedLimit)).stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public ShopifyBridgeGovernedActionAuditSummary beginMcpCartTool(String shopDomain,
                                                                    String shopperSessionId,
                                                                    String actionType,
                                                                    boolean mutation,
                                                                    boolean confirmationAccepted,
                                                                    String message) {
        String normalizedShop = tokenService.normalizeShopDomain(shopDomain);
        String normalizedSession = tokenService.normalizeShopperSessionId(shopperSessionId);
        platformShopifyStoreClient.getStore(normalizedShop);
        ShopifyBridgeBillingSummary billingSummary = resolveBillingSummary(normalizedShop);
        requireGuidedCommerceEnabled(billingSummary);
        boolean confirmationRequired = mutation && billingSummary.requiresExplicitConfirmation();
        if (confirmationRequired && !confirmationAccepted) {
            throw new ResponseStatusException(CONFLICT, "Elite guided commerce actions require explicit shopper confirmation.");
        }

        Instant now = clock.instant();
        ShopifyBridgeGovernedActionAuditEntity entity = new ShopifyBridgeGovernedActionAuditEntity();
        entity.setId(nextAuditId());
        entity.setShopDomain(normalizedShop);
        entity.setShopperSessionId(normalizedSession);
        entity.setActionType(normalizeMcpCartActionType(actionType));
        entity.setActionPackage(GUIDED_COMMERCE_PACKAGE);
        entity.setSurfaceId("MAX_MODE");
        entity.setPageType("CART");
        entity.setConfirmationRequired(confirmationRequired);
        entity.setConfirmationAccepted(confirmationAccepted);
        entity.setStatus("STARTED");
        entity.setMessage(sanitizeText(message, 500));
        entity.setExpiresAt(now.plus(GRANT_TTL));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        usageService.recordEvent(normalizedShop, "STOREFRONT_ACTION_STARTED_" + entity.getActionType());
        return toSummary(entity);
    }

    @Transactional
    public ShopifyBridgeGovernedActionAuditSummary completeMcpTool(String auditId,
                                                                   boolean success,
                                                                   String message) {
        if (auditId == null || auditId.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify governed action audit identifier.");
        }
        ShopifyBridgeGovernedActionAuditEntity entity = repository.findById(auditId.trim())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Unknown Shopify governed action audit record."));
        if (isTerminalStatus(entity.getStatus())) {
            return toSummary(entity);
        }
        Instant now = clock.instant();
        entity.setStatus(success ? "COMPLETED" : "FAILED");
        entity.setMessage(sanitizeText(message, 500));
        entity.setCompletedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        usageService.recordEvent(
            entity.getShopDomain(),
            success ? "STOREFRONT_ACTION_COMPLETED_" + entity.getActionType() : "STOREFRONT_ACTION_FAILED_" + entity.getActionType()
        );
        return toSummary(entity);
    }

    private ShopifyBridgeBillingSummary resolveBillingSummary(String shopDomain) {
        return installCredentialService.resolvePersistedMaterial(shopDomain)
            .map(acquisition -> billingService.summarizeForShop(shopDomain, acquisition.tokenExchangeMaterial().accessToken()))
            .orElseGet(() -> billingService.summarizeForShop(shopDomain, null));
    }

    private void requireGuidedCommerceEnabled(ShopifyBridgeBillingSummary billingSummary) {
        if (!billingSummary.actionCapable()
            || !billingSummary.auditTrailAvailable()
            || !billingSummary.actionPackages().contains(GUIDED_COMMERCE_PACKAGE)) {
            throw new ResponseStatusException(CONFLICT, "Elite guided commerce actions are not active for this store.");
        }
    }

    private ActionSpec actionSpec(String normalizedActionType) {
        return switch (normalizedActionType) {
            case "ADD_TO_CART" -> ActionSpec.ADD_TO_CART;
            case "UPDATE_CART_QUANTITY" -> ActionSpec.UPDATE_CART_QUANTITY;
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported Shopify guided commerce action: " + normalizedActionType);
        };
    }

    private String normalizeActionType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify guided commerce action type.");
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String normalizeMcpCartActionType(String value) {
        String normalized = normalizeActionType(value);
        if (!"GET_CART".equals(normalized) && !"UPDATE_CART".equals(normalized)) {
            throw new ResponseStatusException(CONFLICT, "Unsupported Shopify MCP cart action: " + normalized);
        }
        return normalized;
    }

    private String normalizeCompletionStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify guided commerce completion status.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"COMPLETED".equals(normalized) && !"FAILED".equals(normalized)) {
            throw new ResponseStatusException(CONFLICT, "Unsupported Shopify guided commerce completion status: " + value);
        }
        return normalized;
    }

    private String normalizeEnumLike(String value, String fallback) {
        String normalized = sanitizeText(value, 80);
        if (normalized == null) {
            normalized = fallback;
        }
        return normalized.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private String normalizeDigitsOrNull(String value) {
        String normalized = sanitizeText(value, 80);
        if (normalized == null) {
            return null;
        }
        if (!normalized.chars().allMatch(Character::isDigit)) {
            throw new ResponseStatusException(CONFLICT, "Shopify guided commerce identifiers must be numeric.");
        }
        return normalized;
    }

    private Integer normalizeQuantity(Integer value, int min, int max, Integer fallback) {
        Integer normalized = value == null ? fallback : value;
        if (normalized == null) {
            return null;
        }
        if (normalized < min || normalized > max) {
            throw new ResponseStatusException(CONFLICT, "Shopify guided commerce quantity is outside the supported bounds.");
        }
        return normalized;
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String nextAuditId() {
        return "sga-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "EXPIRED".equals(status);
    }

    private ShopifyBridgeGovernedActionAuditSummary toSummary(ShopifyBridgeGovernedActionAuditEntity entity) {
        String shopperSessionId = entity.getShopperSessionId();
        String shopperSessionRef;
        if (shopperSessionId == null || shopperSessionId.length() < 8) {
            shopperSessionRef = shopperSessionId;
        } else {
            shopperSessionRef = shopperSessionId.substring(0, 4) + "…" + shopperSessionId.substring(shopperSessionId.length() - 4);
        }
        return new ShopifyBridgeGovernedActionAuditSummary(
            entity.getId(),
            entity.getActionType(),
            entity.getActionPackage(),
            entity.getSurfaceId(),
            entity.getPageType(),
            entity.getProductHandle(),
            entity.getProductTitle(),
            entity.getVariantId(),
            entity.getRequestedQuantity(),
            entity.getTargetQuantity(),
            entity.getResultingQuantity(),
            entity.isConfirmationRequired(),
            entity.isConfirmationAccepted(),
            shopperSessionRef,
            entity.getStatus(),
            entity.getMessage(),
            entity.getCreatedAt(),
            entity.getExpiresAt(),
            entity.getCompletedAt()
        );
    }

    private enum ActionSpec {
        ADD_TO_CART("CART_ADD"),
        UPDATE_CART_QUANTITY("CART_CHANGE");

        private final String operationKind;

        ActionSpec(String operationKind) {
            this.operationKind = operationKind;
        }

        public String operationKind() {
            return operationKind;
        }
    }
}
