package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
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
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreGoLiveService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ShopifyStoreConnectionRepository repository;
    private final DeploymentService deploymentService;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreGoLiveService(ShopifyStoreConnectionRepository repository,
                                     DeploymentService deploymentService,
                                     ShopifyStoreConnectionService shopifyStoreConnectionService,
                                     PlatformAuditService platformAuditService) {
        this.repository = repository;
        this.deploymentService = deploymentService;
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
        String authzMode = securityConfig.path("authzMode").asText(null);
        if (ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED.equalsIgnoreCase(authzMode)) {
            return draft;
        }
        securityConfig.put("authzMode", ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED);
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

    private ObjectNode ensureObject(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return JSON.objectNode();
    }

    private String normalizeShopDomain(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase();
    }

}
