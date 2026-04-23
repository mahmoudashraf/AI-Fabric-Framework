package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyWebhookSubscriptionService {

    private static final String LIST_SUBSCRIPTIONS_QUERY = """
        query ShopifyBridgeWebhookSubscriptions($topics: [WebhookSubscriptionTopic!]) {
          webhookSubscriptions(first: 50, topics: $topics) {
            edges {
              node {
                id
                topic
                uri
                name
              }
            }
          }
        }
        """;

    private static final String CREATE_SUBSCRIPTION_MUTATION = """
        mutation ShopifyBridgeCreateWebhookSubscription($topic: WebhookSubscriptionTopic!, $webhookSubscription: WebhookSubscriptionInput!) {
          webhookSubscriptionCreate(topic: $topic, webhookSubscription: $webhookSubscription) {
            webhookSubscription {
              id
              topic
              uri
              name
            }
            userErrors {
              field
              message
            }
          }
        }
        """;

    private static final String DELETE_SUBSCRIPTION_MUTATION = """
        mutation ShopifyBridgeDeleteWebhookSubscription($id: ID!) {
          webhookSubscriptionDelete(id: $id) {
            deletedWebhookSubscriptionId
            userErrors {
              field
              message
            }
          }
        }
        """;

    private static final List<DesiredWebhookSubscription> DESIRED_SUBSCRIPTIONS = List.of(
        new DesiredWebhookSubscription("APP_UNINSTALLED", "loom-app-uninstalled"),
        new DesiredWebhookSubscription("APP_SCOPES_UPDATE", "loom-app-scopes-update"),
        new DesiredWebhookSubscription("APP_SUBSCRIPTIONS_UPDATE", "loom-app-subscriptions-update"),
        new DesiredWebhookSubscription("PRODUCTS_CREATE", "loom-products-create"),
        new DesiredWebhookSubscription("PRODUCTS_UPDATE", "loom-products-update"),
        new DesiredWebhookSubscription("PRODUCTS_DELETE", "loom-products-delete"),
        new DesiredWebhookSubscription("COLLECTIONS_CREATE", "loom-collections-create"),
        new DesiredWebhookSubscription("COLLECTIONS_UPDATE", "loom-collections-update"),
        new DesiredWebhookSubscription("COLLECTIONS_DELETE", "loom-collections-delete"),
        new DesiredWebhookSubscription("METAOBJECTS_CREATE", "loom-metaobjects-create"),
        new DesiredWebhookSubscription("METAOBJECTS_UPDATE", "loom-metaobjects-update"),
        new DesiredWebhookSubscription("METAOBJECTS_DELETE", "loom-metaobjects-delete"),
        new DesiredWebhookSubscription("SHOP_UPDATE", "loom-shop-update")
    );

    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;
    private final ShopifyBridgeProperties properties;

    public ShopifyWebhookSubscriptionService(ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                             ShopifyBridgeProperties properties) {
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.properties = properties;
    }

    public void reconcileContentSubscriptions(String shopDomain, String accessToken) {
        String webhookUri = webhookUri();
        for (DesiredWebhookSubscription desired : DESIRED_SUBSCRIPTIONS) {
            reconcileTopic(shopDomain, accessToken, desired, webhookUri);
        }
    }

    public ShopifyWebhookSubscriptionStatusSummary inspectContentSubscriptions(String shopDomain, String accessToken) {
        String webhookUri = webhookUri();
        List<ShopifyWebhookSubscriptionTopicStatusSummary> topics = DESIRED_SUBSCRIPTIONS.stream()
            .map(desired -> inspectTopic(shopDomain, accessToken, desired, webhookUri))
            .toList();
        int readyCount = (int) topics.stream().filter(topic -> "READY".equalsIgnoreCase(topic.status())).count();
        int missingCount = (int) topics.stream().filter(topic -> "MISSING".equalsIgnoreCase(topic.status())).count();
        int driftedCount = (int) topics.stream().filter(topic -> "DRIFTED".equalsIgnoreCase(topic.status())).count();
        String status = driftedCount > 0 || missingCount > 0 ? "DEGRADED" : "READY";
        String message = "READY".equals(status)
            ? "All required Shopify webhook subscriptions are present."
            : "Some required Shopify webhook subscriptions are missing or drifted.";
        return new ShopifyWebhookSubscriptionStatusSummary(
            normalizeShopDomain(shopDomain),
            status,
            message,
            webhookUri,
            DESIRED_SUBSCRIPTIONS.size(),
            readyCount,
            missingCount,
            driftedCount,
            Instant.now(),
            topics
        );
    }

    public int expectedSubscriptionCount() {
        return DESIRED_SUBSCRIPTIONS.size();
    }

    public List<String> expectedTopics() {
        return DESIRED_SUBSCRIPTIONS.stream().map(DesiredWebhookSubscription::topic).toList();
    }

    public String expectedWebhookUri() {
        return webhookUri();
    }

    private void reconcileTopic(String shopDomain,
                                String accessToken,
                                DesiredWebhookSubscription desired,
                                String webhookUri) {
        List<ExistingWebhookSubscription> existing = listSubscriptions(shopDomain, accessToken, desired.topic());
        boolean satisfied = existing.stream().anyMatch(subscription ->
            same(subscription.name(), desired.name()) && same(subscription.uri(), webhookUri)
        );
        if (satisfied) {
            return;
        }

        boolean hasCompatibleUnnamedSubscription = existing.stream().anyMatch(subscription ->
            same(subscription.uri(), webhookUri)
        );
        if (hasCompatibleUnnamedSubscription) {
            return;
        }

        for (ExistingWebhookSubscription subscription : existing) {
            if (same(subscription.name(), desired.name()) && !same(subscription.uri(), webhookUri)) {
                deleteSubscription(shopDomain, accessToken, subscription.id());
            }
        }

        createSubscription(shopDomain, accessToken, desired, webhookUri);
    }

    private ShopifyWebhookSubscriptionTopicStatusSummary inspectTopic(String shopDomain,
                                                                      String accessToken,
                                                                      DesiredWebhookSubscription desired,
                                                                      String webhookUri) {
        List<ExistingWebhookSubscription> existing = listSubscriptions(shopDomain, accessToken, desired.topic());
        ExistingWebhookSubscription exactMatch = existing.stream()
            .filter(subscription -> same(subscription.name(), desired.name()) && same(subscription.uri(), webhookUri))
            .findFirst()
            .orElse(null);
        if (exactMatch != null) {
            return new ShopifyWebhookSubscriptionTopicStatusSummary(
                desired.topic(),
                desired.name(),
                "READY",
                exactMatch.id(),
                exactMatch.name(),
                exactMatch.uri(),
                "Expected subscription is present."
            );
        }

        ExistingWebhookSubscription compatibleUri = existing.stream()
            .filter(subscription -> same(subscription.uri(), webhookUri))
            .findFirst()
            .orElse(null);
        if (compatibleUri != null) {
            return new ShopifyWebhookSubscriptionTopicStatusSummary(
                desired.topic(),
                desired.name(),
                "READY",
                compatibleUri.id(),
                compatibleUri.name(),
                compatibleUri.uri(),
                compatibleUri.name() == null || compatibleUri.name().isBlank()
                    ? "Compatible unnamed subscription is present."
                    : "Compatible subscription is present."
            );
        }

        ExistingWebhookSubscription namedDrift = existing.stream()
            .filter(subscription -> same(subscription.name(), desired.name()))
            .findFirst()
            .orElse(null);
        if (namedDrift != null) {
            return new ShopifyWebhookSubscriptionTopicStatusSummary(
                desired.topic(),
                desired.name(),
                "DRIFTED",
                namedDrift.id(),
                namedDrift.name(),
                namedDrift.uri(),
                "Subscription name matches, but the webhook URI does not."
            );
        }

        return new ShopifyWebhookSubscriptionTopicStatusSummary(
            desired.topic(),
            desired.name(),
            "MISSING",
            null,
            null,
            null,
            "Required Shopify webhook subscription is missing."
        );
    }

    private List<ExistingWebhookSubscription> listSubscriptions(String shopDomain,
                                                                String accessToken,
                                                                String topic) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            LIST_SUBSCRIPTIONS_QUERY,
            Map.of("topics", List.of(topic))
        );
        failOnGraphQlErrors(response, "Shopify webhook subscription lookup failed.");
        Map<String, Object> data = requireMap(response.get("data"), "Shopify webhook subscription lookup returned no data.");
        Map<String, Object> subscriptions = requireMap(
            data.get("webhookSubscriptions"),
            "Shopify webhook subscription lookup returned no webhookSubscriptions payload."
        );
        List<?> edges = requireList(subscriptions.get("edges"), "Shopify webhook subscription lookup returned no edges.");
        return edges.stream()
            .map(this::requireMapFromListItem)
            .map(edge -> requireMap(edge.get("node"), "Shopify webhook subscription lookup returned an invalid node."))
            .map(node -> new ExistingWebhookSubscription(
                requiredText(node.get("id"), "Shopify webhook subscription is missing id."),
                requiredText(node.get("topic"), "Shopify webhook subscription is missing topic."),
                requiredText(node.get("uri"), "Shopify webhook subscription is missing uri."),
                optionalText(node.get("name"))
            ))
            .toList();
    }

    private void createSubscription(String shopDomain,
                                    String accessToken,
                                    DesiredWebhookSubscription desired,
                                    String webhookUri) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            CREATE_SUBSCRIPTION_MUTATION,
            Map.of(
                "topic", desired.topic(),
                "webhookSubscription", Map.of(
                    "uri", webhookUri,
                    "name", desired.name()
                )
            )
        );
        failOnGraphQlErrors(response, "Shopify webhook subscription creation failed.");
        Map<String, Object> data = requireMap(response.get("data"), "Shopify webhook subscription creation returned no data.");
        Map<String, Object> payload = requireMap(
            data.get("webhookSubscriptionCreate"),
            "Shopify webhook subscription creation returned no payload."
        );
        failOnUserErrors(payload.get("userErrors"), "Shopify webhook subscription creation failed.");
    }

    private void deleteSubscription(String shopDomain, String accessToken, String subscriptionId) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            DELETE_SUBSCRIPTION_MUTATION,
            Map.of("id", subscriptionId)
        );
        failOnGraphQlErrors(response, "Shopify webhook subscription deletion failed.");
        Map<String, Object> data = requireMap(response.get("data"), "Shopify webhook subscription deletion returned no data.");
        Map<String, Object> payload = requireMap(
            data.get("webhookSubscriptionDelete"),
            "Shopify webhook subscription deletion returned no payload."
        );
        failOnUserErrors(payload.get("userErrors"), "Shopify webhook subscription deletion failed.");
    }

    private String webhookUri() {
        String baseUrl = optionalText(properties.publicBaseUrl());
        if (baseUrl == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge public base URL is not configured.");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return normalizedBaseUrl + "/api/webhooks/shopify";
    }

    private void failOnGraphQlErrors(Map<String, Object> response, String messagePrefix) {
        Object errorsValue = response.get("errors");
        if (!(errorsValue instanceof List<?> errors) || errors.isEmpty()) {
            return;
        }
        String message = errors.stream()
            .map(this::graphQlErrorMessage)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(messagePrefix);
        throw new ResponseStatusException(BAD_GATEWAY, messagePrefix + " " + message);
    }

    private void failOnUserErrors(Object value, String messagePrefix) {
        if (!(value instanceof List<?> errors) || errors.isEmpty()) {
            return;
        }
        String message = errors.stream()
            .map(this::userErrorMessage)
            .filter(candidate -> candidate != null && !candidate.isBlank())
            .findFirst()
            .orElse(messagePrefix);
        throw new ResponseStatusException(BAD_GATEWAY, messagePrefix + " " + message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMapFromListItem(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Shopify webhook subscription payload is invalid.");
    }

    private List<?> requireList(Object value, String message) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    private String graphQlErrorMessage(Object value) {
        if (value instanceof Map<?, ?> map) {
            return optionalText(map.get("message"));
        }
        return optionalText(value);
    }

    private String userErrorMessage(Object value) {
        if (value instanceof Map<?, ?> map) {
            return optionalText(map.get("message"));
        }
        return optionalText(value);
    }

    private String requiredText(Object value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_GATEWAY, message);
        }
        return normalized;
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean same(String left, String right) {
        String normalizedLeft = optionalText(left);
        String normalizedRight = optionalText(right);
        if (normalizedLeft == null || normalizedRight == null) {
            return false;
        }
        return normalizedLeft.toLowerCase(Locale.ROOT).equals(normalizedRight.toLowerCase(Locale.ROOT));
    }

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private record DesiredWebhookSubscription(
        String topic,
        String name
    ) {
    }

    private record ExistingWebhookSubscription(
        String id,
        String topic,
        String uri,
        String name
    ) {
    }
}
