package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreGoLiveService {

    private static final String SHOPIFY_BRIDGE_SHARED_SECRET_ENV = "SHOPIFY_BRIDGE_SHARED_SECRET";
    private static final String SHOPIFY_BRIDGE_API_KEY_HEADER = "X-BRIDGE-API-KEY";
    private static final List<String> SHOPIFY_COMPANION_ACTION_IDS = List.of(
        "list_products",
        "search_products",
        "get_product_details",
        "find_similar_products",
        "compare_products",
        "check_availability",
        "get_policy"
    );
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ShopifyStoreConnectionRepository repository;
    private final DeploymentService deploymentService;
    private final PlatformManagedProductServiceRepository productServiceRepository;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreGoLiveService(ShopifyStoreConnectionRepository repository,
                                     DeploymentService deploymentService,
                                     PlatformManagedProductServiceRepository productServiceRepository,
                                     ShopifyStoreConnectionService shopifyStoreConnectionService,
                                     PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.deploymentService = deploymentService;
        this.productServiceRepository = productServiceRepository;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary goLive(String shopDomain) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        ShopifyStoreConnectionSummary summary = shopifyStoreConnectionService.getConnection(store.getShopDomain());
        validateReadyForGoLive(summary);

        DeploymentDraftResponse draft = ensureShopifyCompanionSecurityDefaults(store.getDeploymentId());
        draft = ensureShopifyCompanionConnectorDefaults(store, draft);
        DeploymentVersionSummary version = deploymentService.publishDraft(draft.id());
        DeploymentReleaseSummary release = deploymentService.applyVersion(store.getDeploymentId(), version.id());

        store.setOnboardingStatus("GO_LIVE_REQUESTED");
        store.setUpdatedAt(Instant.now());
        repository.save(store);

        platformAuditService.record(
            "SHOPIFY_STORE_GO_LIVE_REQUESTED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", store.getDeploymentId(),
                "consumerId", store.getConsumerId(),
                "versionId", version.id(),
                "releaseId", release.id()
            )
        );

        return shopifyStoreConnectionService.getConnection(store.getShopDomain());
    }

    private void validateReadyForGoLive(ShopifyStoreConnectionSummary store) {
        if (store.readiness() == null || !store.readiness().goLiveEligible()) {
            String message = store.readiness() == null || store.readiness().goLiveBlockingReasons().isEmpty()
                ? "Shopify store is not ready for go-live yet."
                : store.readiness().goLiveBlockingReasons().get(0);
            throw new ResponseStatusException(CONFLICT, message);
        }
    }

    private DeploymentDraftResponse ensureShopifyCompanionSecurityDefaults(String deploymentId) {
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        ObjectNode securityConfig = ensureObject(draft.securityConfig());
        boolean changed = ShopifyCompanionRuntimeSecurityDefaults.apply(securityConfig, deploymentId);
        if (!changed) {
            return draft;
        }
        return deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                null,
                null,
                null,
                null,
                securityConfig,
                null,
                null,
                null,
                null
            )
        );
    }

    private DeploymentDraftResponse ensureShopifyCompanionConnectorDefaults(ShopifyStoreConnectionEntity store,
                                                                            DeploymentDraftResponse draft) {
        PlatformManagedProductServiceEntity productService = resolveProductService(store.getProductServiceId());
        String productServiceBaseUrl = productService == null ? null : blankToNull(productService.getBaseUrl());
        if (!hasText(productServiceBaseUrl)) {
            return draft;
        }

        ObjectNode routingConfig = ensureObject(draft.routingConfig());
        boolean changed = false;

        ObjectNode connector = ensureNestedObject(routingConfig, "connector");
        ObjectNode upstream = ensureNestedObject(connector, "upstream");
        changed |= putText(upstream, "base-url", trimTrailingSlash(productServiceBaseUrl));

        ObjectNode upstreamAuth = ensureNestedObject(upstream, "auth");
        changed |= putText(upstreamAuth, "type", "API_KEY");
        changed |= putText(upstreamAuth, "header", SHOPIFY_BRIDGE_API_KEY_HEADER);
        changed |= putText(upstreamAuth, "value", "${" + SHOPIFY_BRIDGE_SHARED_SECRET_ENV + "}");

        ObjectNode actions = ensureNestedObject(routingConfig, "actions");
        for (String actionId : SHOPIFY_COMPANION_ACTION_IDS) {
            changed |= ensureShopifyCompanionActionRoute(actions, actionId, store.getShopDomain());
        }

        if (!changed) {
            return draft;
        }

        return deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                null,
                null,
                routingConfig,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );
    }

    private boolean ensureShopifyCompanionActionRoute(ObjectNode actions,
                                                      String actionId,
                                                      String shopDomain) {
        ObjectNode action = ensureNestedObject(actions, actionId);
        boolean changed = false;
        changed |= putText(action, "method", "POST");
        changed |= putText(action, "path", "/api/admin/stores/" + shopDomain + "/actions/execute");
        ObjectNode request = ensureNestedObject(action, "request");
        ObjectNode body = ensureNestedObject(request, "body");
        changed |= putText(body, "actionId", "{{actionId}}");
        changed |= putText(body, "params", "{{params}}");
        changed |= putText(body, "idempotencyKey", "{{idempotencyKey}}");
        changed |= putText(body, "trace", "{{trace}}");
        return changed;
    }

    private ObjectNode ensureObject(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return JSON.objectNode();
    }

    private ObjectNode ensureNestedObject(ObjectNode node, String field) {
        JsonNode existing = node.path(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = JSON.objectNode();
        node.set(field, created);
        return created;
    }

    private PlatformManagedProductServiceEntity resolveProductService(String productServiceId) {
        return hasText(productServiceId) ? productServiceRepository.findById(productServiceId).orElse(null) : null;
    }

    private boolean putText(ObjectNode node, String field, String value) {
        String normalized = blankToNull(value);
        String existing = blankToNull(node.path(field).asText(null));
        if (java.util.Objects.equals(existing, normalized)) {
            return false;
        }
        if (normalized == null) {
            node.remove(field);
        } else {
            node.put(field, normalized);
        }
        return true;
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizeShopDomain(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase();
    }

}
