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
    private static final Set<String> APPROVED_ORDER_SELF_SERVICE_ACTION_PACKAGES = Set.of(
        "order-self-service",
        "customer-order-self-service"
    );
    private static final String ACCOUNT_ACTION_POLICY_NO_SELF_SERVICE = "Account/order/support context: cart actions are not valid here. "
        + "Refund/cancel/edit-order self-service actions are not approved for this store; use order lookup or support handoff.";
    private static final String ACCOUNT_ACTION_POLICY_SELF_SERVICE_APPROVED = "Account/order/support context: cart actions are not valid here. "
        + "Approved order self-service actions may be selected only for explicit customer requests with required parameters, confirmation, audit, and available customer/order auth.";
    private static final String GENERIC_SEARCH_COMPLETED = "Search completed.";
    private static final String INTERNAL_SESSION_PARAM = "shopperSessionId";
    private static final String ORDER_LOOKUP_GUIDANCE = "Use this store's order lookup block with the exact order number and checkout email. "
        + "For refunds, cancellations, or order edits, contact the store support team unless the assistant shows a reviewed confirmation flow for that exact request.";
    private static final String STORE_ASSISTANT_SCOPE_GUIDANCE = "I can help with this store's products, policies, comparisons, and shopping tasks using merchant-approved information.";

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

            JsonNode rawProduct = rawContext.get("product");
            if (rawProduct != null && rawProduct.isObject()) {
                copyLimitedTextField(rawProduct, metadata, "id", "productId");
                copyLimitedTextField(rawProduct, metadata, "handle", "productHandle");
                copyLimitedTextField(rawProduct, metadata, "title", "productTitle");
                copyLimitedTextField(rawProduct, metadata, "vendor", "productVendor");
                copyLimitedTextField(rawProduct, metadata, "type", "productType");
                copyLimitedTextField(rawProduct, metadata, "priceCents", "productPriceCents");
            }

            JsonNode rawCollection = rawContext.get("collection");
            if (rawCollection != null && rawCollection.isObject()) {
                copyLimitedTextField(rawCollection, metadata, "id", "collectionId");
                copyLimitedTextField(rawCollection, metadata, "handle", "collectionHandle");
                copyLimitedTextField(rawCollection, metadata, "title", "collectionTitle");
            }

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
        sanitizedPayload.put("safeSummary", message);
        sanitizedPayload.put("answer", message);
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
        String answer = storefrontAnswerOverride(response, request, extractAnswer(response));
        return ensureWidgetSanitizedPayload(response, answer);
    }

    private JsonNode storefrontPolicyResponse(JsonNode response,
                                              ObjectNode request,
                                              ShopifyBridgeStoreSummary store,
                                              ShopifyBridgeBillingSummary billingSummary) {
        String selectedAction = selectedActionId(response);
        if (selectedAction == null
            && (isRuntimeOutOfScope(response) || isOrderLookupRetrievalPolicyMiss(response, request))
            && isAccountOrSupportContext(request)) {
            if (orderMutationSelfServiceApproved(billingSummary)) {
                return policyStorefrontAnswer(
                    "This store supports approved checkout self-service where Shopify exposes the required action. Post-order refunds, cancellations, returns, and order edits still route to store support until Shopify exposes a reviewed MCP tool for this store."
                );
            }
            if (billingSummary != null && surfaceAllowed(billingSummary, store, "order-lookup")) {
                return policyStorefrontAnswer(ORDER_LOOKUP_GUIDANCE);
            }
            return policyStorefrontAnswer(
                "Order-specific help is handled by store support for this page. Contact the store support team with your order number and checkout email."
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

    private boolean isOrderLookupRetrievalPolicyMiss(JsonNode response, ObjectNode request) {
        if (!"order-lookup".equals(normalizeSurfaceEntry(textOrNull(storefrontContextFromAttachments(request), "shopifySurfaceEntry")))) {
            return false;
        }
        String type = normalizeSurfaceEntry(nestedText(response, "result.type"));
        String reason = normalizeSurfaceEntry(firstNonBlank(
            nestedText(response, "result.data.reason"),
            nestedText(response, "result.sanitizedPayload.data.reason")
        ));
        return "clarification_required".equals(type) && "vector_space_not_allowed_by_policy".equals(reason);
    }

    private boolean isRuntimeOutOfScope(JsonNode response) {
        String type = nestedText(response, "result.type");
        return "out_of_scope".equals(normalizeSurfaceEntry(type));
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

    private String storefrontAnswerOverride(JsonNode response, ObjectNode request, String answer) {
        if (hasOnlyInternalMissingRequiredParameter(response, INTERNAL_SESSION_PARAM)) {
            return "I can help with cart changes after the product or variant is selected and confirmed. Please choose the item you want to update and try again.";
        }
        if (isRuntimeOutOfScope(response)) {
            return STORE_ASSISTANT_SCOPE_GUIDANCE;
        }
        if (isInternalRetrievalClarification(response)) {
            if (isAccountOrSupportContext(request)
                || "order-lookup".equals(normalizeSurfaceEntry(textOrNull(storefrontContextFromAttachments(request), "shopifySurfaceEntry")))) {
                return ORDER_LOOKUP_GUIDANCE;
            }
            return STORE_ASSISTANT_SCOPE_GUIDANCE;
        }
        if (GENERIC_SEARCH_COMPLETED.equals(trimToNull(answer))) {
            String summary = summarizeRetrievedEvidence(response);
            if (summary != null) {
                return summary;
            }
        }
        return answer;
    }

    private boolean hasOnlyInternalMissingRequiredParameter(JsonNode response, String paramName) {
        if (!StringUtils.hasText(paramName)) {
            return false;
        }
        for (String path : List.of(
            "result.data.missingRequiredParameters",
            "result.sanitizedPayload.data.missingRequiredParameters"
        )) {
            JsonNode node = nestedNode(response, path);
            if (node == null || !node.isArray() || node.isEmpty()) {
                continue;
            }
            if (node.size() == 1 && paramName.equals(node.get(0).asText(null))) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalRetrievalClarification(JsonNode response) {
        String type = normalizeSurfaceEntry(nestedText(response, "result.type"));
        String reason = normalizeSurfaceEntry(firstNonBlank(
            nestedText(response, "result.data.reason"),
            nestedText(response, "result.sanitizedPayload.data.reason")
        ));
        return "clarification_required".equals(type) && (
            "vector_space_not_allowed_by_policy".equals(reason)
                || "vector_space_required_by_policy".equals(reason)
                || nestedNode(response, "result.data.allowedVectorSpaces") != null
                || nestedNode(response, "result.sanitizedPayload.data.allowedVectorSpaces") != null
                || nestedNode(response, "result.data.candidateVectorSpaces") != null
                || nestedNode(response, "result.sanitizedPayload.data.candidateVectorSpaces") != null
        );
    }

    private String summarizeRetrievedEvidence(JsonNode response) {
        JsonNode documents = firstArray(
            nestedNode(response, "result.data.documents"),
            nestedNode(response, "result.sanitizedPayload.data.documents"),
            nestedNode(response, "result.data.ragResponse.documents"),
            nestedNode(response, "result.sanitizedPayload.data.ragResponse.documents")
        );
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        List<String> productTitles = new ArrayList<>();
        List<String> infoTitles = new ArrayList<>();
        for (JsonNode document : documents) {
            if (document == null || !document.isObject()) {
                continue;
            }
            String title = firstNonBlank(
                textOrNull(document, "title"),
                textOrNull(document.path("metadata"), "shopifyDocumentTitle"),
                textOrNull(document.path("metadata"), "title")
            );
            if (title == null || productTitles.contains(title) || infoTitles.contains(title)) {
                continue;
            }
            String type = normalizeSurfaceEntry(textOrNull(document, "type"));
            if ("product".equals(type)) {
                productTitles.add(title);
            } else {
                infoTitles.add(title);
            }
            if (productTitles.size() + infoTitles.size() >= 3) {
                break;
            }
        }
        if (!productTitles.isEmpty()) {
            return "I found relevant products: " + joinTitles(productTitles)
                + ". Open a product to confirm price, variants, and availability before checkout.";
        }
        if (!infoTitles.isEmpty()) {
            return "I found relevant store information: " + joinTitles(infoTitles)
                + ". Check those details before deciding.";
        }
        return null;
    }

    private String joinTitles(List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return "";
        }
        if (titles.size() == 1) {
            return titles.get(0);
        }
        if (titles.size() == 2) {
            return titles.get(0) + " and " + titles.get(1);
        }
        return String.join(", ", titles.subList(0, titles.size() - 1)) + ", and " + titles.get(titles.size() - 1);
    }

    private JsonNode firstArray(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
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
        copySafeValue(data, safe, "requiresConfirmation");
        copySafeValue(data, safe, "confirmationRequired");
        copySafeValue(data, safe, "confirmationToken");
        copySafeValue(data, safe, "confirmationPrompt");
        copySafeValue(data, safe, "confirmationMessage");
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
            String content = trimToNull(textOrNull(document, "content"));
            if (content != null) {
                safeDocument.put("content", content.length() > 600 ? content.substring(0, 600) : content);
            }
            safeDocuments.add(safeDocument);
        }
        return safeDocuments;
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
            "result.message",
            "message",
            "answer"
        )) {
            String value = nestedText(response, path);
            if (value != null) {
                return value;
            }
        }
        return null;
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

}
