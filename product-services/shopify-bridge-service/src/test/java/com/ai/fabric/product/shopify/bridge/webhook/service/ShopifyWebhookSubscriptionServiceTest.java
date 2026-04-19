package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class ShopifyWebhookSubscriptionServiceTest {

    @Test
    void reconcileCreatesMissingSubscriptions() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap()))
            .thenReturn(emptyListResponse());
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(createMutation()), anyMap()))
            .thenReturn(createResponse());

        service.reconcileContentSubscriptions("alpha.myshopify.com", "token");

        verify(client, times(9)).execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap());
        verify(client, times(9)).execute(eq("alpha.myshopify.com"), eq("token"), eq(createMutation()), anyMap());
    }

    @Test
    void reconcileReusesMatchingSubscriptionsWithoutCreatingDuplicates() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap()))
            .thenReturn(matchingListResponse());

        service.reconcileContentSubscriptions("alpha.myshopify.com", "token");

        verify(client, times(9)).execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap());
        verify(client, never()).execute(eq("alpha.myshopify.com"), eq("token"), eq(createMutation()), anyMap());
        verify(client, never()).execute(eq("alpha.myshopify.com"), eq("token"), eq(deleteMutation()), anyMap());
    }

    @Test
    void reconcileDeletesSameNamedSubscriptionWhenUriDrifts() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap()))
            .thenReturn(driftedListResponse());
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(deleteMutation()), anyMap()))
            .thenReturn(deleteResponse());
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(createMutation()), anyMap()))
            .thenReturn(createResponse());

        service.reconcileContentSubscriptions("alpha.myshopify.com", "token");

        verify(client, times(9)).execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap());
        verify(client, times(1)).execute(eq("alpha.myshopify.com"), eq("token"), eq(deleteMutation()), anyMap());
        verify(client, times(9)).execute(eq("alpha.myshopify.com"), eq("token"), eq(createMutation()), anyMap());
    }

    @Test
    void inspectMarksMissingAndDriftedTopics() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(listQuery()), anyMap()))
            .thenAnswer(invocation -> {
                Map<String, Object> variables = invocation.getArgument(3);
                String topic = String.valueOf(variables.get("topic"));
                if ("APP_UNINSTALLED".equals(topic)) {
                    return driftedListResponse();
                }
                if ("PRODUCTS_CREATE".equals(topic)) {
                    return matchingListResponse("PRODUCTS_CREATE", "loom-products-create");
                }
                return emptyListResponse();
            });

        var summary = service.inspectContentSubscriptions("alpha.myshopify.com", "token");

        assertThat(summary.status()).isEqualTo("DEGRADED");
        assertThat(summary.expectedCount()).isEqualTo(9);
        assertThat(summary.readyCount()).isEqualTo(1);
        assertThat(summary.missingCount()).isEqualTo(7);
        assertThat(summary.driftedCount()).isEqualTo(1);
        assertThat(summary.topics()).anyMatch(topic -> "APP_UNINSTALLED".equals(topic.topic()) && "DRIFTED".equals(topic.status()));
        assertThat(summary.topics()).anyMatch(topic -> "PRODUCTS_CREATE".equals(topic.topic()) && "READY".equals(topic.status()));
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Loom Companion",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-api-secret",
            "https://platform.example.com",
            "platform-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private Map<String, Object> emptyListResponse() {
        return Map.of(
            "data", Map.of(
                "webhookSubscriptions", Map.of(
                    "edges", List.of()
                )
            )
        );
    }

    private Map<String, Object> matchingListResponse() {
        return matchingListResponse("APP_UNINSTALLED", "loom-app-uninstalled");
    }

    private Map<String, Object> matchingListResponse(String topic, String name) {
        return Map.of(
            "data", Map.of(
                "webhookSubscriptions", Map.of(
                    "edges", List.of(
                        Map.of(
                            "node", Map.of(
                                "id", "gid://shopify/WebhookSubscription/1",
                                "topic", topic,
                                "uri", "https://bridge.example.com/api/webhooks/shopify",
                                "name", name
                            )
                        )
                    )
                )
            )
        );
    }

    private Map<String, Object> driftedListResponse() {
        return Map.of(
            "data", Map.of(
                "webhookSubscriptions", Map.of(
                    "edges", List.of(
                        Map.of(
                            "node", Map.of(
                                "id", "gid://shopify/WebhookSubscription/2",
                                "topic", "APP_UNINSTALLED",
                                "uri", "https://old.example.com/api/webhooks/shopify",
                                "name", "loom-app-uninstalled"
                            )
                        )
                    )
                )
            )
        );
    }

    private Map<String, Object> createResponse() {
        return Map.of(
            "data", Map.of(
                "webhookSubscriptionCreate", Map.of(
                    "webhookSubscription", Map.of(
                        "id", "gid://shopify/WebhookSubscription/99",
                        "topic", "APP_UNINSTALLED",
                        "uri", "https://bridge.example.com/api/webhooks/shopify",
                        "name", "loom-app-uninstalled"
                    ),
                    "userErrors", List.of()
                )
            )
        );
    }

    private Map<String, Object> deleteResponse() {
        return Map.of(
            "data", Map.of(
                "webhookSubscriptionDelete", Map.of(
                    "deletedWebhookSubscriptionId", "gid://shopify/WebhookSubscription/2",
                    "userErrors", List.of()
                )
            )
        );
    }

    private String listQuery() {
        return """
        query ShopifyBridgeWebhookSubscriptions($topic: WebhookSubscriptionTopic!) {
          webhookSubscriptions(first: 50, topics: $topic) {
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
    }

    private String createMutation() {
        return """
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
    }

    private String deleteMutation() {
        return """
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
    }
}
