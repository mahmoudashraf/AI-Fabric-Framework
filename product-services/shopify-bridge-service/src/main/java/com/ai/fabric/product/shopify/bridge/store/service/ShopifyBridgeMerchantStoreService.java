package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalResponse;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalRequest;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessRequestSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSupportProfileRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationEventSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSelectedEntitiesRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateStoreVectorizationPolicyRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSourceSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontPreviewService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionDiagnosticsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class ShopifyBridgeMerchantStoreService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeProperties properties;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeSourcePreflightService sourcePreflightService;
    private final ShopifyBridgeStoreSyncService storeSyncService;
    private final ShopifyStorefrontPreviewService storefrontPreviewService;
    private final ShopifyBridgeUsageService usageService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService;
    private final ShopifyStorefrontGovernedActionService governedActionService;
    private final ShopifyBridgeSupportReadinessService supportReadinessService;

    public ShopifyBridgeMerchantStoreService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                             ShopifyBridgeProperties properties,
                                             ShopifyInstallRecordService installRecordService,
                                             ShopifyBridgeInstallCredentialService installCredentialService,
                                             ShopifyBridgeSourcePreflightService sourcePreflightService,
                                             ShopifyBridgeStoreSyncService storeSyncService,
                                             ShopifyStorefrontPreviewService storefrontPreviewService,
                                             ShopifyBridgeUsageService usageService,
                                             ShopifyBridgeBillingService billingService,
                                             ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService,
                                             ShopifyStorefrontGovernedActionService governedActionService,
                                             ShopifyBridgeSupportReadinessService supportReadinessService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.properties = properties;
        this.installRecordService = installRecordService;
        this.installCredentialService = installCredentialService;
        this.sourcePreflightService = sourcePreflightService;
        this.storeSyncService = storeSyncService;
        this.storefrontPreviewService = storefrontPreviewService;
        this.usageService = usageService;
        this.billingService = billingService;
        this.webhookSubscriptionDiagnosticsService = webhookSubscriptionDiagnosticsService;
        this.governedActionService = governedActionService;
        this.supportReadinessService = supportReadinessService;
    }

    public ShopifyBridgeMerchantSessionResponse session(ShopifyMerchantSession merchantSession,
                                                        String appBridgeHost) {
        ShopifyInstallRecordSummary installRecord = installRecordService.recordAuthenticatedSession(merchantSession, appBridgeHost);
        ShopifyBridgeStoreSummary store = findStoreOrNull(merchantSession.shopDomain());
        boolean installRecoveryRequired = installRecoveryRequired(installRecord, store);
        ShopifyBridgeSupportReadinessSummary supportReadiness = supportReadinessService.summarizeForShop(merchantSession.shopDomain());
        return new ShopifyBridgeMerchantSessionResponse(
            merchantSession.shopDomain(),
            merchantSession.destination(),
            merchantSession.userId(),
            merchantSession.expiresAt(),
            installRecoveryRequired,
            installRecoveryRequired ? "This shop must complete the Shopify install flow again before Companion can continue onboarding." : null,
            installRecoveryRequired ? buildInstallUrl(merchantSession.shopDomain()) : null,
            supportReadiness,
            installRecord,
            store
        );
    }

    public ShopifyBridgeStoreSummary connect(ShopifyMerchantSession merchantSession,
                                             String authorizationHeader) {
        ShopifyBridgeStoreSummary store = acquireConnectedCredentials(merchantSession, authorizationHeader).store();
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_CONNECT");
        return store;
    }

    public ShopifyBridgeStoreSummary runSourcePreflight(ShopifyMerchantSession merchantSession,
                                                        String authorizationHeader) {
        ShopifyBridgeStoreSummary store = sourcePreflightService.run(acquireConnectedCredentials(merchantSession, authorizationHeader));
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_SOURCE_PREFLIGHT");
        return store;
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(ShopifyMerchantSession merchantSession,
                                                         String authorizationHeader) {
        acquireConnectedCredentials(merchantSession, authorizationHeader);
        ShopifyBridgeStoreBootstrapResponse response = platformShopifyStoreClient.bootstrap(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_BOOTSTRAP");
        return response;
    }

    public ShopifyBridgeBillingSummary billingSummary(ShopifyMerchantSession merchantSession) {
        return installCredentialService.resolvePersistedMaterial(merchantSession.shopDomain())
            .map(acquisition -> billingService.summarizeForShop(merchantSession.shopDomain(), acquisition.tokenExchangeMaterial().accessToken()))
            .orElseGet(() -> billingService.summarizeForShop(merchantSession.shopDomain(), null));
    }

    public List<ShopifyBridgePartnerAccessRequestSummary> listPartnerAccessRequests(ShopifyMerchantSession merchantSession) {
        return platformShopifyStoreClient.listPartnerAccessRequests(merchantSession.shopDomain());
    }

    public ShopifyBridgePartnerAccessDecisionSummary approvePartnerAccessRequest(ShopifyMerchantSession merchantSession,
                                                                                String requestId,
                                                                                ShopifyBridgePartnerAccessDecisionRequest request) {
        ShopifyBridgePartnerAccessDecisionRequest payload = normalizePartnerDecisionRequest(merchantSession, request);
        ShopifyBridgePartnerAccessDecisionSummary summary = platformShopifyStoreClient.approvePartnerAccessRequest(
            merchantSession.shopDomain(),
            requestId,
            payload
        );
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_PARTNER_ACCESS_APPROVED");
        return summary;
    }

    public ShopifyBridgePartnerAccessDecisionSummary denyPartnerAccessRequest(ShopifyMerchantSession merchantSession,
                                                                             String requestId,
                                                                             ShopifyBridgePartnerAccessDecisionRequest request) {
        ShopifyBridgePartnerAccessDecisionRequest payload = normalizePartnerDecisionRequest(merchantSession, request);
        ShopifyBridgePartnerAccessDecisionSummary summary = platformShopifyStoreClient.denyPartnerAccessRequest(
            merchantSession.shopDomain(),
            requestId,
            payload
        );
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_PARTNER_ACCESS_DENIED");
        return summary;
    }

    public ShopifyBridgeBillingApprovalResponse requestBillingApproval(ShopifyMerchantSession merchantSession,
                                                                       String authorizationHeader,
                                                                       ShopifyBridgeBillingApprovalRequest request) {
        ShopifyBridgeCredentialAcquisition acquisition = acquireConnectedCredentials(merchantSession, authorizationHeader);
        ShopifyBridgeBillingApprovalResponse response = billingService.createApproval(
            merchantSession.shopDomain(),
            acquisition.tokenExchangeMaterial().accessToken(),
            request == null ? null : request.tierKey()
        );
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_BILLING_APPROVAL_REQUESTED");
        return response;
    }

    public ShopifyBridgeStoreSummary goLive(ShopifyMerchantSession merchantSession,
                                            String authorizationHeader) {
        ShopifyBridgeCredentialAcquisition acquisition = acquireConnectedCredentials(merchantSession, authorizationHeader);
        ShopifyBridgeBillingSummary billingSummary = billingService.summarizeForShop(
            merchantSession.shopDomain(),
            acquisition.tokenExchangeMaterial().accessToken()
        );
        if (billingSummary.launchBlocked()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                billingSummary.message() == null || billingSummary.message().isBlank()
                    ? "Shopify Companion go-live is blocked until billing setup is complete."
                    : billingSummary.message()
            );
        }
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.goLive(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_GO_LIVE");
        return store;
    }

    public ShopifyBridgeStoreSummary syncNow(ShopifyMerchantSession merchantSession,
                                             String authorizationHeader) {
        ShopifyBridgeStoreSummary store = storeSyncService.sync(acquireConnectedCredentials(merchantSession, authorizationHeader));
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_SYNC_NOW");
        return store;
    }

    public ShopifyBridgeStoreVectorizationSummary vectorization(ShopifyMerchantSession merchantSession) {
        return platformShopifyStoreClient.getVectorization(merchantSession.shopDomain());
    }

    public ShopifyBridgeStoreVectorizationSummary reconcileVectorization(ShopifyMerchantSession merchantSession) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.reconcileVectorization(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_RECONCILED");
        return summary;
    }

    public ShopifyBridgeStoreVectorizationSummary vectorizeNow(ShopifyMerchantSession merchantSession) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.vectorizeNow(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_RUN_TRIGGERED");
        return summary;
    }

    public ShopifyBridgeStoreVectorizationSummary indexAllEnabledData(ShopifyMerchantSession merchantSession) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.indexAllEnabledData(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_INDEX_ALL");
        return summary;
    }

    public ShopifyBridgeStoreVectorizationSummary reindexAllEnabledData(ShopifyMerchantSession merchantSession) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.reindexAllEnabledData(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_REINDEX_ALL");
        return summary;
    }

    public ShopifyBridgeStoreVectorizationSummary reindexSelectedEntityTypes(ShopifyMerchantSession merchantSession,
                                                                             ShopifyBridgeStoreVectorizationSelectedEntitiesRequest request) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.reindexSelectedEntityTypes(merchantSession.shopDomain(), request);
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_REINDEX_SELECTED");
        return summary;
    }

    public ShopifyBridgeStoreVectorizationSummary updateVectorizationPolicy(ShopifyMerchantSession merchantSession,
                                                                            ShopifyBridgeUpdateStoreVectorizationPolicyRequest request) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.updateVectorizationPolicy(merchantSession.shopDomain(), request);
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_POLICY_UPDATED");
        return summary;
    }

    public List<ShopifyBridgeStoreVectorizationEventSummary> vectorizationEvents(ShopifyMerchantSession merchantSession, int limit) {
        return platformShopifyStoreClient.fetchVectorizationEvents(merchantSession.shopDomain(), limit);
    }

    public ShopifyBridgeStoreVectorizationSummary replayVectorizationEvent(ShopifyMerchantSession merchantSession,
                                                                           String eventId) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.replayVectorizationEvent(merchantSession.shopDomain(), eventId);
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_EVENT_REPLAYED");
        return summary;
    }

    public ShopifyBridgeStoreVectorizationSummary retryLastFailedAutoRun(ShopifyMerchantSession merchantSession) {
        ShopifyBridgeStoreVectorizationSummary summary = platformShopifyStoreClient.retryLastFailedAutoRun(merchantSession.shopDomain());
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_VECTORIZATION_AUTO_RETRY");
        return summary;
    }

    public ShopifyWebhookSubscriptionStatusSummary webhookSubscriptions(ShopifyMerchantSession merchantSession) {
        return webhookSubscriptionDiagnosticsService.forShop(merchantSession.shopDomain());
    }

    public ShopifyBridgeSupportReadinessSummary supportReadiness(ShopifyMerchantSession merchantSession) {
        return supportReadinessService.summarizeForShop(merchantSession.shopDomain());
    }

    public ShopifyBridgeSupportReadinessSummary updateSupportProfile(ShopifyMerchantSession merchantSession,
                                                                    ShopifyBridgeUpdateSupportProfileRequest request) {
        platformShopifyStoreClient.updateSupportProfile(
            merchantSession.shopDomain(),
            request == null ? new ShopifyBridgeUpdateSupportProfileRequest(null, null, null, null, null) : request
        );
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_SUPPORT_PROFILE_UPDATED");
        return supportReadinessService.summarizeForShop(merchantSession.shopDomain());
    }

    public ShopifyStorefrontPreviewResponse storefrontPreview(ShopifyMerchantSession merchantSession) {
        return storefrontPreviewService.preview(merchantSession.shopDomain());
    }

    public List<ShopifyBridgeGovernedActionAuditSummary> recentGovernedActions(ShopifyMerchantSession merchantSession, int limit) {
        return governedActionService.recentActions(merchantSession.shopDomain(), limit);
    }

    public ShopifyBridgeStoreSummary updateWidgetSettings(ShopifyMerchantSession merchantSession,
                                                          ShopifyBridgeUpdateWidgetSettingsRequest request) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.updateWidgetSettings(merchantSession.shopDomain(), request);
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_WIDGET_SETTINGS_UPDATED");
        return store;
    }

    public ShopifyBridgeStoreSummary updateSourceSettings(ShopifyMerchantSession merchantSession,
                                                          ShopifyBridgeUpdateSourceSettingsRequest request) {
        ShopifyBridgeStoreSummary current = findStoreOrNull(merchantSession.shopDomain());
        boolean productsEnabled = request.productsEnabled() == null || request.productsEnabled();
        boolean collectionsEnabled = request.collectionsEnabled() == null || request.collectionsEnabled();
        boolean pagesEnabled = request.pagesEnabled() == null || request.pagesEnabled();
        boolean policiesEnabled = request.policiesEnabled() == null || request.policiesEnabled();
        boolean articlesEnabled = request.articlesEnabled() == null || request.articlesEnabled();
        boolean metaobjectsEnabled = request.metaobjectsEnabled() == null || request.metaobjectsEnabled();

        if (current == null) {
            ShopifyBridgeStoreSummary store = platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                merchantSession.shopDomain(),
                defaultDisplayName(merchantSession.shopDomain()),
                properties.serviceRef(),
                null,
                null,
                null,
                "INSTALLED",
                "NOT_SYNCED",
                "NOT_RUN",
                "NOT_ENABLED",
                "CONNECTED",
                productsEnabled,
                collectionsEnabled,
                pagesEnabled,
                policiesEnabled,
                articlesEnabled,
                metaobjectsEnabled
            ));
            usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_SOURCE_SETTINGS_UPDATED");
            return store;
        }

        boolean togglesChanged = current.productsEnabled() != productsEnabled
            || current.collectionsEnabled() != collectionsEnabled
            || current.pagesEnabled() != pagesEnabled
            || current.policiesEnabled() != policiesEnabled
            || current.articlesEnabled() != articlesEnabled
            || current.metaobjectsEnabled() != metaobjectsEnabled;

        if (!togglesChanged) {
            return current;
        }

        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
            current.shopDomain(),
            current.displayName(),
            current.productServiceRef(),
            current.customerId(),
            current.deploymentId(),
            current.consumerId(),
            current.installStatus(),
            "NOT_SYNCED",
            "NOT_RUN",
            current.widgetStatus(),
            hasPlatformBindings(current) ? "PLATFORM_BOOTSTRAPPED" : "CONNECTED",
            productsEnabled,
            collectionsEnabled,
            pagesEnabled,
            policiesEnabled,
            articlesEnabled,
            metaobjectsEnabled
        ));
        if (hasPlatformBindings(store)) {
            platformShopifyStoreClient.reconcileVectorization(store.shopDomain());
        }
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_SOURCE_SETTINGS_UPDATED");
        return store;
    }

    private ShopifyBridgeCredentialAcquisition acquireConnectedCredentials(ShopifyMerchantSession merchantSession,
                                                                           String authorizationHeader) {
        ShopifyBridgeStoreSummary current = findStoreOrNull(merchantSession.shopDomain());
        if (current == null) {
            platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                merchantSession.shopDomain(),
                defaultDisplayName(merchantSession.shopDomain()),
                properties.serviceRef(),
                null,
                null,
                null,
                "INSTALLED",
                "NOT_SYNCED",
                "NOT_RUN",
                "NOT_ENABLED",
                "CONNECTED",
                true,
                true,
                true,
                true,
                true,
                true
            ));
        }
        return installCredentialService.acquireAndPersistMaterial(merchantSession, authorizationHeader);
    }

    private ShopifyBridgePartnerAccessDecisionRequest normalizePartnerDecisionRequest(ShopifyMerchantSession merchantSession,
                                                                                     ShopifyBridgePartnerAccessDecisionRequest request) {
        String approverName = request == null || request.approverName() == null || request.approverName().isBlank()
            ? "Shopify admin " + merchantSession.userId()
            : request.approverName().trim();
        return new ShopifyBridgePartnerAccessDecisionRequest(
            approverName,
            request == null ? null : request.approverEmail(),
            request == null || request.approvedScope() == null || request.approvedScope().isBlank()
                ? "IMPLEMENTATION_SUPPORT"
                : request.approvedScope().trim(),
            request == null ? null : request.decisionReason()
        );
    }

    private ShopifyBridgeStoreSummary findStoreOrNull(String shopDomain) {
        try {
            return platformShopifyStoreClient.getStore(shopDomain);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Platform denied Shopify store access.", ex);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Platform Shopify admin credentials were rejected.", ex);
        }
    }

    private String defaultDisplayName(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            return "Shopify store";
        }
        return shopDomain.trim().toLowerCase();
    }

    private boolean hasPlatformBindings(ShopifyBridgeStoreSummary store) {
        return store.customerId() != null && !store.customerId().isBlank()
            && store.deploymentId() != null && !store.deploymentId().isBlank()
            && store.consumerId() != null && !store.consumerId().isBlank();
    }

    private boolean installRecoveryRequired(ShopifyInstallRecordSummary installRecord,
                                            ShopifyBridgeStoreSummary store) {
        return "UNINSTALLED".equalsIgnoreCase(installRecord == null ? null : installRecord.status())
            || "UNINSTALLED".equalsIgnoreCase(store == null ? null : store.installStatus());
    }

    private String buildInstallUrl(String shopDomain) {
        String baseUrl = properties.publicBaseUrl();
        String normalizedBaseUrl = (baseUrl == null || baseUrl.isBlank())
            ? ""
            : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl + "/auth/shopify/install")
            .queryParam("shop", shopDomain)
            .build(true)
            .toUriString();
    }
}
