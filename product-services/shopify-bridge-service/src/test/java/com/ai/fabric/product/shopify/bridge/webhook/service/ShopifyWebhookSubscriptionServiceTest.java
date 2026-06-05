package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void inspectContentSubscriptionsSkipsMetaobjectWebhookDefinitionsWhenDefinitionsAreInaccessible() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(2);
                if (query.contains("metaobjectDefinitions")) {
                    return Map.of(
                        "errors",
                        List.of(Map.of("message", "Access denied for metaobjectDefinitions."))
                    );
                }
                @SuppressWarnings("unchecked")
                List<String> topics = (List<String>) ((Map<String, Object>) invocation.getArgument(3)).get("topics");
                String topic = topics.getFirst();
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
            .contains(org.assertj.core.groups.Tuple.tuple("APP_SCOPES_UPDATE", "READY"));
        assertThat(summary.topics())
            .extracting(ShopifyWebhookSubscriptionTopicStatusSummary::topic)
            .doesNotContain("METAOBJECTS_CREATE", "METAOBJECTS_UPDATE", "METAOBJECTS_DELETE");
    }

    @Test
    void reconcileContentSubscriptionsCreatesMetaobjectSubscriptionsWithDefinitionTypeFilters() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(2);
                if (query.contains("metaobjectDefinitions")) {
                    return definitionResponse("lookbook", 2);
                }
                if (query.contains("webhookSubscriptionCreate")) {
                    return createResponse();
                }
                return emptyListResponse();
            });

        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        assertThatCode(() -> service.reconcileContentSubscriptions("demo.myshopify.com", "token"))
            .doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client, atLeastOnce()).execute(anyString(), anyString(), anyString(), variablesCaptor.capture());

        assertThat(variablesCaptor.getAllValues())
            .filteredOn(variables -> "METAOBJECTS_CREATE".equals(variables.get("topic")))
            .anySatisfy(variables -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> webhookSubscription = (Map<String, Object>) variables.get("webhookSubscription");
                assertThat(webhookSubscription)
                    .containsEntry("filter", "type:lookbook")
                    .containsEntry("name", "loom-metaobjects-create-lookbook");
            });
    }

    @Test
    void reconcileContentSubscriptionsDoesNotBlockUpgradeWhenShopifyRejectsMetaobjectFilter() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(2);
                if (query.contains("metaobjectDefinitions")) {
                    return definitionResponse("$app:entry", 1);
                }
                if (query.contains("webhookSubscriptionCreate")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> variables = invocation.getArgument(3);
                    if (String.valueOf(variables.get("topic")).startsWith("METAOBJECTS_")) {
                        return userErrorCreateResponse("The specified filter is invalid, please ensure you specify the field(s) you wish to filter on.");
                    }
                    return createResponse();
                }
                return emptyListResponse();
            });

        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        assertThatCode(() -> service.reconcileContentSubscriptions("demo.myshopify.com", "token"))
            .doesNotThrowAnyException();
    }

    @Test
    void reconcileContentSubscriptionsRepairsDuplicateWebhookNameDrift() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        AtomicInteger createAttempts = new AtomicInteger();
        when(client.execute(anyString(), anyString(), anyString(), anyMap()))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(2);
                @SuppressWarnings("unchecked")
                Map<String, Object> variables = invocation.getArgument(3);
                if (query.contains("metaobjectDefinitions")) {
                    return Map.of(
                        "errors",
                        List.of(Map.of("message", "Access denied for metaobjectDefinitions."))
                    );
                }
                if (query.contains("webhookSubscriptions")) {
                    @SuppressWarnings("unchecked")
                    List<String> topics = (List<String>) variables.get("topics");
                    if (topics == null) {
                        return listResponse(
                            "SHOP_UPDATE",
                            "https://old.example.com/api/webhooks/shopify",
                            "loom-app-uninstalled"
                        );
                    }
                    return emptyListResponse();
                }
                if (query.contains("webhookSubscriptionDelete")) {
                    return deleteResponse();
                }
                if (query.contains("webhookSubscriptionCreate")) {
                    if ("APP_UNINSTALLED".equals(variables.get("topic"))
                        && createAttempts.getAndIncrement() == 0) {
                        return userErrorCreateResponse("Name already exists, no duplicate allowed");
                    }
                    return createResponse();
                }
                return emptyListResponse();
            });

        ShopifyWebhookSubscriptionService service = new ShopifyWebhookSubscriptionService(client, properties());

        assertThatCode(() -> service.reconcileContentSubscriptions("demo.myshopify.com", "token"))
            .doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client, atLeastOnce()).execute(anyString(), anyString(), anyString(), variablesCaptor.capture());

        assertThat(variablesCaptor.getAllValues())
            .anySatisfy(variables -> assertThat(variables).containsEntry("id", "gid://shopify/WebhookSubscription/SHOP_UPDATE"));
        assertThat(createAttempts).hasValue(2);
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
            "",
            "",
            "",
            "",
            300
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

    private static Map<String, Object> createResponse() {
        return Map.of(
            "data",
            Map.of(
                "webhookSubscriptionCreate",
                Map.of(
                    "webhookSubscription",
                    Map.of(
                        "id", "gid://shopify/WebhookSubscription/created",
                        "topic", "PRODUCTS_UPDATE",
                        "uri", "https://bridge.example.com/api/webhooks/shopify",
                        "name", "created"
                    ),
                    "userErrors",
                    List.of()
                )
            )
        );
    }

    private static Map<String, Object> deleteResponse() {
        return Map.of(
            "data",
            Map.of(
                "webhookSubscriptionDelete",
                Map.of(
                    "deletedWebhookSubscriptionId", "gid://shopify/WebhookSubscription/SHOP_UPDATE",
                    "userErrors",
                    List.of()
                )
            )
        );
    }

    private static Map<String, Object> userErrorCreateResponse(String message) {
        return Map.of(
            "data",
            Map.of(
                "webhookSubscriptionCreate",
                Map.of(
                    "webhookSubscription",
                    Map.of(),
                    "userErrors",
                    List.of(Map.of("message", message))
                )
            )
        );
    }

    private static Map<String, Object> definitionResponse(String type, int metaobjectsCount) {
        return Map.of(
            "data",
            Map.of(
                "metaobjectDefinitions",
                Map.of(
                    "pageInfo",
                    Map.of("hasNextPage", false, "endCursor", ""),
                    "edges",
                    List.of(
                        Map.of(
                            "node",
                            Map.of(
                                "id", "gid://shopify/MetaobjectDefinition/" + type,
                                "type", type,
                                "name", type,
                                "description", "",
                                "displayNameKey", "",
                                "metaobjectsCount", metaobjectsCount,
                                "access", Map.of("admin", "MERCHANT_READ_WRITE", "storefront", "PUBLIC_READ"),
                                "fieldDefinitions", List.of()
                            )
                        )
                    )
                )
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
                                "name", name,
                                "filter", ""
                            )
                        )
                    )
                )
            )
        );
    }

}
