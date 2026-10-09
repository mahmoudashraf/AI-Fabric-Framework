package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionCompletionRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantResponse;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontEngagementEventRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyAIWorkspaceAdapterRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyAIWorkspaceAdapterResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontEngagementService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontBootstrapService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontOrderLookupService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@RequestMapping("/api/storefront/shops")
public class ShopifyStorefrontController {

    private static final String SHOPPER_SESSION_HEADER = "X-AI-FABRIC-SHOPPER-SESSION-ID";

    private final ShopifyStorefrontBootstrapService storefrontBootstrapService;
    private final ShopifyStorefrontChatService storefrontChatService;
    private final ShopifyStorefrontEngagementService storefrontEngagementService;
    private final ShopifyStorefrontOrderLookupService storefrontOrderLookupService;
    private final ShopifyStorefrontGovernedActionService governedActionService;
    private final ShopifyBridgeUsageService usageService;

    public ShopifyStorefrontController(ShopifyStorefrontBootstrapService storefrontBootstrapService,
                                       ShopifyStorefrontChatService storefrontChatService,
                                       ShopifyStorefrontEngagementService storefrontEngagementService,
                                       ShopifyStorefrontOrderLookupService storefrontOrderLookupService,
                                       ShopifyStorefrontGovernedActionService governedActionService,
                                       ShopifyBridgeUsageService usageService) {
        this.storefrontBootstrapService = storefrontBootstrapService;
        this.storefrontChatService = storefrontChatService;
        this.storefrontEngagementService = storefrontEngagementService;
        this.storefrontOrderLookupService = storefrontOrderLookupService;
        this.governedActionService = governedActionService;
        this.usageService = usageService;
    }

    @GetMapping("/{shopDomain}/bootstrap")
    public ShopifyStorefrontBootstrapResponse bootstrap(@PathVariable String shopDomain,
                                                        @RequestParam(value = "pageType", required = false) String pageType) {
        ShopifyStorefrontBootstrapResponse response = storefrontBootstrapService.bootstrap(shopDomain, pageType);
        if (response.available()) {
            usageService.recordEvent(shopDomain, "STOREFRONT_BOOTSTRAP");
        }
        return response;
    }

    @PostMapping("/{shopDomain}/workspace/bootstrap")
    public ShopifyAIWorkspaceAdapterResponse workspaceBootstrap(
        @PathVariable String shopDomain,
        @RequestBody(required = false) ShopifyAIWorkspaceAdapterRequest request
    ) {
        validateWorkspaceAdapterRequest(request);
        ShopifyStorefrontBootstrapResponse storefront = storefrontBootstrapService.bootstrap(shopDomain, null);
        if (!storefront.available()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                storefront.message() == null ? "The Shopify storefront adapter is not ready." : storefront.message());
        }
        URI query = safeBridgeUri(storefront.bridgeQueryUrl());
        URI suggestions = safeBridgeUri(storefront.bridgeSuggestionsUrl());
        if (!query.getScheme().equals(suggestions.getScheme())
            || !query.getAuthority().equals(suggestions.getAuthority())) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify storefront routes do not share one bridge origin.");
        }
        String bootstrapPath = "/api/storefront/shops/" + shopDomain + "/bootstrap";
        return new ShopifyAIWorkspaceAdapterResponse(
            "loomai-workspace-private-adapter-v1",
            query.getScheme() + "://" + query.getAuthority(),
            Map.of(
                "queryUrl", query.getRawPath(),
                "suggestionsUrl", suggestions.getRawPath(),
                "authContextUrl", bootstrapPath,
                "shellConfigUrl", bootstrapPath,
                "conversationsUrl", query.getRawPath(),
                "conversationItemUrlTemplate", query.getRawPath()
            ),
            Map.of(SHOPPER_SESSION_HEADER, "workspace-" + UUID.randomUUID()),
            "omit",
            false,
            false,
            Map.of("conversations", false)
        );
    }

    @PostMapping("/{shopDomain}/chat/query")
    public JsonNode query(@PathVariable String shopDomain,
                          @RequestBody(required = false) JsonNode request,
                          @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                          String shopperSessionId) {
        JsonNode response = storefrontChatService.query(shopDomain, request, shopperSessionId);
        usageService.recordQueryInsight(shopDomain, "STOREFRONT_QUERY", request, "launcher");
        return response;
    }

    @PostMapping("/{shopDomain}/chat/suggestions")
    public JsonNode suggestions(@PathVariable String shopDomain,
                                @RequestBody(required = false) JsonNode request,
                                @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                String shopperSessionId) {
        JsonNode response = storefrontChatService.suggestions(shopDomain, request, shopperSessionId);
        usageService.recordEvent(shopDomain, "STOREFRONT_SUGGESTIONS");
        return response;
    }

    @PostMapping("/{shopDomain}/events")
    public ResponseEntity<Void> event(@PathVariable String shopDomain,
                                      @RequestBody(required = false) ShopifyStorefrontEngagementEventRequest request,
                                      @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                      String shopperSessionId) {
        storefrontEngagementService.record(shopDomain, request, shopperSessionId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{shopDomain}/support/order-lookup")
    public ShopifyStorefrontOrderLookupResponse orderLookup(@PathVariable String shopDomain,
                                                            @RequestBody(required = false) ShopifyStorefrontOrderLookupRequest request,
                                                            @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                                            String shopperSessionId) {
        ShopifyStorefrontOrderLookupResponse response = storefrontOrderLookupService.lookup(shopDomain, request);
        usageService.recordEvent(shopDomain, "STOREFRONT_ORDER_LOOKUP");
        if (response.matched()) {
            usageService.recordEvent(shopDomain, "STOREFRONT_ORDER_LOOKUP_MATCHED");
        }
        return response;
    }

    @PostMapping("/{shopDomain}/actions/grant")
    public ShopifyStorefrontGovernedActionGrantResponse grantAction(@PathVariable String shopDomain,
                                                                    @RequestBody(required = false) ShopifyStorefrontGovernedActionGrantRequest request,
                                                                    @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                                                    String shopperSessionId) {
        return governedActionService.grant(shopDomain, request, shopperSessionId);
    }

    @PostMapping("/{shopDomain}/actions/complete")
    public ShopifyBridgeGovernedActionAuditSummary completeAction(@PathVariable String shopDomain,
                                                                  @RequestBody(required = false) ShopifyStorefrontGovernedActionCompletionRequest request,
                                                                  @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                                                  String shopperSessionId) {
        return governedActionService.complete(shopDomain, request, shopperSessionId);
    }

    private void validateWorkspaceAdapterRequest(ShopifyAIWorkspaceAdapterRequest request) {
        if (request == null
            || !"loomai-workspace-private-adapter-request-v1".equals(request.schemaVersion())
            || request.installationId() == null
            || !request.installationId().matches("awi_pub_[a-f0-9]{32}")
            || request.assignmentRevision() == null
            || !request.assignmentRevision().matches("sha256:[a-f0-9]{64}")) {
            throw new ResponseStatusException(BAD_REQUEST, "The AI Workspace adapter request is invalid.");
        }
    }

    private URI safeBridgeUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawPath() == null || !uri.getRawPath().startsWith("/api/storefront/")) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify storefront routes are invalid.");
        }
    }
}
