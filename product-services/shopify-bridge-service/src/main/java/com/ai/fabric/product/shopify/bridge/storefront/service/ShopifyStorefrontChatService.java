package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopifyStorefrontChatService {

    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;
    private static final Set<String> CONTEXT_TOP_LEVEL_FIELDS = Set.of(
        "pageType",
        "pageTitle",
        "product",
        "collection",
        "document",
        "cart",
        "cartId",
        "cart_id",
        "shopifyShellModeProfile",
        "shopifySurfaceEntry",
        "shopifyPageModeGroup",
        "shopifyEffectiveConversationMode",
        "shopifyAllowedConversationModes",
        "shopifyPageModeMappings"
    );
    private static final Set<String> DEPTH_SURFACE_ENTRIES = Set.of(
        "launcher",
        "max-mode",
        "chat",
        "depth"
    );
    private static final Set<String> MODE_CONTROL_SURFACE_ENTRIES = Set.of(
        "launcher",
        "max-mode",
        "chat",
        "depth",
        "comparison"
    );
    private static final Set<String> PRODUCT_CONTEXT_REQUIRED_SURFACE_ENTRIES = Set.of(
        "product-insight",
        "product-faq"
    );
    private static final String PRODUCT_CONTEXT_REQUIRED_MESSAGE =
        "Open a product page or select a product so I can answer about that item.";
    private static final String THINKER_MODE = "THINKER_DEEP";
    private static final Set<String> CANONICAL_CONVERSATION_MODES = Set.of(
        "navigator",
        "navigator_deep",
        "thinker",
        "thinker_deep",
        "cart_assistant",
        "executor"
    );
    private static final Set<String> ACCOUNT_AND_SUPPORT_PAGE_GROUPS = Set.of(
        "account",
        "order",
        "orders",
        "support",
        "contact",
        "returns",
        "help"
    );
    private static final Set<String> CART_ACTION_IDS = Set.of(
        "shopify_get_cart",
        "shopify_create_cart",
        "shopify_update_cart",
        "shopify_cart_create",
        "shopify_cart_update"
    );
    private static final Set<String> MARKETPLACE_ORDER_SELF_SERVICE_ACTION_IDS = Set.of(
        "shopify_cancel_checkout",
        "shopify_cancel_order",
        "shopify_refund_order",
        "shopify_edit_order",
        "shopify_update_order",
        "shopify_change_order_address",
        "shopify_start_return_request"
    );
    private static final Set<String> CUSTOMER_ACCOUNT_STORE_CREDIT_ACTION_IDS = Set.of(
        "shopify_get_store_credit_balances"
    );
    private static final Set<String> CUSTOMER_ACCOUNT_RETURN_ACTION_IDS = Set.of(
        "shopify_request_return",
        "shopify_start_return_request"
    );
    private static final Set<String> APPROVED_ORDER_SELF_SERVICE_ACTION_PACKAGES = Set.of(
        "order-self-service",
        "customer-order-self-service"
    );
    private static final String ACCOUNT_ACTION_POLICY_NO_SELF_SERVICE = "Account/order/support context: cart actions are not valid here. "
        + "Refund/cancel/edit-order self-service actions are not approved for this store; use order lookup or support handoff.";
    private static final String ACCOUNT_ACTION_POLICY_SELF_SERVICE_APPROVED = "Account/order/support context: cart actions are not valid for order requests here. "
        + "Approved order self-service actions may be selected only for explicit customer requests with required parameters, confirmation, audit, and available customer/order auth.";
    private static final String ORDER_LOOKUP_GUIDANCE = "Use this store's order lookup block with the exact order number and checkout email. "
        + "For refunds, cancellations, or order edits, contact the store support team unless the assistant shows a reviewed confirmation flow for that exact request.";

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeBillingService billingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShopifyStorefrontChatService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                        ShopifyBridgeInstallCredentialService installCredentialService,
                                        ShopifyBridgeBillingService billingService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.billingService = billingService;
    }

    public JsonNode query(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        ObjectNode normalizedRequest = normalizeRequest(request, store.shopDomain());
        ShopifyBridgeBillingSummary billingSummary = storefrontBillingSummary(store, normalizedRequest);
        enforceSurfaceEntitlement(store, normalizedRequest, billingSummary);
        JsonNode productContextResponse = productContextRequiredResponse(normalizedRequest);
        if (productContextResponse != null) {
            return productContextResponse;
        }
        appendStorefrontActionPolicyAttachment(normalizedRequest, billingSummary);
        applyStorefrontConversationMode(normalizedRequest, billingSummary);
        JsonNode response = platformShopifyStoreClient.queryConsumerBridgeChat(store.consumerId(), normalizedRequest, shopperSessionId);
        return shapeStorefrontResponse(response, normalizedRequest, store, billingSummary);
    }

    public JsonNode suggestions(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        ObjectNode normalizedRequest = normalizeRequest(request, store.shopDomain());
        enforceSurfaceEntitlement(store, normalizedRequest, storefrontBillingSummary(store, normalizedRequest));
        return platformShopifyStoreClient.suggestConsumerBridgeChat(store.consumerId(), normalizedRequest, shopperSessionId);
    }

    private ObjectNode normalizeRequest(JsonNode request, String shopDomain) {
        ObjectNode body = request != null && request.isObject()
            ? (ObjectNode) request.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode storefrontContext = extractStorefrontContext(body);
        ObjectNode storefrontContextAttachment = storefrontContextAttachment(storefrontContext, shopDomain);
        body.remove("storefrontContext");
        CONTEXT_TOP_LEVEL_FIELDS.forEach(body::remove);
        if (storefrontContextAttachment != null && !storefrontContextAttachment.isEmpty()) {
            ArrayNode attachments = objectMapper.createArrayNode();
            JsonNode existingAttachments = body.get("attachments");
            if (existingAttachments != null && existingAttachments.isArray()) {
                attachments.addAll((ArrayNode) existingAttachments);
            }
            attachments.add(storefrontContextAttachment);
            body.set("attachments", attachments);
        }
        return body;
    }

    private ObjectNode extractStorefrontContext(ObjectNode body) {
        ObjectNode context = null;
        JsonNode rawNestedContext = body.get("storefrontContext");
        if (rawNestedContext != null && rawNestedContext.isObject()) {
            context = (ObjectNode) rawNestedContext.deepCopy();
        }
        for (String field : CONTEXT_TOP_LEVEL_FIELDS) {
            JsonNode value = body.get(field);
            if (value != null && !value.isNull()) {
                if (context == null) {
                    context = objectMapper.createObjectNode();
                }
                context.set(field, value.deepCopy());
            }
        }
        return context;
    }

    private ObjectNode storefrontContextAttachment(JsonNode rawContext, String shopDomain) {
        ObjectNode metadata = objectMapper.createObjectNode();
        putLimitedTextField(metadata, "shopDomain", shopDomain);
        if (rawContext != null && rawContext.isObject()) {
            copyLimitedTextField(rawContext, metadata, "pageType");
            copyLimitedTextField(rawContext, metadata, "pageTitle");
            copyLimitedTextField(rawContext, metadata, "shopifyShellModeProfile");
            copyLimitedTextField(rawContext, metadata, "shopifySurfaceEntry");
            copyLimitedTextField(rawContext, metadata, "shopifyPageModeGroup");
            copyLimitedTextField(rawContext, metadata, "shopifyEffectiveConversationMode");

            JsonNode rawCart = rawContext.get("cart");
            if (rawCart != null && rawCart.isObject()) {
                copyLimitedTextField(rawCart, metadata, "id", "cart_id");
                copyLimitedTextField(rawCart, metadata, "cartId", "cart_id");
                copyLimitedTextField(rawCart, metadata, "cart_id", "cart_id");
            }
            copyLimitedTextField(rawContext, metadata, "cartId", "cart_id");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "cart_id");

            JsonNode rawProduct = rawContext.get("product");
            if (rawProduct != null && rawProduct.isObject()) {
                copyLimitedTextField(rawProduct, metadata, "id", "productId");
                copyLimitedTextField(rawProduct, metadata, "handle", "productHandle");
                copyLimitedTextField(rawProduct, metadata, "title", "productTitle");
                copyLimitedTextField(rawProduct, metadata, "vendor", "productVendor");
                copyLimitedTextField(rawProduct, metadata, "type", "productType");
                copyLimitedTextField(rawProduct, metadata, "priceCents", "productPriceCents");
            }
            copyLimitedTextFieldIfMissing(rawContext, metadata, "productId");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "productHandle");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "productTitle");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "productVendor");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "productType");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "productPriceCents");

            JsonNode rawCollection = rawContext.get("collection");
            if (rawCollection != null && rawCollection.isObject()) {
                copyLimitedTextField(rawCollection, metadata, "id", "collectionId");
                copyLimitedTextField(rawCollection, metadata, "handle", "collectionHandle");
                copyLimitedTextField(rawCollection, metadata, "title", "collectionTitle");
            }
            copyLimitedTextFieldIfMissing(rawContext, metadata, "collectionId");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "collectionHandle");
            copyLimitedTextFieldIfMissing(rawContext, metadata, "collectionTitle");

            JsonNode rawDocument = rawContext.get("document");
            if (rawDocument != null && rawDocument.isObject()) {
                copyLimitedTextField(rawDocument, metadata, "id", "documentId");
                copyLimitedTextField(rawDocument, metadata, "handle", "documentHandle");
                copyLimitedTextField(rawDocument, metadata, "title", "documentTitle");
                copyLimitedTextField(rawDocument, metadata, "type", "documentType");
                copyLimitedTextField(rawDocument, metadata, "url", "documentUrl");
            }
        }
        String contentText = storefrontContextSummary(metadata);
        if (!StringUtils.hasText(contentText) && metadata.isEmpty()) {
            return null;
        }
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("source", "shopify-storefront-context");
        if (StringUtils.hasText(contentText)) {
            attachment.put("contentText", contentText);
        }
        if (!metadata.isEmpty()) {
            attachment.set("metadata", metadata);
        }
        return attachment;
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String field) {
        copyLimitedTextField(source, target, field, field);
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        String value = trimToNull(textOrNull(source, sourceField));
        putLimitedTextField(target, targetField, value);
    }

    private void copyLimitedTextFieldIfMissing(JsonNode source, ObjectNode target, String field) {
        if (target == null || !StringUtils.hasText(field) || target.hasNonNull(field)) {
            return;
        }
        copyLimitedTextField(source, target, field);
    }

    private void putLimitedTextField(ObjectNode target, String targetField, String value) {
        if (target == null || !StringUtils.hasText(targetField)) {
            return;
        }
        value = trimToNull(value);
        if (value == null) {
            return;
        }
        if (value.length() > MAX_CONTEXT_TEXT_LENGTH) {
            value = value.substring(0, MAX_CONTEXT_TEXT_LENGTH);
        }
        target.put(targetField, value);
    }

    private String storefrontContextSummary(ObjectNode metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addSummaryPart(parts, metadata, "pageType", "Page type");
        addSummaryPart(parts, metadata, "pageTitle", "Page title");
        addSummaryPart(parts, metadata, "shopifyShellModeProfile", "Shopify shell mode");
        addSummaryPart(parts, metadata, "shopifySurfaceEntry", "Shopify surface");
        addSummaryPart(parts, metadata, "shopifyPageModeGroup", "Shopify page group");
        addSummaryPart(parts, metadata, "shopifyEffectiveConversationMode", "Shopify mode");
        addSummaryPart(parts, metadata, "cart_id", "Cart id");
        addSummaryPart(parts, metadata, "productTitle", "Product");
        addSummaryPart(parts, metadata, "productHandle", "Product handle");
        addSummaryPart(parts, metadata, "productVendor", "Product vendor");
        addSummaryPart(parts, metadata, "productType", "Product type");
        addSummaryPart(parts, metadata, "productPriceCents", "Product price cents");
        addSummaryPart(parts, metadata, "collectionTitle", "Collection");
        addSummaryPart(parts, metadata, "collectionHandle", "Collection handle");
        addSummaryPart(parts, metadata, "documentTitle", "Document");
        addSummaryPart(parts, metadata, "documentType", "Document type");
        addSummaryPart(parts, metadata, "documentHandle", "Document handle");
        if (parts.isEmpty()) {
            return null;
        }
        String summary = String.join(". ", parts);
        return summary.length() > MAX_CONTEXT_TEXT_LENGTH
            ? summary.substring(0, MAX_CONTEXT_TEXT_LENGTH)
            : summary;
    }

    private void addSummaryPart(List<String> parts, ObjectNode metadata, String field, String label) {
        String value = trimToNull(textOrNull(metadata, field));
        if (value != null) {
            parts.add(label + ": " + value);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() || !child.isValueNode() ? null : child.asText(null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private ShopifyBridgeBillingSummary storefrontBillingSummary(ShopifyBridgeStoreSummary store, ObjectNode request) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String surfaceEntry = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        if (surfaceEntry == null) {
            return null;
        }

        String storefrontAccessToken = storefrontAccessToken(store.shopDomain());
        return billingService.summarizeForShop(store.shopDomain(), storefrontAccessToken);
    }

    private void enforceSurfaceEntitlement(ShopifyBridgeStoreSummary store,
                                           ObjectNode request,
                                           ShopifyBridgeBillingSummary billingSummary) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String surfaceEntry = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        if (surfaceEntry == null || billingSummary == null) {
            return;
        }

        String requestedMode = requestedConversationMode(request, context);
        if (DEPTH_SURFACE_ENTRIES.contains(surfaceEntry)) {
            if (requiresThinkerConversationEntitlement(requestedMode)) {
                if (billingSummary.actionCapable()) {
                    return;
                }
                if (billingSummary.chatFallbackEnabled()) {
                    return;
                }
                throw forbidden("Companion chat depth is not available for this store's current plan.");
            }
            if (requiresDepthConversationEntitlement(requestedMode)) {
                if (billingSummary.chatFallbackEnabled()) {
                    return;
                }
                throw forbidden("Companion chat depth is not available for this store's current plan.");
            }
            if ("cart_assistant".equals(requestedMode) || "executor".equals(requestedMode)) {
                if (billingSummary.actionCapable()) {
                    return;
                }
                throw forbidden("Companion governed actions are not available for this store's current plan.");
            }
            if (baseNavigatorSurfaceAllowed(billingSummary, store)) {
                return;
            }
            throw forbidden("Companion surface 'ai-search' is not available for this store's current plan.");
        }

        List<String> allowedSurfaces = effectiveAllowedSurfaces(
            billingSummary.allowedSurfaces(),
            configuredEnabledSurfaces(store)
        );
        if (!allowedSurfaces.contains(surfaceEntry)) {
            throw forbidden("Companion surface '" + surfaceEntry + "' is not available for this store's current plan.");
        }
    }

    private String requestedConversationMode(ObjectNode request, ObjectNode context) {
        String requestedMode = normalizeConversationMode(textOrNull(request, "mode"));
        if (requestedMode != null) {
            return requestedMode;
        }
        return normalizeConversationMode(textOrNull(context, "shopifyEffectiveConversationMode"));
    }

    private boolean requiresDepthConversationEntitlement(String mode) {
        return mode == null || "navigator_deep".equals(mode);
    }

    private boolean requiresThinkerConversationEntitlement(String mode) {
        return "thinker_deep".equals(mode);
    }

    private boolean baseNavigatorSurfaceAllowed(ShopifyBridgeBillingSummary billingSummary, ShopifyBridgeStoreSummary store) {
        List<String> allowedSurfaces = billingSummary.allowedSurfaces() == null
            ? List.of()
            : billingSummary.allowedSurfaces();
        return allowedSurfaces.contains("ai-search");
    }

    private void applyStorefrontConversationMode(ObjectNode request, ShopifyBridgeBillingSummary billingSummary) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String surfaceEntry = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        if (surfaceEntry == null || !MODE_CONTROL_SURFACE_ENTRIES.contains(surfaceEntry)) {
            return;
        }
        String requestedMode = normalizeConversationMode(textOrNull(request, "mode"));
        if (requestedMode != null) {
            request.put("mode", platformConversationMode(effectiveDepthConversationMode(requestedMode, billingSummary)));
            return;
        }
        String contextMode = normalizeConversationMode(textOrNull(context, "shopifyEffectiveConversationMode"));
        if (contextMode != null) {
            request.put("mode", platformConversationMode(effectiveDepthConversationMode(contextMode, billingSummary)));
            return;
        }
        request.put("mode", platformConversationMode(effectiveDepthConversationMode("thinker_deep", billingSummary)));
    }

    private String effectiveDepthConversationMode(String normalizedMode, ShopifyBridgeBillingSummary billingSummary) {
        if ("thinker_deep".equals(normalizedMode) && (billingSummary == null || !billingSummary.chatFallbackEnabled())) {
            return "navigator_deep";
        }
        return normalizedMode;
    }

    private String normalizeConversationMode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!CANONICAL_CONVERSATION_MODES.contains(normalized)) {
            return null;
        }
        return "thinker".equals(normalized) ? "thinker_deep" : normalized;
    }

    private String platformConversationMode(String normalizedMode) {
        return "thinker_deep".equals(normalizedMode) ? THINKER_MODE : normalizedMode;
    }

    private JsonNode policyStorefrontAnswer(String message) {
        return policyStorefrontAnswer(message, null);
    }

    private JsonNode policyStorefrontAnswer(String message, String customerActionRequirement) {
        return policyStorefrontAnswer(message, customerActionRequirement, "ORDER_LOOKUP");
    }

    private JsonNode policyStorefrontAnswer(String message, String customerActionRequirement, String reason) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("conversationId", "chat-" + UUID.randomUUID());
        ObjectNode result = response.putObject("result");
        result.put("type", "INFORMATION_PROVIDED");
        result.put("success", true);
        result.put("message", message);
        ObjectNode sanitizedPayload = result.putObject("sanitizedPayload");
        sanitizedPayload.put("type", "INFORMATION_PROVIDED");
        sanitizedPayload.put("success", true);
        sanitizedPayload.put("message", message);
        sanitizedPayload.put("safeSummary", message);
        sanitizedPayload.put("answer", message);
        if ("CUSTOMER_ACCOUNT_AUTH_REQUIRED".equals(customerActionRequirement)) {
            ObjectNode data = sanitizedPayload.putObject("data");
            data.put("errorCode", "CUSTOMER_ACCOUNT_AUTH_REQUIRED");
            data.put("customerAccountAuthRequired", true);
            ObjectNode customerAccountAuth = data.putObject("customerAccountAuth");
            customerAccountAuth.put("required", true);
            customerAccountAuth.put("reason", StringUtils.hasText(reason) ? reason : "ORDER_LOOKUP");
            result.set("data", data.deepCopy());
        }
        return response;
    }

    private boolean surfaceAllowed(ShopifyBridgeBillingSummary billingSummary,
                                   ShopifyBridgeStoreSummary store,
                                   String surfaceId) {
        return effectiveAllowedSurfaces(billingSummary.allowedSurfaces(), configuredEnabledSurfaces(store)).contains(surfaceId);
    }

    private void appendStorefrontActionPolicyAttachment(ObjectNode request, ShopifyBridgeBillingSummary billingSummary) {
        if (!isAccountOrSupportContext(request)) {
            return;
        }
        boolean orderSelfServiceApproved = orderMutationSelfServiceApproved(billingSummary);
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("source", "shopify-storefront-action-policy");
        attachment.put(
            "contentText",
            orderSelfServiceApproved ? ACCOUNT_ACTION_POLICY_SELF_SERVICE_APPROVED : ACCOUNT_ACTION_POLICY_NO_SELF_SERVICE
        );
        ObjectNode metadata = attachment.putObject("metadata");
        metadata.put("cartActionsAllowed", false);
        metadata.put("orderSelfServiceApproved", orderSelfServiceApproved);
        ArrayNode deniedActions = metadata.putArray("deniedOrderSelfServiceActionsWhenUnapproved");
        MARKETPLACE_ORDER_SELF_SERVICE_ACTION_IDS.forEach(deniedActions::add);

        JsonNode existingAttachments = request.get("attachments");
        ArrayNode attachments = objectMapper.createArrayNode();
        if (existingAttachments != null && existingAttachments.isArray()) {
            attachments.addAll((ArrayNode) existingAttachments);
        }
        attachments.add(attachment);
        request.set("attachments", attachments);
    }

    private boolean orderMutationSelfServiceApproved(ShopifyBridgeBillingSummary billingSummary) {
        if (billingSummary == null
            || !billingSummary.actionCapable()
            || !billingSummary.requiresExplicitConfirmation()
            || !billingSummary.auditTrailAvailable()) {
            return false;
        }
        List<String> packages = billingSummary.actionPackages() == null ? List.of() : billingSummary.actionPackages();
        return packages.stream()
            .map(this::normalizeSurfaceEntry)
            .anyMatch(APPROVED_ORDER_SELF_SERVICE_ACTION_PACKAGES::contains);
    }

    private ObjectNode storefrontContextFromAttachments(ObjectNode request) {
        JsonNode attachments = request.get("attachments");
        if (attachments == null || !attachments.isArray()) {
            return null;
        }
        for (JsonNode attachment : attachments) {
            if (attachment == null || !attachment.isObject()) {
                continue;
            }
            if (!"shopify-storefront-context".equals(textOrNull(attachment, "source"))) {
                continue;
            }
            JsonNode metadata = attachment.get("metadata");
            return metadata != null && metadata.isObject() ? (ObjectNode) metadata : null;
        }
        return null;
    }

    private List<String> configuredEnabledSurfaces(ShopifyBridgeStoreSummary store) {
        return store.widgetDetail() != null
            && store.widgetDetail().settings() != null
            && store.widgetDetail().settings().enabledSurfaces() != null
            ? store.widgetDetail().settings().enabledSurfaces()
            : List.of();
    }

    private List<String> effectiveAllowedSurfaces(List<String> allowedSurfaces, List<String> configuredSurfaces) {
        List<String> allowed = allowedSurfaces == null ? List.of() : allowedSurfaces;
        List<String> configured = configuredSurfaces == null ? List.of() : configuredSurfaces.stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .toList();
        if (configured.isEmpty()) {
            return allowed;
        }
        return configured.stream()
            .filter(allowed::contains)
            .distinct()
            .toList();
    }

    private String storefrontAccessToken(String shopDomain) {
        return installCredentialService.resolvePersistedMaterial(shopDomain)
            .map(ShopifyBridgeCredentialAcquisition::tokenExchangeMaterial)
            .map(material -> trimToNull(material.accessToken()))
            .orElse(null);
    }

    private String normalizeSurfaceEntry(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private ShopifyBridgeStoreSummary requireReadyStore(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (!ShopifyStorefrontInteractionReadinessSupport.isReady(store)) {
            throw unavailable(firstStorefrontBlockingReason(store));
        }
        return store;
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        return ShopifyStorefrontInteractionReadinessSupport.firstBlockingReason(
            store,
            "Store assistant is not live yet for " + store.shopDomain() + "."
        );
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private JsonNode shapeStorefrontResponse(JsonNode response,
                                             ObjectNode request,
                                             ShopifyBridgeStoreSummary store,
                                             ShopifyBridgeBillingSummary billingSummary) {
        JsonNode policyResponse = storefrontPolicyResponse(response, request, store, billingSummary);
        if (policyResponse != null) {
            return policyResponse;
        }
        String answer = extractAnswer(response);
        return ensureWidgetSanitizedPayload(response, answer);
    }

    private JsonNode storefrontPolicyResponse(JsonNode response,
                                              ObjectNode request,
                                              ShopifyBridgeStoreSummary store,
                                              ShopifyBridgeBillingSummary billingSummary) {
        String selectedAction = selectedActionId(response);
        String errorCode = selectedErrorCode(response);
        if ("CUSTOMER_ACCOUNT_AUTH_REQUIRED".equals(errorCode)
            || "INVALID_CUSTOMER_ACCOUNT_SESSION".equals(errorCode)) {
            CustomerAccountAuthCopy authCopy = customerAccountAuthCopy(selectedAction);
            return policyStorefrontAnswer(
                authCopy.message(),
                "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
                authCopy.reason()
            );
        }
        if ("CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED".equals(errorCode)) {
            return policyStorefrontAnswer(
                "Customer account order lookup is not available for this store yet. Contact the store support team with your order number and checkout email."
            );
        }
        if (selectedAction == null) {
            return null;
        }
        if (MARKETPLACE_ORDER_SELF_SERVICE_ACTION_IDS.contains(selectedAction)
            && !orderMutationSelfServiceApproved(billingSummary)) {
            return policyStorefrontAnswer(
                "This store has not enabled self-service order changes in chat. Contact the store support team so they can review the request safely."
            );
        }
        if ("shopify_get_cart".equals(selectedAction) && genericMcpToolResult(response)) {
            return policyStorefrontAnswer(
                "I could not find cart details from the current cart session. Open your cart or add an item, then ask again."
            );
        }
        if (CART_ACTION_IDS.contains(selectedAction) && isAccountOrSupportContext(request)) {
            if (billingSummary != null && surfaceAllowed(billingSummary, store, "order-lookup")) {
                return policyStorefrontAnswer(ORDER_LOOKUP_GUIDANCE);
            }
            return policyStorefrontAnswer(
                "Order-specific help is handled by store support for this page. Contact the store support team with your order number and checkout email."
            );
        }
        return null;
    }

    private CustomerAccountAuthCopy customerAccountAuthCopy(String selectedAction) {
        if (CUSTOMER_ACCOUNT_STORE_CREDIT_ACTION_IDS.contains(selectedAction)) {
            return new CustomerAccountAuthCopy(
                "Connect your store account to view your store credit balance securely. If you still need help, contact the store support team.",
                "STORE_CREDIT"
            );
        }
        if (CUSTOMER_ACCOUNT_RETURN_ACTION_IDS.contains(selectedAction)) {
            return new CustomerAccountAuthCopy(
                "Connect your store account to start a return securely. If you still need help, contact the store support team with your order number and checkout email.",
                "RETURN_REQUEST"
            );
        }
        return new CustomerAccountAuthCopy(
            "Connect your store account to view or manage your orders. If you still need help, contact the store support team with your order number and checkout email.",
            "ORDER_LOOKUP"
        );
    }

    private boolean genericMcpToolResult(JsonNode response) {
        for (String path : List.of(
            "result.sanitizedPayload.safeSummary",
            "result.sanitizedPayload.message",
            "result.sanitizedPayload.answer",
            "result.data.actionResult.message",
            "result.sanitizedPayload.data.actionResult.message",
            "result.message"
        )) {
            String value = nestedText(response, path);
            if ("MCP tool result".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String selectedErrorCode(JsonNode response) {
        for (String path : List.of(
            "result.data.actionResult.errorCode",
            "result.sanitizedPayload.data.actionResult.errorCode",
            "result.data.errorCode",
            "result.sanitizedPayload.data.errorCode",
            "errorCode"
        )) {
            String value = nestedText(response, path);
            if (value != null) {
                return value.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private String selectedActionId(JsonNode response) {
        for (String path : List.of(
            "result.data.action",
            "result.data.actionId",
            "result.data.action.id",
            "result.sanitizedPayload.data.action",
            "result.sanitizedPayload.data.actionId",
            "result.sanitizedPayload.data.action.id",
            "result.data.actionResult.data.action",
            "result.data.actionResult.data.actionId",
            "result.sanitizedPayload.data.actionResult.data.action",
            "result.sanitizedPayload.data.actionResult.data.actionId"
        )) {
            String value = nestedText(response, path);
            if (value != null) {
                return value.trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private boolean isAccountOrSupportContext(ObjectNode request) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String pageType = normalizeSurfaceEntry(textOrNull(context, "pageType"));
        String pageGroup = normalizeSurfaceEntry(textOrNull(context, "shopifyPageModeGroup"));
        return containsNonNull(ACCOUNT_AND_SUPPORT_PAGE_GROUPS, pageType)
            || containsNonNull(ACCOUNT_AND_SUPPORT_PAGE_GROUPS, pageGroup);
    }

    private boolean containsNonNull(Set<String> values, String value) {
        return value != null && values.contains(value);
    }

    private JsonNode productContextRequiredResponse(ObjectNode request) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String surfaceEntry = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        if (!containsNonNull(PRODUCT_CONTEXT_REQUIRED_SURFACE_ENTRIES, surfaceEntry)) {
            return null;
        }
        if (hasCurrentProductIdentity(context)) {
            return null;
        }
        return policyStorefrontAnswer(PRODUCT_CONTEXT_REQUIRED_MESSAGE);
    }

    private boolean hasCurrentProductIdentity(ObjectNode context) {
        return StringUtils.hasText(textOrNull(context, "productId"))
            || StringUtils.hasText(textOrNull(context, "productHandle"))
            || StringUtils.hasText(textOrNull(context, "productTitle"));
    }

    private JsonNode ensureWidgetSanitizedPayload(JsonNode response, String answer) {
        if (response == null || !response.isObject()) {
            return response;
        }
        ObjectNode shaped = (ObjectNode) response.deepCopy();
        shaped.remove("authContext");
        ObjectNode result = objectChild(shaped, "result");
        result.remove(List.of("metadata", "nextSteps", "children"));
        ObjectNode sanitizedPayload = objectChild(result, "sanitizedPayload");
        String resultType = trimToNull(textOrNull(result, "type"));
        if (trimToNull(textOrNull(sanitizedPayload, "type")) == null) {
            sanitizedPayload.put("type", resultType == null ? "INFORMATION_PROVIDED" : resultType);
        }
        if (!sanitizedPayload.has("success")) {
            boolean success = result.has("success")
                ? result.path("success").asBoolean(true)
                : shaped.path("success").asBoolean(true);
            sanitizedPayload.put("success", success);
        }
        if (answer != null) {
            result.put("message", answer);
            sanitizedPayload.put("message", answer);
            sanitizedPayload.put("safeSummary", answer);
            sanitizedPayload.put("answer", answer);
        }
        JsonNode safeData = storefrontSafeData(firstPresent(result.get("data"), sanitizedPayload.get("data")));
        if (safeData != null && !safeData.isNull()) {
            result.set("data", safeData.deepCopy());
            sanitizedPayload.set("data", safeData.deepCopy());
        }
        return shaped;
    }

    private JsonNode firstPresent(JsonNode first, JsonNode second) {
        return first != null && !first.isNull() ? first : second;
    }

    private JsonNode storefrontSafeData(JsonNode data) {
        if (data == null || data.isNull()) {
            return data;
        }
        if (!data.isObject()) {
            return data.deepCopy();
        }
        ObjectNode safe = objectMapper.createObjectNode();
        copySafeValue(data, safe, "answer");
        copySafeValue(data, safe, "query");
        copySafeValue(data, safe, "response");
        copySafeValue(data, safe, "action");
        copySafeValue(data, safe, "errorCode");
        copySafeValue(data, safe, "customerAccountAuthRequired");
        copySafeValue(data, safe, "requiresConfirmation");
        copySafeValue(data, safe, "confirmationRequired");
        copySafeValue(data, safe, "confirmationToken");
        copySafeValue(data, safe, "confirmationPrompt");
        copySafeValue(data, safe, "confirmationMessage");
        JsonNode customerAccountAuth = data.get("customerAccountAuth");
        if (customerAccountAuth != null && customerAccountAuth.isObject()) {
            ObjectNode safeCustomerAccountAuth = objectMapper.createObjectNode();
            copySafeValue(customerAccountAuth, safeCustomerAccountAuth, "required");
            copySafeValue(customerAccountAuth, safeCustomerAccountAuth, "reason");
            if (!safeCustomerAccountAuth.isEmpty()) {
                safe.set("customerAccountAuth", safeCustomerAccountAuth);
            }
        }
        JsonNode actionResult = data.get("actionResult");
        if (actionResult != null && !actionResult.isNull()) {
            safe.set("actionResult", storefrontSafeActionResult(actionResult));
        }
        JsonNode documents = data.get("documents");
        if (documents != null && documents.isArray()) {
            safe.set("documents", storefrontSafeDocuments(documents));
        }
        JsonNode ragResponse = data.get("ragResponse");
        if (ragResponse != null && ragResponse.isObject()) {
            ObjectNode safeRag = objectMapper.createObjectNode();
            copySafeValue(ragResponse, safeRag, "answer");
            copySafeValue(ragResponse, safeRag, "query");
            copySafeValue(ragResponse, safeRag, "response");
            JsonNode ragDocuments = ragResponse.get("documents");
            if (ragDocuments != null && ragDocuments.isArray()) {
                safeRag.set("documents", storefrontSafeDocuments(ragDocuments));
            }
            safe.set("ragResponse", safeRag);
        }
        return safe;
    }

    private JsonNode storefrontSafeActionResult(JsonNode actionResult) {
        if (!actionResult.isObject()) {
            return actionResult.deepCopy();
        }
        ObjectNode safe = objectMapper.createObjectNode();
        copySafeValue(actionResult, safe, "success");
        copySafeValue(actionResult, safe, "message");
        copySafeValue(actionResult, safe, "errorCode");
        JsonNode data = actionResult.get("data");
        if (data != null && !data.isNull()) {
            safe.set("data", storefrontSafeData(data));
        }
        return safe;
    }

    private ArrayNode storefrontSafeDocuments(JsonNode documents) {
        ArrayNode safeDocuments = objectMapper.createArrayNode();
        if (documents == null || !documents.isArray()) {
            return safeDocuments;
        }
        for (JsonNode document : documents) {
            if (document == null || !document.isObject()) {
                continue;
            }
            ObjectNode safeDocument = objectMapper.createObjectNode();
            copySafeValue(document, safeDocument, "id");
            copySafeValue(document, safeDocument, "type");
            copySafeValue(document, safeDocument, "score");
            copySafeValue(document, safeDocument, "similarity");
            copySafeValue(document, safeDocument, "source");
            copySafeValue(document, safeDocument, "url");
            String title = firstNonBlank(
                textOrNull(document, "title"),
                textOrNull(document.path("metadata"), "shopifyDocumentTitle"),
                textOrNull(document.path("metadata"), "title")
            );
            if (title != null) {
                safeDocument.put("title", title);
            }
            String storefrontUrl = firstNonBlank(
                textOrNull(document, "storefrontUrl"),
                textOrNull(document.path("metadata"), "storefrontUrl"),
                textOrNull(document, "url")
            );
            if (storefrontUrl != null) {
                safeDocument.put("storefrontUrl", storefrontUrl);
                safeDocument.put("url", storefrontUrl);
            }
            String productVariantId = safeProductVariantGid(firstNonBlank(
                textOrNull(document, "product_variant_id"),
                textOrNull(document.path("metadata"), "product_variant_id")
            ));
            if (productVariantId != null) {
                safeDocument.put("product_variant_id", productVariantId);
            }
            copySafeDocumentText(document, safeDocument, "firstAvailableVariantTitle");
            copySafeDocumentText(document.path("metadata"), safeDocument, "firstAvailableVariantTitle");
            copySafeDocumentText(document, safeDocument, "priceRange");
            copySafeDocumentText(document.path("metadata"), safeDocument, "priceRange");
            copySafeDocumentText(document, safeDocument, "availability");
            copySafeDocumentText(document.path("metadata"), safeDocument, "availability");
            String content = trimToNull(textOrNull(document, "content"));
            if (content != null) {
                safeDocument.put("content", content.length() > 600 ? content.substring(0, 600) : content);
            }
            safeDocuments.add(safeDocument);
        }
        return safeDocuments;
    }

    private String safeProductVariantGid(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.matches("^gid://shopify/ProductVariant/[0-9]+$")) {
            return null;
        }
        return normalized;
    }

    private void copySafeDocumentText(JsonNode source, ObjectNode target, String field) {
        if (source == null || target == null || !StringUtils.hasText(field) || target.has(field)) {
            return;
        }
        String value = trimToNull(textOrNull(source, field));
        if (value != null) {
            String safeValue = value.length() > MAX_CONTEXT_TEXT_LENGTH ? value.substring(0, MAX_CONTEXT_TEXT_LENGTH) : value;
            target.put(field, safeValue);
        }
    }

    private void copySafeValue(JsonNode source, ObjectNode target, String field) {
        if (source == null || target == null || !source.has(field)) {
            return;
        }
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return;
        }
        target.set(field, value.deepCopy());
    }

    private ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing != null && existing.isObject()) {
            return (ObjectNode) existing;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private String extractAnswer(JsonNode response) {
        for (String path : List.of(
            "result.sanitizedPayload.safeSummary",
            "result.sanitizedPayload.message",
            "result.sanitizedPayload.answer",
            "result.data.actionResult.message",
            "result.sanitizedPayload.data.actionResult.message",
            "result.message",
            "message",
            "answer"
        )) {
            String value = nestedText(response, path);
            if (value != null && !genericMcpToolResult(value)) {
                return value;
            }
        }
        return extractMcpToolTextAnswer(response);
    }

    private String extractMcpToolTextAnswer(JsonNode response) {
        for (String path : List.of(
            "result.data.actionResult.data.toolResult.content",
            "result.sanitizedPayload.data.actionResult.data.toolResult.content",
            "result.data.toolResult.content",
            "result.sanitizedPayload.data.toolResult.content"
        )) {
            JsonNode content = nestedNode(response, path);
            if (content == null || !content.isArray()) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (JsonNode item : content) {
                String type = trimToNull(textOrNull(item, "type"));
                String text = trimToNull(textOrNull(item, "text"));
                if ("text".equalsIgnoreCase(type) && text != null) {
                    parts.add(summarizeMcpToolText(text));
                }
            }
            if (!parts.isEmpty()) {
                return String.join("\n", parts);
            }
        }
        return null;
    }

    private String summarizeMcpToolText(String text) {
        String normalized = trimToNull(text);
        if (normalized == null || (!normalized.startsWith("{") && !normalized.startsWith("["))) {
            return normalized;
        }
        try {
            JsonNode parsed = objectMapper.readTree(normalized);
            String error = firstMcpErrorMessage(parsed);
            if (error != null) {
                return error;
            }
            String cartSummary = cartSummary(parsed.path("cart"));
            return cartSummary != null ? cartSummary : normalized;
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private String firstMcpErrorMessage(JsonNode parsed) {
        JsonNode errors = parsed != null ? parsed.path("errors") : null;
        if (errors == null || !errors.isArray() || errors.isEmpty()) {
            return null;
        }
        for (JsonNode error : errors) {
            String message = trimToNull(textOrNull(error, "message"));
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private String cartSummary(JsonNode cart) {
        if (cart == null || !cart.isObject() || cart.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String lineSummary = cartLineSummary(cart.path("lines"));
        if (lineSummary != null) {
            parts.add("Cart updated: " + lineSummary + ".");
        } else {
            int totalQuantity = cart.path("total_quantity").asInt(-1);
            parts.add(totalQuantity >= 0 ? "Cart updated. Total quantity: " + totalQuantity + "." : "Cart updated.");
        }
        String total = moneySummary(cart.path("cost").path("total_amount"));
        if (total != null) {
            parts.add("Total: " + total + ".");
        }
        String checkoutUrl = trimToNull(textOrNull(cart, "checkout_url"));
        if (checkoutUrl != null) {
            parts.add("Checkout: " + checkoutUrl);
        }
        return String.join(" ", parts);
    }

    private String cartLineSummary(JsonNode lines) {
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            return null;
        }
        List<String> summaries = new ArrayList<>();
        int shown = 0;
        for (JsonNode line : lines) {
            if (line == null || !line.isObject()) {
                continue;
            }
            String title = firstNonBlank(
                textOrNull(line.path("merchandise").path("product"), "title"),
                textOrNull(line.path("merchandise"), "title")
            );
            int quantity = line.path("quantity").asInt(0);
            if (title == null || quantity <= 0) {
                continue;
            }
            summaries.add(quantity + " x " + title);
            shown++;
            if (shown >= 3) {
                break;
            }
        }
        if (summaries.isEmpty()) {
            return null;
        }
        int remaining = lines.size() - shown;
        if (remaining > 0) {
            summaries.add(remaining + " more");
        }
        return String.join(", ", summaries);
    }

    private String moneySummary(JsonNode money) {
        if (money == null || !money.isObject()) {
            return null;
        }
        String amount = trimToNull(textOrNull(money, "amount"));
        String currency = firstNonBlank(textOrNull(money, "currency"), textOrNull(money, "currencyCode"));
        if (amount == null) {
            return null;
        }
        return currency != null ? amount + " " + currency : amount;
    }

    private boolean genericMcpToolResult(String value) {
        return "MCP tool result".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private String nestedText(JsonNode node, String dottedPath) {
        JsonNode current = nestedNode(node, dottedPath);
        String value = current == null || current.isNull() || !current.isValueNode() ? null : current.asText(null);
        return trimToNull(value);
    }

    private JsonNode nestedNode(JsonNode node, String dottedPath) {
        JsonNode current = node;
        if (!StringUtils.hasText(dottedPath)) {
            return current;
        }
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private record CustomerAccountAuthCopy(String message, String reason) {
    }

}
