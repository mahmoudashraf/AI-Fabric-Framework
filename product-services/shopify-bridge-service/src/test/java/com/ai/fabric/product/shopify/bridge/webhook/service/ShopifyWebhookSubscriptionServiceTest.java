package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyWebhookSubscriptionServiceTest {

    @Test
    void inspectTopicStatusReturnsReadyForSingleTopicProbe() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenReturn(listResponse(
                "APP_SCOPES_UPDATE",
                "https://bridge.example.com/api/webhooks/shopify",
                "loom-app-scopes-update"
            ));

        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        ShopifyWebhookSubscriptionTopicStatusSummary topic = service.inspectTopicStatus(
            "demo.myshopify.com",
            "token",
            "APP_SCOPES_UPDATE"
        );

        assertThat(topic.topic()).isEqualTo("APP_SCOPES_UPDATE");
        assertThat(topic.status()).isEqualTo("READY");
    }

    @Test
    void inspectContentSubscriptionsMarksInaccessibleTopicsAsBlocked() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<String> topics = (List<String>) ((Map<String, Object>) invocation.getArgument(3)).get("topics");
                String topic = topics.getFirst();
                if (topic.startsWith("METAOBJECTS_")) {
                    return Map.of(
                        "errors",
                        List.of(Map.of("message", "topics argument cannot contain any topics to which you do not have access."))
                    );
                }
                if ("APP_SCOPES_UPDATE".equals(topic)) {
                    return listResponse(topic, "https://bridge.example.com/api/webhooks/shopify", "loom-app-scopes-update");
                }
                return emptyListResponse();
            });

        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        ShopifyWebhookSubscriptionStatusSummary summary = service.inspectContentSubscriptions("demo.myshopify.com", "token");

        assertThat(summary.status()).isEqualTo("DEGRADED");
        assertThat(summary.topics())
            .extracting(ShopifyWebhookSubscriptionTopicStatusSummary::topic, ShopifyWebhookSubscriptionTopicStatusSummary::status)
            .contains(
                org.assertj.core.groups.Tuple.tuple("APP_SCOPES_UPDATE", "READY"),
                org.assertj.core.groups.Tuple.tuple("METAOBJECTS_CREATE", "BLOCKED"),
                org.assertj.core.groups.Tuple.tuple("METAOBJECTS_UPDATE", "BLOCKED"),
                org.assertj.core.groups.Tuple.tuple("METAOBJECTS_DELETE", "BLOCKED")
            );
    }

    @Test
    void reconcileContentSubscriptionsSkipsInaccessibleTopics() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<String> topics = (List<String>) ((Map<String, Object>) invocation.getArgument(3)).get("topics");
                String topic = topics.getFirst();
                if (topic.startsWith("METAOBJECTS_")) {
                    return Map.of(
                        "errors",
                        List.of(Map.of("message", "topics argument cannot contain any topics to which you do not have access."))
                    );
                }
                return listResponse(
                    topic,
                    "https://bridge.example.com/api/webhooks/shopify",
                    expectedName(topic)
                );
            });

        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        assertThatCode(() -> service.reconcileContentSubscriptions("demo.myshopify.com", "token"))
            .doesNotThrowAnyException();
    }

    private static ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Shopify Bridge Service",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "test",
            "https://bridge.example.com",
            "2026-04",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            ""
        );
    }

    private static Map<String, Object> emptyListResponse() {
        return Map.of(
            "data",
            Map.of(
                "webhookSubscriptions",
                Map.of("edges", List.of())
            )
        );
    }

    private static Map<String, Object> listResponse(String topic, String uri, String name) {
        return Map.of(
            "data",
            Map.of(
                "webhookSubscriptions",
                Map.of(
                    "edges",
                    List.of(
                        Map.of(
                            "node",
                            Map.of(
                                "id", "gid://shopify/WebhookSubscription/" + topic,
                                "topic", topic,
                                "uri", uri,
                                "name", name
                            )
                        )
                    )
                )
            )
        );
    }

    private static String expectedName(String topic) {
        return switch (topic) {
            case "APP_UNINSTALLED" -> "loom-app-uninstalled";
            case "APP_SCOPES_UPDATE" -> "loom-app-scopes-update";
            case "APP_SUBSCRIPTIONS_UPDATE" -> "loom-app-subscriptions-update";
            case "PRODUCTS_CREATE" -> "loom-products-create";
            case "PRODUCTS_UPDATE" -> "loom-products-update";
            case "PRODUCTS_DELETE" -> "loom-products-delete";
            case "COLLECTIONS_CREATE" -> "loom-collections-create";
            case "COLLECTIONS_UPDATE" -> "loom-collections-update";
            case "COLLECTIONS_DELETE" -> "loom-collections-delete";
            case "METAOBJECTS_CREATE" -> "loom-metaobjects-create";
            case "METAOBJECTS_UPDATE" -> "loom-metaobjects-update";
            case "METAOBJECTS_DELETE" -> "loom-metaobjects-delete";
            case "SHOP_UPDATE" -> "loom-shop-update";
            default -> topic == null ? null : topic.toLowerCase();
        };
    }
}
