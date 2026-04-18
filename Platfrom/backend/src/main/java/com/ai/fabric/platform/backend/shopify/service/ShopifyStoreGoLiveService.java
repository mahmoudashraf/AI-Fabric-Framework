package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreGoLiveService {

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
        validateReadyForGoLive(store);

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(store.getDeploymentId());
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

    private void validateReadyForGoLive(ShopifyStoreConnectionEntity store) {
        if (!"INSTALLED".equalsIgnoreCase(store.getInstallStatus())) {
            throw new ResponseStatusException(CONFLICT, "Shopify store must be installed before go-live can be requested.");
        }
        if (!hasText(store.getCustomerId()) || !hasText(store.getDeploymentId()) || !hasText(store.getConsumerId())) {
            throw new ResponseStatusException(CONFLICT, "Shopify store must be bootstrapped to a customer, deployment, and consumer before go-live can be requested.");
        }
        if (!"READY".equalsIgnoreCase(store.getSourceReadinessStatus())) {
            throw new ResponseStatusException(CONFLICT, "Shopify store source readiness must be READY before go-live can be requested.");
        }
        if ("BLOCKED".equalsIgnoreCase(store.getOnboardingStatus())) {
            throw new ResponseStatusException(CONFLICT, "Shopify store is currently BLOCKED and cannot go live until the blocking issue is resolved.");
        }
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!hasText(shopDomain)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
