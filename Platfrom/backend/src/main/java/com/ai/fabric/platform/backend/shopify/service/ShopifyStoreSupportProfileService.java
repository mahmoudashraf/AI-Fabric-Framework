package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSupportProfileRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreSupportProfileService {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_POLICY_NOTE_LENGTH = 600;

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreSourcePreflightSupport support;
    private final PlatformAuditService auditService;

    public ShopifyStoreSupportProfileService(ShopifyStoreConnectionRepository repository,
                                             ShopifyStoreSourcePreflightSupport support,
                                             PlatformAuditService auditService) {
        this.repository = repository;
        this.support = support;
        this.auditService = auditService;
    }

    public ShopifyStoreSupportProfileSummary get(String shopDomain) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        return support.summarizeSupportProfile(store.getDetailsJson());
    }

    @Transactional
    public ShopifyStoreSupportProfileSummary update(String shopDomain, UpdateShopifyStoreSupportProfileRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null) {
            throw new ResponseStatusException(CONFLICT, "support profile payload is required.");
        }

        String contactEmail = normalizeEmail(request.contactEmail());
        String contactUrl = normalizeUrl(request.contactUrl(), "contactUrl");
        String helpCenterUrl = normalizeUrl(request.helpCenterUrl(), "helpCenterUrl");
        String orderLookupPageUrl = normalizeUrl(request.orderLookupPageUrl(), "orderLookupPageUrl");
        String supportPolicyNote = normalizePolicyNote(request.supportPolicyNote());
        Instant now = Instant.now();

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode supportProfile = details.putObject("supportProfile");
        writeNullable(supportProfile, "contactEmail", contactEmail);
        writeNullable(supportProfile, "contactUrl", contactUrl);
        writeNullable(supportProfile, "helpCenterUrl", helpCenterUrl);
        writeNullable(supportProfile, "orderLookupPageUrl", orderLookupPageUrl);
        writeNullable(supportProfile, "supportPolicyNote", supportPolicyNote);
        supportProfile.put("updatedAt", now.toString());

        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        ShopifyStoreSupportProfileSummary summary = support.summarizeSupportProfile(store.getDetailsJson());
        auditService.record(
            "SHOPIFY_STORE_SUPPORT_PROFILE_UPDATED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "merchantHandoffConfigured", Boolean.toString(summary.merchantHandoffConfigured()),
                "contactEmailConfigured", Boolean.toString(hasText(summary.contactEmail())),
                "contactUrlConfigured", Boolean.toString(hasText(summary.contactUrl())),
                "helpCenterUrlConfigured", Boolean.toString(hasText(summary.helpCenterUrl())),
                "orderLookupPageUrlConfigured", Boolean.toString(hasText(summary.orderLookupPageUrl()))
            )
        );
        return summary;
    }

    private String normalizeShopDomain(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new ResponseStatusException(CONFLICT, "contactEmail exceeds " + MAX_EMAIL_LENGTH + " characters.");
        }
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new ResponseStatusException(CONFLICT, "contactEmail must be a valid support email address.");
        }
        return normalized;
    }

    private String normalizeUrl(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_URL_LENGTH) {
            throw new ResponseStatusException(CONFLICT, fieldName + " exceeds " + MAX_URL_LENGTH + " characters.");
        }
        if (!(normalized.startsWith("https://") || normalized.startsWith("http://") || normalized.startsWith("/"))) {
            throw new ResponseStatusException(CONFLICT, fieldName + " must start with https://, http://, or /.");
        }
        return normalized;
    }

    private String normalizePolicyNote(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_POLICY_NOTE_LENGTH) {
            throw new ResponseStatusException(CONFLICT, "supportPolicyNote exceeds " + MAX_POLICY_NOTE_LENGTH + " characters.");
        }
        return normalized;
    }

    private void writeNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
            return;
        }
        node.put(field, value);
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
