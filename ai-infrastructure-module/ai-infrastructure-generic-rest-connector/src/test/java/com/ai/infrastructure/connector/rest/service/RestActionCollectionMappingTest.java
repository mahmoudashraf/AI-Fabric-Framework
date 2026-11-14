package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.api.ActionTargetRefDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.template.TemplateEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RestActionCollectionMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PRODUCTS = """
        {"products":[
          {"id":"desk-17","name":"Standing Desk","price":149.5,"sku":"HD-17","category":"desks"},
          {"id":"chair-2","name":"Office Chair","price":79,"sku":"HC-2","category":"chairs"}
        ]}
        """;

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void stopReceiver() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void anonymousNonIndexedProductResponsePinsExactRecordsWithNullVectorSpace(String vectorSpace) throws Exception {
        RestRoutingConfig config = config(PRODUCTS);
        mapping(config).setVectorSpace(vectorSpace);

        ActionResultDto result = execute(config);

        assertExactProductTargets(result, null);
        JsonNode data = MAPPER.valueToTree(result.data());
        assertThat(data).isEqualTo(MAPPER.readTree(PRODUCTS));
        assertThat(config.getDataSources()).isEmpty();
        assertThat(calls).hasValue(1);
    }

    @Test
    void indexedProductMappingKeepsConfiguredVectorSpaceAndRecordValues() throws Exception {
        RestRoutingConfig config = config(PRODUCTS);
        mapping(config).setVectorSpace("product-record.v1");

        assertExactProductTargets(execute(config), "product-record.v1");
        assertThat(calls).hasValue(1);
    }

    @Test
    void nonIndexedMappingRetainsTargetAndContentBounds() throws Exception {
        RestRoutingConfig config = config(PRODUCTS.replace("Standing Desk", "D".repeat(100)));
        mapping(config).setMaxTargets(1);
        mapping(config).setMaxContentChars(40);

        ActionResultDto result = execute(config);

        assertThat(result.success()).isTrue();
        assertThat(result.pinnedTargets()).containsExactly(new ActionTargetRefDto(
            "desk-17", null, "name: " + "D".repeat(34), Map.of("sku", "HD-17", "category", "desks")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"products\":{}}",
        "{\"products\":[\"not-a-record\"]}",
        "{\"products\":[{\"name\":\"Missing ID\"}]}",
        "{\"products\":[{\"id\":\" \",\"name\":\"Blank ID\"}]}",
        "{\"products\":[{\"id\":{},\"name\":\"Object ID\"}]}",
        "{\"products\":[{\"id\":\"valid-1\",\"name\":\"Valid\"},{\"name\":\"Missing ID\"}]}"
    })
    void missingIdentitiesAndMalformedCollectionsStillFailWithoutPartialTargets(String response) throws Exception {
        ActionResultDto result = execute(config(response));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(result.pinnedTargets()).isNullOrEmpty();
        assertThat(calls).hasValue(1);
    }

    @Test
    void nonIndexedMappingDoesNotBypassRouteAuthorization() throws Exception {
        RestRoutingConfig config = config(PRODUCTS);
        config.getActions().get("list_products").getAuthz().setEnabled(true);

        ActionResultDto result = execute(config);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("AUTHZ_DENIED");
        assertThat(calls).hasValue(0);
    }

    private void assertExactProductTargets(ActionResultDto result, String vectorSpace) {
        assertThat(result.success()).isTrue();
        assertThat(result.pinnedTargets()).containsExactly(
            new ActionTargetRefDto("desk-17", vectorSpace, "name: Standing Desk\nprice: 149.5",
                Map.of("sku", "HD-17", "category", "desks")),
            new ActionTargetRefDto("chair-2", vectorSpace, "name: Office Chair\nprice: 79",
                Map.of("sku", "HC-2", "category", "chairs"))
        );
    }

    private RestRoutingConfig config(String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/products", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        RestRoutingConfig config = new RestRoutingConfig();
        config.getConnector().getUpstream().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath("/products");
        route.setMethod("GET");
        RestRoutingConfig.PinnedTargetsFromCollection mapping = new RestRoutingConfig.PinnedTargetsFromCollection();
        mapping.setCollectionJsonPointer("/products");
        mapping.setIdJsonPointer("/id");
        Map<String, String> contentFields = new LinkedHashMap<>();
        contentFields.put("name", "/name");
        contentFields.put("price", "/price");
        mapping.setContentFields(contentFields);
        mapping.setMetadataFields(Map.of("sku", "/sku", "category", "/category"));
        route.getResponse().setPinnedTargetsFromCollection(mapping);
        config.getActions().put("list_products", route);
        return config;
    }

    private RestRoutingConfig.PinnedTargetsFromCollection mapping(RestRoutingConfig config) {
        return config.getActions().get("list_products").getResponse().getPinnedTargetsFromCollection();
    }

    private ActionResultDto execute(RestRoutingConfig config) {
        RestActionExecutionService service = new RestActionExecutionService(config, new TemplateEngine(), MAPPER,
            new InMemoryIdempotencyStore(config, Clock.systemUTC()), new RestAuthzProxyService(config));
        return service.execute(new ActionExecuteRequestDto("list_products", Map.of(), null, null));
    }
}
