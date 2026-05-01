package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyMetaobjectSupport;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyWebhookSubscriptionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyWebhookSubscriptionService.class);

    private static final String LIST_SUBSCRIPTIONS_QUERY = """
        query ShopifyBridgeWebhookSubscriptions($topics: [WebhookSubscriptionTopic!]) {
          webhookSubscriptions(first: 50, topics: $topics) {
            edges {
              node {
                id
                topic
                uri
                name
                filter
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
              filter
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

    private static final List<DesiredWebhookSubscription> BASE_DESIRED_SUBSCRIPTIONS = List.of(
        new DesiredWebhookSubscription("APP_UNINSTALLED", "loom-app-uninstalled"),
        new DesiredWebhookSubscription("APP_SCOPES_UPDATE", "loom-app-scopes-update"),
        new DesiredWebhookSubscription("APP_SUBSCRIPTIONS_UPDATE", "loom-app-subscriptions-update"),
        new DesiredWebhookSubscription("PRODUCTS_CREATE", "loom-products-create"),
        new DesiredWebhookSubscription("PRODUCTS_UPDATE", "loom-products-update"),
        new DesiredWebhookSubscription("PRODUCTS_DELETE", "loom-products-delete"),
        new DesiredWebhookSubscription("COLLECTIONS_CREATE", "loom-collections-create"),
        new DesiredWebhookSubscription("COLLECTIONS_UPDATE", "loom-collections-update"),
        new DesiredWebhookSubscription("COLLECTIONS_DELETE", "loom-collections-delete"),
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
        for (DesiredWebhookSubscription desired : desiredSubscriptions(shopDomain, accessToken, true)) {
            reconcileTopic(shopDomain, accessToken, desired, webhookUri);
        }
    }

    public ShopifyWebhookSubscriptionStatusSummary inspectContentSubscriptions(String shopDomain, String accessToken) {
        String webhookUri = webhookUri();
        List<DesiredWebhookSubscription> desiredSubscriptions = desiredSubscriptions(shopDomain, accessToken, true);
        List<ShopifyWebhookSubscriptionTopicStatusSummary> topics = desiredSubscriptions.stream()
            .map(desired -> inspectTopic(shopDomain, accessToken, desired, webhookUri))
            .toList();
        int readyCount = (int) topics.stream().filter(topic -> "READY".equalsIgnoreCase(topic.status())).count();
        int missingCount = (int) topics.stream().filter(topic -> "MISSING".equalsIgnoreCase(topic.status())).count();
        int driftedCount = (int) topics.stream().filter(topic -> "DRIFTED".equalsIgnoreCase(topic.status())).count();
        int blockedCount = (int) topics.stream().filter(topic -> "BLOCKED".equalsIgnoreCase(topic.status())).count();
        String status = driftedCount > 0 || missingCount > 0 || blockedCount > 0 ? "DEGRADED" : "READY";
        String message = "READY".equals(status)
            ? "All required Shopify webhook subscriptions are present."
            : "Some required Shopify webhook subscriptions are missing, drifted, or blocked by scope access.";
        return new ShopifyWebhookSubscriptionStatusSummary(
            normalizeShopDomain(shopDomain),
            status,
            message,
            webhookUri,
            desiredSubscriptions.size(),
            readyCount,
            missingCount,
            driftedCount,
            Instant.now(),
            topics
        );
    }

    public ShopifyWebhookSubscriptionTopicStatusSummary inspectTopicStatus(String shopDomain,
                                                                           String accessToken,
                                                                           String topic) {
        DesiredWebhookSubscription desired = BASE_DESIRED_SUBSCRIPTIONS.stream()
            .filter(current -> current.topic().equalsIgnoreCase(topic))
            .findFirst()
            .orElseGet(() -> desiredSubscriptions(shopDomain, accessToken, true).stream()
                .filter(current -> current.topic().equalsIgnoreCase(topic))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                    BAD_GATEWAY,
                    "Unsupported Shopify webhook topic: " + topic
                )));
        return inspectTopic(shopDomain, accessToken, desired, webhookUri());
    }

    public int expectedSubscriptionCount() {
        return BASE_DESIRED_SUBSCRIPTIONS.size();
    }

    public List<String> expectedTopics() {
        return BASE_DESIRED_SUBSCRIPTIONS.stream().map(DesiredWebhookSubscription::topic).toList();
    }

    public String expectedWebhookUri() {
        return webhookUri();
    }

    private void reconcileTopic(String shopDomain,
                                String accessToken,
                                DesiredWebhookSubscription desired,
                                String webhookUri) {
        List<ExistingWebhookSubscription> existing;
        try {
            existing = listSubscriptions(shopDomain, accessToken, desired.topic());
        } catch (ResponseStatusException ex) {
            if (isInaccessibleTopicFailure(ex)) {
                return;
            }
            throw ex;
        }
        boolean satisfied = existing.stream().anyMatch(subscription ->
            same(subscription.name(), desired.name())
                && same(subscription.uri(), webhookUri)
                && sameOptional(subscription.filter(), desired.filter())
        );
        if (satisfied) {
            return;
        }

        boolean hasCompatibleUnnamedSubscription = existing.stream().anyMatch(subscription ->
            same(subscription.uri(), webhookUri)
                && sameOptional(subscription.filter(), desired.filter())
        );
        if (hasCompatibleUnnamedSubscription) {
            return;
        }

        for (ExistingWebhookSubscription subscription : existing) {
            if (same(subscription.name(), desired.name())
                && (!same(subscription.uri(), webhookUri) || !sameOptional(subscription.filter(), desired.filter()))) {
                deleteSubscription(shopDomain, accessToken, subscription.id());
            }
        }

        try {
            createSubscription(shopDomain, accessToken, desired, webhookUri);
        } catch (ResponseStatusException ex) {
            if (desired.metaobjectScoped() && isRecoverableMetaobjectFilterFailure(ex)) {
                LOGGER.warn(
                    "Skipping Shopify metaobject webhook subscription for shop={} topic={} filter={} because Shopify rejected the filter: {}",
                    normalizeShopDomain(shopDomain),
                    desired.topic(),
                    desired.filter(),
                    optionalText(ex.getReason())
                );
                return;
            }
            throw ex;
        }
    }

    private ShopifyWebhookSubscriptionTopicStatusSummary inspectTopic(String shopDomain,
                                                                      String accessToken,
                                                                      DesiredWebhookSubscription desired,
                                                                      String webhookUri) {
        List<ExistingWebhookSubscription> existing;
        try {
            existing = listSubscriptions(shopDomain, accessToken, desired.topic());
        } catch (ResponseStatusException ex) {
            if (isInaccessibleTopicFailure(ex)) {
                return new ShopifyWebhookSubscriptionTopicStatusSummary(
                    desired.topic(),
                    desired.name(),
                    "BLOCKED",
                    null,
                    null,
                    null,
                    "Webhook topic is not accessible with the current Shopify scopes."
                );
            }
            throw ex;
        }
        ExistingWebhookSubscription exactMatch = existing.stream()
            .filter(subscription ->
                same(subscription.name(), desired.name())
                    && same(subscription.uri(), webhookUri)
                    && sameOptional(subscription.filter(), desired.filter())
            )
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
            .filter(subscription ->
                same(subscription.uri(), webhookUri)
                    && sameOptional(subscription.filter(), desired.filter())
            )
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
                optionalText(node.get("name")),
                optionalText(node.get("filter"))
            ))
            .toList();
    }

    private void createSubscription(String shopDomain,
                                    String accessToken,
                                    DesiredWebhookSubscription desired,
                                    String webhookUri) {
        Map<String, Object> webhookSubscription = new LinkedHashMap<>();
        webhookSubscription.put("uri", webhookUri);
        webhookSubscription.put("name", desired.name());
        if (desired.filter() != null) {
            webhookSubscription.put("filter", desired.filter());
        }
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            CREATE_SUBSCRIPTION_MUTATION,
            Map.of(
                "topic", desired.topic(),
                "webhookSubscription", webhookSubscription
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

    private boolean sameOptional(String left, String right) {
        String normalizedLeft = optionalText(left);
        String normalizedRight = optionalText(right);
        if (normalizedLeft == null && normalizedRight == null) {
            return true;
        }
        if (normalizedLeft == null || normalizedRight == null) {
            return false;
        }
        return normalizedLeft.toLowerCase(Locale.ROOT).equals(normalizedRight.toLowerCase(Locale.ROOT));
    }

    private boolean isInaccessibleTopicFailure(ResponseStatusException ex) {
        String message = optionalText(ex.getReason());
        if (message == null) {
            message = optionalText(ex.getMessage());
        }
        return message != null
            && (
                message.toLowerCase(Locale.ROOT).contains("do not have access")
                    || message.toLowerCase(Locale.ROOT).contains("access denied")
                    || message.toLowerCase(Locale.ROOT).contains("not authorized")
            );
    }

    private boolean isRecoverableMetaobjectFilterFailure(ResponseStatusException ex) {
        String message = optionalText(ex.getReason());
        if (message == null) {
            message = optionalText(ex.getMessage());
        }
        return message != null
            && (
                message.toLowerCase(Locale.ROOT).contains("specified filter is invalid")
                    || message.toLowerCase(Locale.ROOT).contains("filter is invalid")
            );
    }

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private List<DesiredWebhookSubscription> desiredSubscriptions(String shopDomain,
                                                                  String accessToken,
                                                                  boolean includeMetaobjectDefinitions) {
        List<DesiredWebhookSubscription> subscriptions = new ArrayList<>(BASE_DESIRED_SUBSCRIPTIONS);
        if (!includeMetaobjectDefinitions) {
            return List.copyOf(subscriptions);
        }
        try {
            ShopifyMetaobjectSupport.loadDefinitions(shopDomain, accessToken, shopifyAdminGraphqlClient)
                .stream()
                .map(ShopifyMetaobjectSupport.MetaobjectDefinitionSummary::type)
                .map(this::optionalText)
                .distinct()
                .forEach(type -> addMetaobjectSubscriptions(subscriptions, type));
        } catch (ResponseStatusException ex) {
            if (!isInaccessibleTopicFailure(ex)) {
                throw ex;
            }
            LOGGER.warn(
                "Skipping Shopify metaobject webhook subscription discovery for shop={} because metaobject definitions are not accessible: {}",
                normalizeShopDomain(shopDomain),
                optionalText(ex.getReason())
            );
        }
        return List.copyOf(subscriptions);
    }

    private void addMetaobjectSubscriptions(List<DesiredWebhookSubscription> subscriptions, String type) {
        String safeType = optionalText(type);
        if (safeType == null) {
            return;
        }
        String nameSuffix = safeType.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
        if (nameSuffix.isBlank()) {
            return;
        }
        String filter = "type:" + safeType;
        subscriptions.add(new DesiredWebhookSubscription("METAOBJECTS_CREATE", "loom-metaobjects-create-" + nameSuffix, filter));
        subscriptions.add(new DesiredWebhookSubscription("METAOBJECTS_UPDATE", "loom-metaobjects-update-" + nameSuffix, filter));
        subscriptions.add(new DesiredWebhookSubscription("METAOBJECTS_DELETE", "loom-metaobjects-delete-" + nameSuffix, filter));
    }

    private record DesiredWebhookSubscription(
        String topic,
        String name,
        String filter
    ) {
        DesiredWebhookSubscription(String topic, String name) {
            this(topic, name, null);
        }

        boolean metaobjectScoped() {
            return topic != null && topic.startsWith("METAOBJECTS_") && filter != null;
        }
    }

    private record ExistingWebhookSubscription(
        String id,
        String topic,
        String uri,
        String name,
        String filter
    ) {
    }
}
