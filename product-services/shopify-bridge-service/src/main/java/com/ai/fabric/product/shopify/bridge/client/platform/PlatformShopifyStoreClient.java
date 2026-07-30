package com.ai.fabric.product.shopify.bridge.client.platform;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerRuntimeAssignmentResponse;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicRuntimeEndpointsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessInviteRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessInviteSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessRequestSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeCreateProvisioningJobRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeCustomerAccountConfigSummary;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class PlatformShopifyStoreClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformShopifyStoreClient.class);
    private static final int CHAT_RETRY_ATTEMPTS = 3;
    private static final long CHAT_RETRY_SLEEP_MS = 250L;
    private static final String DEFAULT_RUNTIME_API_KEY_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    private static final String DEFAULT_RUNTIME_ASSERTION_HEADER = "X-AIFABRIC-RUNTIME-AUTHORIZATION";
    private static final String DEFAULT_RUNTIME_ASSERTION_SCHEME = "Bearer";
    private static final String PRIVATE_RUNTIME_AUTH_MODE = "PRIVATE_RUNTIME_SIGNED_ASSERTION";
    private static final String BACKEND_MEDIATED_PRIVATE_RUNTIME = "BACKEND_MEDIATED_PRIVATE_RUNTIME";
    private static final String SCOPE_CHAT_QUERY = "chat:query";
    private static final String SCOPE_CHAT_SUGGESTIONS = "chat:suggestions";
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,120}$");

    private static final ParameterizedTypeReference<List<ShopifyBridgeStoreSummary>> STORE_LIST_TYPE =
        new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ShopifyBridgePartnerAccessRequestSummary>> PARTNER_ACCESS_REQUEST_LIST_TYPE =
        new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ShopifyBridgeStoreVectorizationEventSummary>> VECTORIZATION_EVENT_LIST_TYPE =
        new ParameterizedTypeReference<>() { };

    private final ShopifyBridgeProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, CachedRuntimeAssignment> runtimeAssignmentCache = new ConcurrentHashMap<>();

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

    public ShopifyBridgeCustomerAccountConfigSummary getCustomerAccountConfig(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/customer-account-config")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeCustomerAccountConfigSummary.class);
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

    public ShopifyBridgePartnerAccessInviteSummary sendPartnerAccessInvite(String shopDomain,
                                                                           String requestId,
                                                                           ShopifyBridgePartnerAccessInviteRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl()
                + "/api/merchant/partner-access/requests/"
                + encodePath(requestId)
                + "/invite?shopDomain="
                + encodeQueryParam(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request == null ? new ShopifyBridgePartnerAccessInviteRequest(null) : request)
            .retrieve()
            .body(ShopifyBridgePartnerAccessInviteSummary.class);
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

    public PlatformPublicConsumerRuntimeAssignmentResponse getConsumerRuntimeAssignment(String consumerId) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/public/consumers/" + encodePath(consumerId) + "/runtime-assignment")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(PlatformPublicConsumerRuntimeAssignmentResponse.class);
    }

    public void warmConsumerRuntimeAssignment(String consumerId) {
        runtimeAssignment(consumerId);
    }

    public JsonNode queryConsumerBridgeChat(String consumerId, JsonNode request, String shopperSessionId) {
        return executeDirectRuntimeChat(consumerId, "query", request, shopperSessionId, List.of(SCOPE_CHAT_QUERY));
    }

    public JsonNode suggestConsumerBridgeChat(String consumerId, JsonNode request, String shopperSessionId) {
        return executeDirectRuntimeChat(consumerId, "suggestions", request, shopperSessionId, List.of(SCOPE_CHAT_SUGGESTIONS));
    }

    private JsonNode executeDirectRuntimeChat(String consumerId,
                                              String endpointKind,
                                              JsonNode request,
                                              String shopperSessionId,
                                              List<String> scopes) {
        RestClientResponseException lastResponseException = null;
        ResourceAccessException lastAccessException = null;
        for (int attempt = 1; attempt <= CHAT_RETRY_ATTEMPTS; attempt += 1) {
            RuntimeAssignment assignment = runtimeAssignment(consumerId);
            String targetUrl = endpointUrl(assignment.assignment(), endpointKind);
            try {
                return restClient.post()
                    .uri(targetUrl)
                    .headers(headers -> {
                        headers.set(assignment.apiKeyHeader(), requireRuntimeTrustedBackendApiKey());
                        headers.set(
                            assignment.assertionHeader(),
                            assignment.tokenScheme() + " " + issueRuntimeAssertion(
                                assignment,
                                shopperSessionId,
                                scopes
                            )
                        );
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
                runtimeAssignmentCache.remove(consumerId);
                log.warn("Retrying Shopify bridge direct runtime chat after HTTP {} on attempt {}/{} for consumer {} endpoint {}",
                    ex.getStatusCode().value(), attempt, CHAT_RETRY_ATTEMPTS, consumerId, endpointKind);
                sleepBeforeRetry();
            } catch (ResourceAccessException ex) {
                if (attempt == CHAT_RETRY_ATTEMPTS) {
                    throw ex;
                }
                lastAccessException = ex;
                runtimeAssignmentCache.remove(consumerId);
                log.warn("Retrying Shopify bridge direct runtime chat after transport failure on attempt {}/{} for consumer {} endpoint {}: {}",
                    attempt, CHAT_RETRY_ATTEMPTS, consumerId, endpointKind, ex.getMessage());
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
        return statusCode.value() == 401
            || statusCode.value() == 403
            || statusCode.value() == 404
            || statusCode.value() == 429
            || statusCode.is5xxServerError();
    }

    private RuntimeAssignment runtimeAssignment(String consumerId) {
        String normalizedConsumerId = requireText(consumerId, "Consumer id is required.");
        CachedRuntimeAssignment cached = runtimeAssignmentCache.get(normalizedConsumerId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.assignment();
        }
        PlatformPublicConsumerRuntimeAssignmentResponse assignment = getConsumerRuntimeAssignment(normalizedConsumerId);
        RuntimeAssignment resolved = validateRuntimeAssignment(normalizedConsumerId, assignment);
        int ttl = assignment.cacheTtlSeconds() > 0
            ? assignment.cacheTtlSeconds()
            : properties.runtimeAssignmentCacheTtlSeconds();
        runtimeAssignmentCache.put(
            normalizedConsumerId,
            new CachedRuntimeAssignment(resolved, Instant.now().plusSeconds(ttl))
        );
        return resolved;
    }

    private RuntimeAssignment validateRuntimeAssignment(String consumerId,
                                                        PlatformPublicConsumerRuntimeAssignmentResponse assignment) {
        if (assignment == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Runtime assignment is not available for consumer " + consumerId + ".");
        }
        String runtimeBaseUrl = requireText(assignment.runtimeBaseUrl(), "Runtime assignment is missing runtimeBaseUrl.");
        String deploymentId = requireText(assignment.deploymentId(), "Runtime assignment is missing deploymentId.");
        String runtimeAuthMode = requireText(assignment.runtimeAuthMode(), "Runtime assignment is missing runtimeAuthMode.");
        String preferredIntegrationMode = requireText(assignment.preferredIntegrationMode(), "Runtime assignment is missing preferredIntegrationMode.");
        if (!PRIVATE_RUNTIME_AUTH_MODE.equals(runtimeAuthMode)
            || !BACKEND_MEDIATED_PRIVATE_RUNTIME.equals(preferredIntegrationMode)) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Runtime assignment is not configured for backend-mediated private runtime chat."
            );
        }
        if (!assignment.externalIntegrationReady()) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Runtime assignment is not ready for external backend-mediated traffic."
            );
        }
        PlatformPublicRuntimeEndpointsSummary endpoints = assignment.endpoints();
        if (endpoints == null
            || !StringUtils.hasText(endpoints.chatQueryUrl())
            || !StringUtils.hasText(endpoints.suggestionsUrl())) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Runtime assignment is missing chat endpoints.");
        }
        String issuer = StringUtils.hasText(assignment.privateRuntimeIssuer())
            ? assignment.privateRuntimeIssuer().trim()
            : properties.runtimePrivateAssertionIssuer();
        String audience = StringUtils.hasText(assignment.privateRuntimeAudience())
            ? assignment.privateRuntimeAudience().trim()
            : deploymentId;
        return new RuntimeAssignment(
            assignment,
            consumerId,
            deploymentId,
            runtimeBaseUrl,
            issuer,
            audience,
            StringUtils.hasText(assignment.trustedBackendApiKeyHeader())
                ? assignment.trustedBackendApiKeyHeader().trim()
                : DEFAULT_RUNTIME_API_KEY_HEADER,
            StringUtils.hasText(assignment.privateAssertionAuthorizationHeader())
                ? assignment.privateAssertionAuthorizationHeader().trim()
                : DEFAULT_RUNTIME_ASSERTION_HEADER,
            StringUtils.hasText(assignment.privateAssertionTokenScheme())
                ? assignment.privateAssertionTokenScheme().trim()
                : DEFAULT_RUNTIME_ASSERTION_SCHEME
        );
    }

    private String endpointUrl(PlatformPublicConsumerRuntimeAssignmentResponse assignment, String endpointKind) {
        PlatformPublicRuntimeEndpointsSummary endpoints = assignment.endpoints();
        if ("suggestions".equals(endpointKind)) {
            return requireText(endpoints.suggestionsUrl(), "Runtime assignment is missing suggestionsUrl.");
        }
        return requireText(endpoints.chatQueryUrl(), "Runtime assignment is missing chatQueryUrl.");
    }

    private String issueRuntimeAssertion(RuntimeAssignment assignment,
                                         String shopperSessionId,
                                         List<String> scopes) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            ShopperIdentity identity = shopperIdentity(assignment.consumerId(), shopperSessionId);
            payload.put("sub", identity.subjectId());
            payload.put("subjectType", "END_USER");
            payload.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
            payload.put("callerType", "TRUSTED_BACKEND");
            payload.put("sessionId", identity.sessionId());
            payload.put("deploymentId", assignment.deploymentId());
            payload.put("iss", assignment.issuer());
            payload.put("aud", assignment.audience());
            payload.put("exp", Instant.now().plus(Duration.ofMinutes(5)).toString());
            ArrayNode grantedScopes = payload.putArray("scopes");
            if (scopes != null) {
                scopes.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .forEach(grantedScopes::add);
            }
            String payloadSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));
            String signatureSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signRuntimeAssertion(payloadSegment));
            return "rpa1." + payloadSegment + "." + signatureSegment;
        } catch (Exception ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Failed to issue runtime private assertion.", ex);
        }
    }

    private byte[] signRuntimeAssertion(String payloadSegment) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireRuntimePrivateAssertionSigningKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign runtime private assertion.", ex);
        }
    }

    private ShopperIdentity shopperIdentity(String consumerId, String shopperSessionId) {
        String normalizedSessionId = normalizeSessionId(consumerId, shopperSessionId);
        String subjectId = "consumer-session-" + shortSha(consumerId + "|" + normalizedSessionId);
        return new ShopperIdentity(subjectId, normalizedSessionId);
    }

    private String normalizeSessionId(String consumerId, String shopperSessionId) {
        String trimmed = shopperSessionId == null ? null : shopperSessionId.trim();
        if (!StringUtils.hasText(trimmed)) {
            return "storefront-" + shortSha(consumerId);
        }
        if (SAFE_SESSION_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "storefront-" + shortSha(consumerId + "|" + trimmed);
    }

    private String shortSha(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash runtime shopper identity.", ex);
        }
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

    private String requireRuntimeTrustedBackendApiKey() {
        if (properties.runtimeTrustedBackendApiKey().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge runtime trusted backend API key is not configured.");
        }
        return properties.runtimeTrustedBackendApiKey();
    }

    private String requireRuntimePrivateAssertionSigningKey() {
        if (properties.runtimePrivateAssertionSigningKey().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge runtime private assertion signing key is not configured.");
        }
        return properties.runtimePrivateAssertionSigningKey();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, message);
        }
        return value.trim();
    }

    private String encodePath(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private record CachedRuntimeAssignment(RuntimeAssignment assignment, Instant expiresAt) {
    }

    private record RuntimeAssignment(
        PlatformPublicConsumerRuntimeAssignmentResponse assignment,
        String consumerId,
        String deploymentId,
        String runtimeBaseUrl,
        String issuer,
        String audience,
        String apiKeyHeader,
        String assertionHeader,
        String tokenScheme
    ) {
    }

    private record ShopperIdentity(String subjectId, String sessionId) {
    }
}
