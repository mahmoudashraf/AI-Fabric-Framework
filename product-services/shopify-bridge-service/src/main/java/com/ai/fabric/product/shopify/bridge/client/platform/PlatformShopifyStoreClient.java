package com.ai.fabric.product.shopify.bridge.client.platform;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessRequestSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeCreateProvisioningJobRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeProvisioningJobSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeProvisioningStatusSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordBillingStateRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWebhookEventRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordedBillingStateSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationEventSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSelectedEntitiesRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportProfileSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSyncStoreDocumentsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeThinkerHealthSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateStoreVectorizationPolicyRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSupportProfileRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class PlatformShopifyStoreClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformShopifyStoreClient.class);
    private static final int CHAT_RETRY_ATTEMPTS = 3;
    private static final long CHAT_RETRY_SLEEP_MS = 250L;

    private static final ParameterizedTypeReference<List<ShopifyBridgeStoreSummary>> STORE_LIST_TYPE =
        new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ShopifyBridgePartnerAccessRequestSummary>> PARTNER_ACCESS_REQUEST_LIST_TYPE =
        new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ShopifyBridgeStoreVectorizationEventSummary>> VECTORIZATION_EVENT_LIST_TYPE =
        new ParameterizedTypeReference<>() { };

    private final ShopifyBridgeProperties properties;
    private final RestClient restClient;

    public PlatformShopifyStoreClient(RestClient.Builder restClientBuilder,
                                      ShopifyBridgeProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public List<ShopifyBridgeStoreSummary> listStores() {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(STORE_LIST_TYPE);
    }

    public ShopifyBridgeStoreSummary getStore(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeRecordedBillingStateSummary getBillingState(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/billing-state")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeRecordedBillingStateSummary.class);
    }

    public ShopifyBridgeRecordedBillingStateSummary recordBillingState(String shopDomain,
                                                                       ShopifyBridgeRecordBillingStateRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/billing-state")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeRecordedBillingStateSummary.class);
    }

    public List<ShopifyBridgePartnerAccessRequestSummary> listPartnerAccessRequests(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/merchant/partner-access/requests?shopDomain=" + encodeQueryParam(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(PARTNER_ACCESS_REQUEST_LIST_TYPE);
    }

    public ShopifyBridgePartnerAccessDecisionSummary approvePartnerAccessRequest(String shopDomain,
                                                                                String requestId,
                                                                                ShopifyBridgePartnerAccessDecisionRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl()
                + "/api/merchant/partner-access/requests/"
                + encodePath(requestId)
                + "/approve?shopDomain="
                + encodeQueryParam(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgePartnerAccessDecisionSummary.class);
    }

    public ShopifyBridgePartnerAccessDecisionSummary denyPartnerAccessRequest(String shopDomain,
                                                                             String requestId,
                                                                             ShopifyBridgePartnerAccessDecisionRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl()
                + "/api/merchant/partner-access/requests/"
                + encodePath(requestId)
                + "/deny?shopDomain="
                + encodeQueryParam(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgePartnerAccessDecisionSummary.class);
    }

    public ShopifyBridgePartnerAccessDecisionSummary revokePartnerAccessRequest(String shopDomain,
                                                                               String requestId,
                                                                               ShopifyBridgePartnerAccessDecisionRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl()
                + "/api/merchant/partner-access/requests/"
                + encodePath(requestId)
                + "/revoke?shopDomain="
                + encodeQueryParam(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgePartnerAccessDecisionSummary.class);
    }

    public ShopifyBridgeStoreSummary upsertStore(ShopifyBridgeUpsertStoreRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary upsertCredentials(String shopDomain, ShopifyBridgeUpsertStoreCredentialsRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/credentials")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary clearCredentials(String shopDomain) {
        return restClient.delete()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/credentials")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeResolvedStoreCredentials resolveCredentialMaterial(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/credentials/material")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeResolvedStoreCredentials.class);
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/bootstrap")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreBootstrapResponse.class);
    }

    public ShopifyBridgeProvisioningStatusSummary getProvisioningStatus(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/provisioning")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeProvisioningStatusSummary.class);
    }

    public ShopifyBridgeProvisioningJobSummary enqueueProvisioning(String shopDomain,
                                                                   ShopifyBridgeCreateProvisioningJobRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/provisioning-jobs")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeProvisioningJobSummary.class);
    }

    public ShopifyBridgeStoreSummary goLive(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/go-live")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary markUninstalled(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/uninstall")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public void deleteStore(String shopDomain, boolean force) {
        restClient.delete()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "?force=" + force)
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .toBodilessEntity();
    }

    public ShopifyBridgeStoreSummary recordSourcePreflight(String shopDomain, ShopifyBridgeRecordSourcePreflightRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/source-preflight")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary recordSyncStatus(String shopDomain, ShopifyBridgeRecordSyncStatusRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/sync-status")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary syncDocuments(String shopDomain, ShopifyBridgeSyncStoreDocumentsRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/documents/sync")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary recordWebhookEvent(String shopDomain, ShopifyBridgeRecordWebhookEventRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/webhook-events")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary recordWidgetStatus(String shopDomain, ShopifyBridgeRecordWidgetStatusRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/widget-status")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary updateWidgetSettings(String shopDomain, ShopifyBridgeUpdateWidgetSettingsRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/widget-settings")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeSupportProfileSummary getSupportProfile(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/support-profile")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeSupportProfileSummary.class);
    }

    public ShopifyBridgeSupportProfileSummary updateSupportProfile(String shopDomain, ShopifyBridgeUpdateSupportProfileRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/support-profile")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeSupportProfileSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary getVectorization(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary reconcileVectorization(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/reconcile")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary vectorizeNow(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/vectorize-now")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary indexAllEnabledData(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/index-all")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary reindexAllEnabledData(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/reindex-all")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary reindexSelectedEntityTypes(String shopDomain,
                                                                             ShopifyBridgeStoreVectorizationSelectedEntitiesRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/reindex-selected")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary updateVectorizationPolicy(String shopDomain,
                                                                           ShopifyBridgeUpdateStoreVectorizationPolicyRequest request) {
        return restClient.put()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/policy")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public List<ShopifyBridgeStoreVectorizationEventSummary> fetchVectorizationEvents(String shopDomain, int limit) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/events?limit=" + limit)
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(VECTORIZATION_EVENT_LIST_TYPE);
    }

    public ShopifyBridgeStoreVectorizationSummary replayVectorizationEvent(String shopDomain, String eventId) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/events/" + encodePath(eventId) + "/replay")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeStoreVectorizationSummary retryLastFailedAutoRun(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/vectorization/retry-last-failed-auto-run")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreVectorizationSummary.class);
    }

    public ShopifyBridgeThinkerHealthSummary thinkerHealth(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/thinker-health")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeThinkerHealthSummary.class);
    }

    public PlatformPublicConsumerDeploymentCredentialsResponse getConsumerCredentials(String consumerId) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/public/consumers/" + encodePath(consumerId) + "/credentials")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(PlatformPublicConsumerDeploymentCredentialsResponse.class);
    }

    public JsonNode queryConsumerBridgeChat(String consumerId, JsonNode request, String shopperSessionId) {
        return executeConsumerBridgeChat("/api/public/consumers/" + encodePath(consumerId) + "/bridge/chat/query", request, shopperSessionId);
    }

    public JsonNode suggestConsumerBridgeChat(String consumerId, JsonNode request, String shopperSessionId) {
        return executeConsumerBridgeChat("/api/public/consumers/" + encodePath(consumerId) + "/bridge/chat/suggestions", request, shopperSessionId);
    }

    private JsonNode executeConsumerBridgeChat(String path, JsonNode request, String shopperSessionId) {
        RestClientResponseException lastResponseException = null;
        ResourceAccessException lastAccessException = null;
        for (int attempt = 1; attempt <= CHAT_RETRY_ATTEMPTS; attempt += 1) {
            try {
                return restClient.post()
                    .uri(requirePlatformBaseUrl() + path)
                    .headers(headers -> {
                        headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey());
                        if (shopperSessionId != null && !shopperSessionId.isBlank()) {
                            headers.set("X-AI-FABRIC-SHOPPER-SESSION-ID", shopperSessionId.trim());
                        }
                    })
                    .body(request == null ? Map.of() : request)
                    .retrieve()
                    .body(JsonNode.class);
            } catch (RestClientResponseException ex) {
                if (!retryableChatStatus(ex.getStatusCode()) || attempt == CHAT_RETRY_ATTEMPTS) {
                    throw ex;
                }
                lastResponseException = ex;
                log.warn("Retrying Shopify bridge consumer chat after upstream HTTP {} on attempt {}/{} for path {}",
                    ex.getStatusCode().value(), attempt, CHAT_RETRY_ATTEMPTS, path);
                sleepBeforeRetry();
            } catch (ResourceAccessException ex) {
                if (attempt == CHAT_RETRY_ATTEMPTS) {
                    throw ex;
                }
                lastAccessException = ex;
                log.warn("Retrying Shopify bridge consumer chat after transport failure on attempt {}/{} for path {}: {}",
                    attempt, CHAT_RETRY_ATTEMPTS, path, ex.getMessage());
                sleepBeforeRetry();
            }
        }
        if (lastResponseException != null) {
            throw lastResponseException;
        }
        if (lastAccessException != null) {
            throw lastAccessException;
        }
        throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge consumer chat failed before a response was returned.");
    }

    private boolean retryableChatStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(CHAT_RETRY_SLEEP_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge consumer chat retry interrupted.", ex);
        }
    }

    private String requirePlatformBaseUrl() {
        if (properties.platformBaseUrl().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge platform base URL is not configured.");
        }
        return properties.platformBaseUrl().endsWith("/")
            ? properties.platformBaseUrl().substring(0, properties.platformBaseUrl().length() - 1)
            : properties.platformBaseUrl();
    }

    private String requirePlatformAdminApiKey() {
        if (properties.platformAdminApiKey().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge platform admin API key is not configured.");
        }
        return properties.platformAdminApiKey();
    }

    private String encodePath(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }
}
