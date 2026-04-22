package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyInstallFlowService {

    private static final String DEFAULT_APP_SCOPES = "read_products,read_content,read_legal_policies";
    private static final Logger log = LoggerFactory.getLogger(ShopifyInstallFlowService.class);

    private final ShopifyBridgeProperties properties;
    private final ShopifyInstallStateService installStateService;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyWebhookSubscriptionService webhookSubscriptionService;
    private final RestClient restClient;

    public ShopifyInstallFlowService(ShopifyBridgeProperties properties,
                                     ShopifyInstallStateService installStateService,
                                     PlatformShopifyStoreClient platformShopifyStoreClient,
                                     ShopifyInstallRecordService installRecordService,
                                     ShopifyWebhookSubscriptionService webhookSubscriptionService,
                                     RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.installStateService = installStateService;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installRecordService = installRecordService;
        this.webhookSubscriptionService = webhookSubscriptionService;
        this.restClient = restClientBuilder.build();
    }

    public URI beginInstall(String shopDomain) {
        requireInstallConfig();
        String normalizedShop = installStateService.normalizeShopDomain(shopDomain);
        String state = installStateService.issueState(normalizedShop);
        return UriComponentsBuilder.fromHttpUrl("https://" + normalizedShop + "/admin/oauth/authorize")
            .queryParam("client_id", properties.shopifyApiKey())
            .queryParam("scope", DEFAULT_APP_SCOPES)
            .queryParam("redirect_uri", callbackUrl())
            .queryParam("state", state)
            .build(true)
            .toUri();
    }

    public URI completeInstall(String shopDomain,
                               String code,
                               String host,
                               String state,
                               String rawQuery) {
        requireInstallConfig();
        String normalizedShop = installStateService.normalizeShopDomain(shopDomain);
        installStateService.verifyState(state, normalizedShop);
        verifyCallbackSignature(rawQuery);

        Map<String, Object> tokenResponse = exchangeAuthorizationCode(normalizedShop, code);
        String accessToken = requireField(tokenResponse, "access_token");
        String scopesText = normalize(tokenResponse.get("scope"));

        ensurePlatformStore(normalizedShop);
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.upsertCredentials(
            normalizedShop,
            new ShopifyBridgeUpsertStoreCredentialsRequest(
                accessToken,
                null,
                null,
                null,
                scopesText,
                false
            )
        );

        installRecordService.recordInstall(
            normalizedShop,
            "https://" + normalizedShop,
            blankToNull(host),
            scopesText,
            store.credentials() == null ? null : store.credentials().accessTokenSecretRef(),
            store.credentials() == null ? null : store.credentials().refreshTokenSecretRef(),
            store.credentials() == null ? null : store.credentials().accessTokenExpiresAt(),
            store.credentials() == null ? null : store.credentials().refreshTokenExpiresAt()
        );
        reconcileSubscriptionsSafely(normalizedShop, accessToken);

        return embeddedAppUrl(normalizedShop, host);
    }

    private void reconcileSubscriptionsSafely(String shopDomain, String accessToken) {
        try {
            webhookSubscriptionService.reconcileContentSubscriptions(shopDomain, accessToken);
        } catch (RuntimeException ex) {
            log.warn("Shopify webhook subscription reconciliation failed for shop={}", shopDomain, ex);
        }
    }

    private void ensurePlatformStore(String shopDomain) {
        try {
            ShopifyBridgeStoreSummary existing = platformShopifyStoreClient.getStore(shopDomain);
            platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                existing.shopDomain(),
                existing.displayName(),
                existing.productServiceRef(),
                existing.customerId(),
                existing.deploymentId(),
                existing.consumerId(),
                "INSTALLED",
                existing.syncStatus(),
                existing.sourceReadinessStatus(),
                existing.widgetStatus(),
                existing.onboardingStatus(),
                existing.productsEnabled(),
                existing.collectionsEnabled(),
                existing.pagesEnabled(),
                existing.policiesEnabled()
            ));
        } catch (HttpClientErrorException.NotFound ex) {
            platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                shopDomain,
                shopDomain,
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
                true
            ));
        }
    }

    private Map<String, Object> exchangeAuthorizationCode(String shopDomain, String code) {
        String normalizedCode = normalize(code);
        if (normalizedCode == null) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify authorization code.");
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.shopifyApiKey());
        form.add("client_secret", properties.shopifyApiSecret());
        form.add("code", normalizedCode);

        Map<String, Object> response = restClient.post()
            .uri("https://" + shopDomain + "/admin/oauth/access_token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(form)
            .retrieve()
            .body(new ParameterizedTypeReference<>() { });
        if (response == null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify install callback returned an empty token response.");
        }
        return response;
    }

    private void verifyCallbackSignature(String rawQuery) {
        String providedHmac = null;
        List<String> messagePairs = new ArrayList<>();
        for (String segment : splitQuery(rawQuery)) {
            if (segment.isBlank()) {
                continue;
            }
            int separator = segment.indexOf('=');
            String key = separator >= 0 ? segment.substring(0, separator) : segment;
            String value = separator >= 0 ? segment.substring(separator + 1) : "";
            String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            if ("hmac".equals(normalizedKey)) {
                providedHmac = value;
                continue;
            }
            if ("signature".equals(normalizedKey)) {
                continue;
            }
            messagePairs.add(key + "=" + value);
        }
        if (providedHmac == null || providedHmac.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify callback HMAC.");
        }
        messagePairs.sort(Comparator.naturalOrder());
        String message = String.join("&", messagePairs);
        String expected = hmacHex(message);
        if (!constantTimeEquals(expected, providedHmac.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify callback HMAC.");
        }
    }

    private List<String> splitQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : rawQuery.split("&")) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private URI embeddedAppUrl(String shopDomain, String host) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalizedPublicBaseUrl())
            .queryParam("shop", shopDomain);
        String normalizedHost = blankToNull(host);
        if (normalizedHost != null) {
            builder.queryParam("host", normalizedHost);
        }
        return builder.build(true).toUri();
    }

    private String callbackUrl() {
        return normalizedPublicBaseUrl() + "/auth/shopify/callback";
    }

    private String normalizedPublicBaseUrl() {
        if (properties.publicBaseUrl().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge public base URL is not configured.");
        }
        return properties.publicBaseUrl().endsWith("/")
            ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
            : properties.publicBaseUrl();
    }

    private void requireInstallConfig() {
        if (properties.shopifyApiKey().isBlank() || properties.shopifyApiSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge client credentials are not configured.");
        }
        if (properties.serviceRef().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge service ref is not configured.");
        }
        if (properties.publicBaseUrl().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge public base URL is not configured.");
        }
    }

    private String requireField(Map<String, Object> response, String field) {
        String value = normalize(response.get(field));
        if (value == null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify install callback response is missing " + field + ".");
        }
        return value;
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.shopifyApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify Shopify callback signature.", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String blankToNull(String value) {
        return normalize(value);
    }
}
