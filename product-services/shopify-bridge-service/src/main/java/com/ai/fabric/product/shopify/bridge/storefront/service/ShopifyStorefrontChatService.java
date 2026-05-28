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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopifyStorefrontChatService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyStorefrontChatService.class);

    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;
    private static final int MAX_CONTEXT_ATTACHMENT_TEXT_LENGTH = 1_200;
    private static final int MAX_CONTEXT_CART_ITEMS = 5;
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
    private static final String DEBUG_CONTRACT_VERSION = "SHOPIFY_STOREFRONT_DEBUG_V1";
    private static final int DEBUG_MAX_DEPTH = 8;
    private static final int DEBUG_MAX_ARRAY_ITEMS = 24;
    private static final int DEBUG_MAX_STRING_LENGTH = 2_000;
    private static final Set<String> DEBUG_SENSITIVE_FIELD_NAMES = Set.of(
        "access_token",
        "accesstoken",
        "api_key",
        "apikey",
        "auth",
        "authcontext",
        "authorization",
        "authorizationurl",
        "authtoken",
        "callbackurl",
        "cookie",
        "customeremail",
        "email",
        "headers",
        "hmac",
        "id_token",
        "idtoken",
        "key",
        "password",
        "raw",
        "refresh_token",
        "refreshtoken",
        "secret",
        "session",
        "sessionid",
        "customersessionid",
        "mcpsessionid",
        "shoppersessionid",
        "starturl",
        "token"
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
        rejectLegacyPublicChatFields(body);
        ObjectNode storefrontContext = extractStorefrontContext(body);
        ObjectNode storefrontContextAttachment = storefrontContextAttachment(storefrontContext, shopDomain);
        CONTEXT_TOP_LEVEL_FIELDS.forEach(body::remove);
        if (storefrontContext == null || storefrontContext.isEmpty()) {
            body.remove("context");
        } else {
            body.set("context", storefrontContext.deepCopy());
        }
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

    private void rejectLegacyPublicChatFields(ObjectNode body) {
        if (body == null || !body.has("storefrontContext")) {
            return;
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Unsupported chat request field: storefrontContext. Use context for product/storefront request state."
        );
    }

    private ObjectNode extractStorefrontContext(ObjectNode body) {
        JsonNode rawNestedContext = body.get("context");
        if (rawNestedContext != null && rawNestedContext.isObject()) {
            return (ObjectNode) rawNestedContext.deepCopy();
        }
        return null;
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
                copyLimitedTextField(rawCart, metadata, "itemCount", "cartItemCount");
                copyLimitedTextField(rawCart, metadata, "totalPriceCents", "cartTotalPriceCents");
                copyLimitedTextField(rawCart, metadata, "currency", "cartCurrency");
                putLimitedTextField(metadata, "cartSummary", liveCartSummary(rawCart), MAX_CONTEXT_ATTACHMENT_TEXT_LENGTH);
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
        putLimitedTextField(target, targetField, value, MAX_CONTEXT_TEXT_LENGTH);
    }

    private void putLimitedTextField(ObjectNode target, String targetField, String value, int maxLength) {
        if (target == null || !StringUtils.hasText(targetField)) {
            return;
        }
        value = trimToNull(value);
        if (value == null) {
            return;
        }
        int boundedMaxLength = Math.max(1, maxLength);
        if (value.length() > boundedMaxLength) {
            value = value.substring(0, boundedMaxLength);
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
        addSummaryPart(parts, metadata, "cartSummary", "Current cart");
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
        return summary.length() > MAX_CONTEXT_ATTACHMENT_TEXT_LENGTH
            ? summary.substring(0, MAX_CONTEXT_ATTACHMENT_TEXT_LENGTH)
            : summary;
    }

    private String liveCartSummary(JsonNode cart) {
        if (cart == null || !cart.isObject() || cart.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String itemCount = trimToNull(textOrNull(cart, "itemCount"));
        String currency = trimToNull(textOrNull(cart, "currency"));
        String totalPriceCents = trimToNull(textOrNull(cart, "totalPriceCents"));
        if (itemCount != null) {
            parts.add(itemCount + ("1".equals(itemCount) ? " item" : " items"));
        }
        String total = moneyFromCents(totalPriceCents, currency);
        if (total != null) {
            parts.add("total " + total);
        }
        List<String> itemSummaries = liveCartItemSummaries(cart.path("items"), currency);
        if (!itemSummaries.isEmpty()) {
            parts.add("items: " + String.join("; ", itemSummaries));
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private List<String> liveCartItemSummaries(JsonNode items, String currency) {
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }
        List<String> summaries = new ArrayList<>();
        int count = 0;
        for (JsonNode item : items) {
            if (item == null || !item.isObject()) {
                continue;
            }
            if (count >= MAX_CONTEXT_CART_ITEMS) {
                summaries.add("and " + (items.size() - count) + " more");
                break;
            }
            String title = firstNonBlank(
                textOrNull(item, "title"),
                textOrNull(item, "productTitle")
            );
            if (title == null) {
                continue;
            }
            String quantity = trimToNull(textOrNull(item, "quantity"));
            String price = moneyFromCents(
                firstNonBlank(textOrNull(item, "finalLinePriceCents"), textOrNull(item, "priceCents")),
                currency
            );
            String variantTitle = trimToNull(textOrNull(item, "variantTitle"));
            List<String> details = new ArrayList<>();
            if (quantity != null) {
                details.add("qty " + quantity);
            }
            if (price != null) {
                details.add(price);
            }
            if (variantTitle != null && !"Default Title".equalsIgnoreCase(variantTitle)) {
                details.add(variantTitle);
            }
            summaries.add(details.isEmpty() ? title : title + " (" + String.join(", ", details) + ")");
            count++;
        }
        return summaries;
    }

    private String moneyFromCents(String centsText, String currency) {
        if (!StringUtils.hasText(centsText)) {
            return null;
        }
        try {
            long cents = Long.parseLong(centsText.trim());
            java.math.BigDecimal amount = java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros();
            return amount.toPlainString() + (StringUtils.hasText(currency) ? " " + currency.trim() : "");
        } catch (NumberFormatException ignored) {
            return null;
        }
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
        response.put("type", "INFORMATION_PROVIDED");
        response.put("answer", message);
        response.put("safeSummary", message);
        response.put("conversationId", "chat-" + UUID.randomUUID());
        response.putArray("sources");
        ArrayNode actions = response.putArray("actions");
        response.putArray("suggestions");
        if ("CUSTOMER_ACCOUNT_AUTH_REQUIRED".equals(customerActionRequirement)) {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("errorCode", "CUSTOMER_ACCOUNT_AUTH_REQUIRED");
            data.put("customerAccountAuthRequired", true);
            ObjectNode customerAccountAuth = data.putObject("customerAccountAuth");
            customerAccountAuth.put("required", true);
            customerAccountAuth.put("reason", StringUtils.hasText(reason) ? reason : "ORDER_LOOKUP");
            actions.add(data);
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
        try {
            return installCredentialService.resolvePersistedMaterial(shopDomain)
                .map(ShopifyBridgeCredentialAcquisition::tokenExchangeMaterial)
                .map(material -> trimToNull(material.accessToken()))
                .orElse(null);
        } catch (RuntimeException exception) {
            log.warn(
                "Shopify storefront chat continuing without refreshed credential material for shop={}. Token-backed subscription refresh remains gated until the app is reconnected. cause={}",
                shopDomain,
                exception.toString()
            );
            return null;
        }
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
            if (policyResponse.isObject() && storefrontDebugEnabled(store)) {
                attachDebugPayload((ObjectNode) policyResponse, response, request);
            }
            return policyResponse;
        }
        String answer = extractAnswer(response);
        ObjectNode shaped = ensureCanonicalStorefrontPayload(response, answer);
        if (storefrontDebugEnabled(store)) {
            attachDebugPayload(shaped, response, request);
        }
        return shaped;
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
            "safeSummary",
            "answer",
            "actions.0.message",
            "actions.0.actionResult.message",
            "result.sanitizedPayload.safeSummary",
            "result.sanitizedPayload.message",
            "result.sanitizedPayload.answer",
            "result.data.actionResult.message",
            "result.sanitizedPayload.data.actionResult.message",
            "result.message",
            "message"
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
            "actions.0.errorCode",
            "actions.0.actionResult.errorCode",
            "result.data.actionResult.errorCode",
            "result.sanitizedPayload.data.actionResult.errorCode",
            "result.data.errorCode",
            "result.sanitizedPayload.data.errorCode",
            "fallbackReason",
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
            "actions.0.action",
            "actions.0.actionId",
            "actions.0.action.id",
            "actions.0.actionResult.data.action",
            "actions.0.actionResult.data.actionId",
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

    private ObjectNode ensureCanonicalStorefrontPayload(JsonNode response, String answer) {
        ObjectNode shaped = objectMapper.createObjectNode();
        if (response == null || !response.isObject()) {
            if (answer != null) {
                shaped.put("answer", answer);
                shaped.put("safeSummary", answer);
            }
            shaped.put("success", true);
            shaped.put("type", "INFORMATION_PROVIDED");
            shaped.put("conversationId", "chat-" + UUID.randomUUID());
            shaped.putArray("sources");
            shaped.putArray("actions");
            shaped.putArray("suggestions");
            return shaped;
        }
        boolean success = response.has("success")
            ? response.path("success").asBoolean(true)
            : firstPresent(response.path("result").path("success"), response.path("result").path("sanitizedPayload").path("success")).asBoolean(true);
        String resultType = firstNonBlank(
            textOrNull(response, "type"),
            nestedText(response, "result.type"),
            nestedText(response, "result.sanitizedPayload.type")
        );
        shaped.put("success", success);
        shaped.put("type", resultType == null ? "INFORMATION_PROVIDED" : resultType);
        if (answer != null) {
            shaped.put("answer", answer);
            shaped.put("safeSummary", answer);
        }
        String conversationId = firstNonBlank(textOrNull(response, "conversationId"), "chat-" + UUID.randomUUID());
        shaped.put("conversationId", conversationId);
        copySafeValue(response, shaped, "mode");
        copySafeValue(response, shaped, "position");
        copySafeValue(response, shaped, "fallbackReason");
        copySafeValue(response, shaped, "providerRequestId");
        JsonNode sources = firstArrayNode(
            response.get("sources"),
            response.get("documents"),
            nestedNode(response, "result.data.documents"),
            nestedNode(response, "result.data.ragResponse.documents"),
            nestedNode(response, "result.sanitizedPayload.data.documents"),
            nestedNode(response, "result.sanitizedPayload.data.ragResponse.documents")
        );
        ArrayNode safeSources = storefrontSafeDocuments(sources);
        shaped.set("sources", safeSources);
        JsonNode ragResponse = storefrontSafeRagResponse(response, sources, safeSources);
        if (ragResponse != null && !ragResponse.isEmpty()) {
            shaped.set("ragResponse", ragResponse);
        }
        shaped.set("actions", canonicalActions(response));
        JsonNode suggestions = firstArrayNode(
            response.get("suggestions"),
            nestedNode(response, "result.sanitizedPayload.suggestions"),
            nestedNode(response, "result.data.suggestions")
        );
        shaped.set("suggestions", suggestions != null ? suggestions.deepCopy() : objectMapper.createArrayNode());
        JsonNode metadata = response.get("metadata");
        if (metadata != null && metadata.isObject()) {
            shaped.set("metadata", metadata.deepCopy());
        }
        return shaped;
    }

    private JsonNode storefrontSafeRagResponse(JsonNode response, JsonNode rawDocuments, ArrayNode safeSources) {
        JsonNode ragResponse = firstObjectNode(
            response == null ? null : response.get("ragResponse"),
            nestedNode(response, "result.data.ragResponse"),
            nestedNode(response, "result.sanitizedPayload.data.ragResponse")
        );
        if (ragResponse == null || !ragResponse.isObject()) {
            return null;
        }
        ObjectNode safe = objectMapper.createObjectNode();
        copySafeValue(ragResponse, safe, "query");
        copySafeValue(ragResponse, safe, "optimizedQuery");
        copySafeValue(ragResponse, safe, "embeddingQuery");
        copySafeValue(ragResponse, safe, "entityType");
        copySafeValue(ragResponse, safe, "usedDocuments");
        copySafeValue(ragResponse, safe, "processingTimeMs");
        copySafeValue(ragResponse, safe, "requiresRetrieval");
        copySafeValue(ragResponse, safe, "requiresGeneration");

        String metadataEmbeddingQuery = firstNonBlank(
            nestedText(ragResponse, "metadata.embeddingQuery"),
            nestedText(response, "result.data.metadata.embeddingQuery"),
            nestedText(response, "result.metadata.embeddingQuery")
        );
        if (metadataEmbeddingQuery != null && !safe.has("embeddingQuery")) {
            safe.put("embeddingQuery", metadataEmbeddingQuery);
        }

        JsonNode documents = firstArrayNode(ragResponse.get("documents"), rawDocuments);
        ArrayNode safeDocuments = storefrontSafeDocuments(documents);
        if (safeDocuments.isEmpty() && safeSources != null && !safeSources.isEmpty()) {
            safeDocuments = safeSources.deepCopy();
        }
        safe.set("documents", safeDocuments);
        if (!safe.has("usedDocuments")) {
            safe.put("usedDocuments", safeDocuments.size());
        }
        return safe;
    }

    private JsonNode firstObjectNode(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && node.isObject()) {
                return node;
            }
        }
        return null;
    }

    private boolean storefrontDebugEnabled(ShopifyBridgeStoreSummary store) {
        return store != null
            && store.widgetDetail() != null
            && store.widgetDetail().settings() != null
            && store.widgetDetail().settings().debugEnabled();
    }

    private void attachDebugPayload(ObjectNode shaped, JsonNode upstreamResponse, ObjectNode normalizedRequest) {
        ObjectNode debug = objectMapper.createObjectNode();
        debug.put("contractVersion", DEBUG_CONTRACT_VERSION);
        debug.set("normalizedRequest", debugSafeNode(normalizedRequest, 0));
        debug.set("upstreamResponse", debugSafeNode(upstreamResponse, 0));

        ObjectNode diagnostics = objectMapper.createObjectNode();
        diagnostics.put("upstreamSuccess", upstreamResponse == null || !upstreamResponse.has("success")
            || upstreamResponse.path("success").asBoolean(true));
        copySafeValue(upstreamResponse, diagnostics, "conversationId");
        copySafeValue(upstreamResponse, diagnostics, "providerRequestId");
        copySafeValue(upstreamResponse, diagnostics, "fallbackReason");
        copySafeValue(shaped, diagnostics, "type");
        copySafeValue(shaped, diagnostics, "mode");
        copySafeValue(shaped, diagnostics, "position");
        diagnostics.put("extractedSourcesCount", shaped.path("sources").isArray() ? shaped.path("sources").size() : 0);
        diagnostics.put("extractedActionsCount", shaped.path("actions").isArray() ? shaped.path("actions").size() : 0);
        diagnostics.put("upstreamSourcesCount", debugSourceCount(upstreamResponse));
        diagnostics.put("upstreamActionsCount", debugActionCount(upstreamResponse));
        debug.set("diagnostics", diagnostics);

        shaped.set("debug", debug);
    }

    private int debugSourceCount(JsonNode response) {
        JsonNode sources = firstArrayNode(
            response == null ? null : response.get("sources"),
            response == null ? null : response.get("documents"),
            nestedNode(response, "result.data.documents"),
            nestedNode(response, "result.data.ragResponse.documents"),
            nestedNode(response, "result.sanitizedPayload.data.documents"),
            nestedNode(response, "result.sanitizedPayload.data.ragResponse.documents")
        );
        return sources == null ? 0 : sources.size();
    }

    private int debugActionCount(JsonNode response) {
        JsonNode actions = response == null ? null : response.get("actions");
        if (actions != null && actions.isArray()) {
            return actions.size();
        }
        JsonNode resultData = firstPresent(
            nestedNode(response, "result.data"),
            nestedNode(response, "result.sanitizedPayload.data")
        );
        return resultData != null && resultData.isObject() && !resultData.isEmpty() ? 1 : 0;
    }

    private JsonNode debugSafeNode(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (depth >= DEBUG_MAX_DEPTH) {
            return objectMapper.getNodeFactory().textNode("[debug-truncated-depth]");
        }
        if (node.isObject()) {
            ObjectNode safe = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (debugSensitiveField(field.getKey())) {
                    safe.put(field.getKey(), "[redacted]");
                } else {
                    safe.set(field.getKey(), debugSafeNode(field.getValue(), depth + 1));
                }
            }
            return safe;
        }
        if (node.isArray()) {
            ArrayNode safe = objectMapper.createArrayNode();
            int index = 0;
            for (JsonNode item : node) {
                if (index >= DEBUG_MAX_ARRAY_ITEMS) {
                    ObjectNode marker = objectMapper.createObjectNode();
                    marker.put("debugTruncated", true);
                    marker.put("remainingItems", node.size() - DEBUG_MAX_ARRAY_ITEMS);
                    safe.add(marker);
                    break;
                }
                safe.add(debugSafeNode(item, depth + 1));
                index++;
            }
            return safe;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(debugSafeText(node.asText("")));
        }
        return node.deepCopy();
    }

    private boolean debugSensitiveField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (DEBUG_SENSITIVE_FIELD_NAMES.contains(normalized)) {
            return true;
        }
        return normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("authorization")
            || normalized.contains("cookie")
            || normalized.contains("signature");
    }

    private String debugSafeText(String value) {
        String safe = value == null ? "" : value;
        safe = safe.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [redacted]");
        safe = safe.replaceAll("(?i)(shpat|shpss|shpca|shpua|shppa)_[A-Za-z0-9_]+", "$1_[redacted]");
        safe = safe.replaceAll("(?i)([?&](?:key|token|access_token|refresh_token|id_token|signature|hmac)=)[^&#\\s]+", "$1[redacted]");
        safe = safe.replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[redacted-email]");
        if (safe.length() > DEBUG_MAX_STRING_LENGTH) {
            return safe.substring(0, DEBUG_MAX_STRING_LENGTH) + "...[debug-truncated]";
        }
        return safe;
    }

    private ArrayNode canonicalActions(JsonNode response) {
        ArrayNode actions = objectMapper.createArrayNode();
        JsonNode existingActions = response == null ? null : response.get("actions");
        if (existingActions != null && existingActions.isArray()) {
            for (JsonNode action : existingActions) {
                JsonNode safe = storefrontSafeData(action);
                if (safe != null && !safe.isNull()) {
                    actions.add(safe);
                }
            }
        }
        if (!actions.isEmpty()) {
            return actions;
        }
        JsonNode safeData = storefrontSafeData(firstPresent(
            nestedNode(response, "result.data"),
            nestedNode(response, "result.sanitizedPayload.data")
        ));
        if (safeData != null && safeData.isObject() && !safeData.isEmpty()) {
            actions.add(safeData);
        }
        return actions;
    }

    private JsonNode firstArrayNode(JsonNode... nodes) {
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
        return first != null && !first.isNull() && !first.isMissingNode() ? first : second;
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
        copySafeValue(data, safe, "actionId");
        copySafeValue(data, safe, "message");
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
                textOrNull(document, "name"),
                textOrNull(document.path("entity"), "name"),
                textOrNull(document.path("metadata"), "shopifyDocumentTitle"),
                textOrNull(document.path("metadata"), "title"),
                textOrNull(document.path("metadata"), "name"),
                sourceTitleFromContent(document)
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
            String handle = firstNonBlank(
                textOrNull(document, "handle"),
                textOrNull(document.path("metadata"), "handle")
            );
            if (handle != null) {
                safeDocument.put("handle", handle);
            }
            String imageUrl = safeImageUrl(firstNonBlank(
                textOrNull(document, "imageUrl"),
                textOrNull(document.path("metadata"), "imageUrl"),
                textOrNull(document.path("metadata"), "featuredImageUrl"),
                textOrNull(document.path("metadata"), "productImageUrl")
            ));
            if (imageUrl != null) {
                safeDocument.put("imageUrl", imageUrl);
            }
            String imageAltText = firstNonBlank(
                textOrNull(document, "imageAltText"),
                textOrNull(document.path("metadata"), "imageAltText"),
                textOrNull(document.path("metadata"), "featuredImageAltText")
            );
            if (imageAltText != null) {
                safeDocument.put("imageAltText", imageAltText.length() > 120 ? imageAltText.substring(0, 120) : imageAltText);
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

    private String safeImageUrl(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > 1_000) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                return null;
            }
            if (!StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String sourceTitleFromContent(JsonNode document) {
        String content = trimToNull(textOrNull(document, "content"));
        if (content == null) {
            return null;
        }
        if (content.startsWith("{")) {
            try {
                JsonNode parsed = objectMapper.readTree(content);
                String title = firstNonBlank(textOrNull(parsed, "name"), textOrNull(parsed, "title"));
                if (title != null) {
                    return title.length() > 120 ? title.substring(0, 120) : title;
                }
            } catch (Exception ignored) {
                // Fall back to plain-text extraction below.
            }
        }
        String firstLine = content.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse(null);
        return firstLine == null || firstLine.length() > 120 ? null : firstLine;
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

    private String extractAnswer(JsonNode response) {
        for (String path : List.of(
            "safeSummary",
            "answer",
            "actions.0.message",
            "actions.0.actionResult.message",
            "result.sanitizedPayload.safeSummary",
            "result.sanitizedPayload.message",
            "result.sanitizedPayload.answer",
            "result.data.actionResult.message",
            "result.sanitizedPayload.data.actionResult.message",
            "result.message",
            "message"
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
            "actions.0.actionResult.data.toolResult.content",
            "actions.0.toolResult.content",
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
            if (current == null || current.isNull()) {
                return null;
            }
            if (current.isArray() && part.matches("\\d+")) {
                current = current.get(Integer.parseInt(part));
            } else if (current.isObject()) {
                current = current.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private record CustomerAccountAuthCopy(String message, String reason) {
    }

}
