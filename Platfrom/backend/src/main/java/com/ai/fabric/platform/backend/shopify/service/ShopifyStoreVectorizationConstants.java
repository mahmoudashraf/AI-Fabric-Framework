package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class ShopifyStoreVectorizationConstants {

    static final String SOURCE_PRODUCTS = "products";
    static final String SOURCE_COLLECTIONS = "collections";
    static final String SOURCE_PAGES = "pages";
    static final String SOURCE_POLICIES = "policies";
    static final String SOURCE_ARTICLES = "articles";
    static final List<String> SOURCE_CATEGORIES = List.of(
        SOURCE_PRODUCTS,
        SOURCE_COLLECTIONS,
        SOURCE_PAGES,
        SOURCE_POLICIES,
        SOURCE_ARTICLES
    );

    static final String OPERATION_CREATE = "CREATE";
    static final String OPERATION_UPDATE = "UPDATE";
    static final String OPERATION_DELETE = "DELETE";

    static final String UPDATE_MODE_NONE = "NONE";
    static final String UPDATE_MODE_ANY = "ANY_UPDATE";
    static final String UPDATE_MODE_INDEXED_ONLY = "INDEXED_FIELDS_ONLY";
    static final String UPDATE_MODE_SELECTED = "SELECTED_INDEXED_FIELDS";
    static final Set<String> UPDATE_TRIGGER_MODES = Set.of(
        UPDATE_MODE_NONE,
        UPDATE_MODE_ANY,
        UPDATE_MODE_INDEXED_ONLY,
        UPDATE_MODE_SELECTED
    );

    static final String EVENT_STATUS_QUEUED = "QUEUED";
    static final String EVENT_STATUS_LEASED = "LEASED";
    static final String EVENT_STATUS_COALESCED = "COALESCED";
    static final String EVENT_STATUS_DISPATCHED = "DISPATCHED";
    static final String EVENT_STATUS_COMPLETED = "COMPLETED";
    static final String EVENT_STATUS_SKIPPED = "SKIPPED";
    static final String EVENT_STATUS_FAILED = "FAILED";
    static final String EVENT_STATUS_DEAD_LETTERED = "DEAD_LETTERED";
    static final Set<String> ACTIVE_EVENT_STATUSES = Set.of(
        EVENT_STATUS_QUEUED,
        EVENT_STATUS_LEASED,
        EVENT_STATUS_FAILED
    );
    static final Set<String> TERMINAL_EVENT_STATUSES = Set.of(
        EVENT_STATUS_COALESCED,
        EVENT_STATUS_COMPLETED,
        EVENT_STATUS_SKIPPED,
        EVENT_STATUS_DEAD_LETTERED
    );

    static final String TRIGGER_REASON_CREATE = "CREATE_TRIGGER";
    static final String TRIGGER_REASON_DELETE = "DELETE_TRIGGER";
    static final String TRIGGER_REASON_ANY_UPDATE = "ANY_UPDATE";
    static final String TRIGGER_REASON_INDEXED_FIELDS_CHANGED = "INDEXED_FIELDS_CHANGED";
    static final String TRIGGER_REASON_SELECTED_FIELDS_CHANGED = "SELECTED_INDEXED_FIELDS_CHANGED";

    static final String FAILURE_POLICY_EVALUATION = "POLICY_EVALUATION_FAILED";
    static final String FAILURE_RUN_ENQUEUE = "RUN_ENQUEUE_FAILED";
    static final String FAILURE_LEDGER_REFRESH = "LEDGER_REFRESH_FAILED";
    static final String FAILURE_BRIDGE_UNAVAILABLE = "BRIDGE_UNAVAILABLE";
    static final String FAILURE_SYNC_WAIT = "WAITING_FOR_SYNC";

    static final String ENTITY_TYPE_PRODUCT = ShopifyCompanionPluginSelection.ENTITY_TYPE_PRODUCT;
    static final String ENTITY_TYPE_SUPPORT_POLICY = ShopifyCompanionPluginSelection.ENTITY_TYPE_SUPPORT_POLICY;
    static final Set<String> ENTITY_TYPES = Set.of(ENTITY_TYPE_PRODUCT, ENTITY_TYPE_SUPPORT_POLICY);

    private ShopifyStoreVectorizationConstants() {
    }

    static String normalizeSourceCategory(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "sourceCategory is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_CATEGORIES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify source category: " + value);
        }
        return normalized;
    }

    static String normalizeEntityType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "entityType is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify entity type: " + value);
        }
        return normalized;
    }

    static String normalizeUpdateTriggerMode(String value) {
        String normalized = value == null || value.isBlank()
            ? UPDATE_MODE_INDEXED_ONLY
            : value.trim().toUpperCase(Locale.ROOT);
        if (!UPDATE_TRIGGER_MODES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify update trigger mode: " + value);
        }
        return normalized;
    }

    static String normalizeOperation(String value) {
        if (value == null || value.isBlank()) {
            return OPERATION_UPDATE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case OPERATION_CREATE, OPERATION_UPDATE, OPERATION_DELETE -> normalized;
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify operation: " + value);
        };
    }

    static String entityTypeForCategory(String sourceCategory) {
        String normalized = normalizeSourceCategory(sourceCategory);
        return switch (normalized) {
            case SOURCE_PRODUCTS, SOURCE_COLLECTIONS -> ENTITY_TYPE_PRODUCT;
            case SOURCE_PAGES, SOURCE_POLICIES, SOURCE_ARTICLES -> ENTITY_TYPE_SUPPORT_POLICY;
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify source category: " + sourceCategory);
        };
    }

    static boolean sourceEnabled(ShopifyStoreConnectionEntity store, String sourceCategory) {
        if (store == null) {
            return false;
        }
        return switch (normalizeSourceCategory(sourceCategory)) {
            case SOURCE_PRODUCTS -> store.isProductsEnabled();
            case SOURCE_COLLECTIONS -> store.isCollectionsEnabled();
            case SOURCE_PAGES -> store.isPagesEnabled();
            case SOURCE_POLICIES -> store.isPoliciesEnabled();
            case SOURCE_ARTICLES -> store.isArticlesEnabled();
            default -> false;
        };
    }

    static String aggregateKey(String shopDomain, String entityType) {
        return shopDomain.trim().toLowerCase(Locale.ROOT) + "::" + normalizeEntityType(entityType);
    }

    static boolean manualAllowed(ShopifyStoreConnectionEntity store, String sourceCategory) {
        return sourceEnabled(store, sourceCategory);
    }
}
