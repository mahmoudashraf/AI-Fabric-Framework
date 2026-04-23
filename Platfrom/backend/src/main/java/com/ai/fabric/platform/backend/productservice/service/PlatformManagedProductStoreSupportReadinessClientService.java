package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportReadinessSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportSubscriptionSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedProductStoreSupportReadinessClientService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final String BRIDGE_ADMIN_API_KEY_HEADER = "X-BRIDGE-API-KEY";

    private final PlatformManagedProductServiceService serviceService;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PlatformManagedProductStoreSupportReadinessClientService(PlatformManagedProductServiceService serviceService,
                                                                    ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                                                    PlatformSecretService platformSecretService,
                                                                    ObjectMapper objectMapper) {
        this.serviceService = serviceService;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    }

    public PlatformManagedProductServiceStoreSupportReadinessSummary getStoreSupportReadiness(String serviceRef, String shopDomain) {
        try {
            return parseStoreSupportReadiness(
                shopDomain,
                fetchStoreAdminPayload(
                    serviceRef,
                    shopDomain,
                    "/support-readiness",
                    "Managed product support readiness request failed."
                )
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, firstNonBlank(ex.getMessage(), "Managed product support readiness request failed."), ex);
        }
    }

    private JsonNode fetchStoreAdminPayload(String serviceRef,
                                            String shopDomain,
                                            String endpointSuffix,
                                            String failureMessage) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        requireStoreAdminBridgeConfiguration(serviceRef, service);
        requireMappedStore(serviceRef, service, shopDomain);
        String apiKey = requireStoreAdminApiKey(service);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(service.getBaseUrl(), "/api/admin/stores/" + encodePath(shopDomain) + endpointSuffix)))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header(BRIDGE_ADMIN_API_KEY_HEADER, apiKey)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(CONFLICT, failureMessage + " HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, firstNonBlank(ex.getMessage(), failureMessage), ex);
        }
    }

    private PlatformManagedProductServiceStoreSupportReadinessSummary parseStoreSupportReadiness(String shopDomain, JsonNode node) {
        return new PlatformManagedProductServiceStoreSupportReadinessSummary(
            shopDomain,
            text(node, "status", "UNKNOWN"),
            text(node, "message", "Managed product service did not return support readiness diagnostics."),
            text(node, "lifecycleStage", "UNKNOWN"),
            node.path("orderLookupSupported").asBoolean(false),
            node.path("orderLookupScopeGranted").asBoolean(false),
            node.path("allOrdersScopeGranted").asBoolean(false),
            node.path("appScopesUpdateWebhookReady").asBoolean(false),
            node.path("installRecoveryRequired").asBoolean(false),
            text(node, "installRecoveryUrl", null),
            node.path("scopeGrantRequired").asBoolean(false),
            text(node, "scopeGrantUrl", null),
            text(node, "installStatus", "UNKNOWN"),
            text(node, "billingTier", null),
            text(node, "billingStatus", "UNKNOWN"),
            stringList(node.path("grantedScopes")),
            stringList(node.path("missingScopes")),
            stringList(node.path("activeSubscriptionNames")),
            parseSupportSubscriptions(node.path("activeSubscriptions")),
            parseSupportProfile(node.path("supportProfile")),
            node.path("merchantHandoffConfigured").asBoolean(false),
            text(node, "merchantHandoffMessage", null),
            stringList(node.path("nextActions")),
            stringList(node.path("verificationMethods")),
            stringList(node.path("supportedCapabilities")),
            stringList(node.path("blockedCapabilities"))
        );
    }

    private List<PlatformManagedProductServiceStoreSupportSubscriptionSummary> parseSupportSubscriptions(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlatformManagedProductServiceStoreSupportSubscriptionSummary> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            values.add(new PlatformManagedProductServiceStoreSupportSubscriptionSummary(
                text(item, "subscriptionId", null),
                text(item, "name", null),
                text(item, "status", "UNKNOWN"),
                text(item, "tierKey", "UNKNOWN"),
                item.path("active").asBoolean(false)
            ));
        }
        return List.copyOf(values);
    }

    private PlatformManagedProductServiceStoreSupportProfileSummary parseSupportProfile(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new PlatformManagedProductServiceStoreSupportProfileSummary(null, null, null, null, null, false);
        }
        return new PlatformManagedProductServiceStoreSupportProfileSummary(
            text(node, "contactEmail", null),
            text(node, "contactUrl", null),
            text(node, "helpCenterUrl", null),
            text(node, "orderLookupPageUrl", null),
            text(node, "supportPolicyNote", null),
            node.path("merchantHandoffConfigured").asBoolean(false)
        );
    }

    private void requireStoreAdminBridgeConfiguration(String serviceRef, PlatformManagedProductServiceEntity service) {
        if (!hasText(service.getBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare a base URL yet: " + serviceRef);
        }
        if (!hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare an admin secret: " + serviceRef);
        }
    }

    private void requireMappedStore(String serviceRef, PlatformManagedProductServiceEntity service, String shopDomain) {
        shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), shopDomain)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify store " + shopDomain + " is not mapped to managed product service " + serviceRef + "."
            ));
    }

    private String requireStoreAdminApiKey(PlatformManagedProductServiceEntity service) {
        String apiKey = platformSecretService.resolveSecret(service.getSecretName());
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(CONFLICT, "Managed product service admin secret is missing.");
        }
        return apiKey;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item == null ? null : trimToNull(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = trimToNull(value.asText());
        return text == null ? fallback : text;
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private String encodePath(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
