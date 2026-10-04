package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.api.TraceContextDto;
import com.ai.infrastructure.connector.rest.api.VerifiedAuthContextDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.InMemoryIntegrationStateRepository;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.template.TemplateEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestActionExecutionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void executePerformsRouteLevelAuthzPreflightBeforeUpstreamCall() throws Exception {
        AtomicInteger upstreamCalls = new AtomicInteger();
        AtomicReference<JsonNode> authzPayload = new AtomicReference<>();
        AtomicReference<String> upstreamSubjectHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/authz/check", exchange -> {
            authzPayload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                {"granted":true,"reason":"ok","policyVersion":"p1"}
                """);
        });
        server.createContext("/orders/ORD-1/cancel", exchange -> {
            upstreamCalls.incrementAndGet();
            upstreamSubjectHeader.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-AUTH-SUBJECT-ID"));
            respond(exchange, 200, """
                {"status":"cancelled"}
                """);
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        RestRoutingConfig config = config(serverBaseUrl());
        RestActionExecutionService service = service(config);

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "cancel_order",
            Map.of("orderId", "ORD-1"),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("status", "cancelled");
        assertThat(upstreamCalls.get()).isEqualTo(1);
        assertThat(upstreamSubjectHeader.get()).isEqualTo("customer-123");

        JsonNode payload = authzPayload.get();
        assertThat(payload).isNotNull();
        assertThat(payload.path("contractVersion").asText()).isEqualTo("AUTH_CONTEXT_V1");
        assertThat(payload.path("resourceId").asText()).isEqualTo("order:ORD-1");
        assertThat(payload.path("operationType").asText()).isEqualTo("ORDER_CANCEL");
        assertThat(payload.path("requestedScopes").get(0).asText()).isEqualTo("orders:write");
        assertThat(payload.path("requestContext").path("actionId").asText()).isEqualTo("cancel_order");
        assertThat(payload.path("requestContext").path("orderId").asText()).isEqualTo("ORD-1");
        assertThat(payload.path("authContext").path("subjectId").asText()).isEqualTo("customer-123");
        assertThat(payload.path("userId").isMissingNode()).isTrue();
        assertThat(payload.path("subjectId").isMissingNode()).isTrue();
        assertThat(payload.path("sessionId").isMissingNode()).isTrue();
        assertThat(payload.path("compatibilityAliases").isMissingNode()).isTrue();
    }

    @Test
    void executeFailsClosedWhenRouteLevelAuthzDeniesAction() throws Exception {
        AtomicInteger upstreamCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/authz/check", exchange -> respond(exchange, 200, """
            {"granted":false,"reason":"Order not owned by caller","policyVersion":"p1"}
            """));
        server.createContext("/orders/ORD-1/cancel", exchange -> {
            upstreamCalls.incrementAndGet();
            respond(exchange, 200, "{\"status\":\"cancelled\"}");
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        RestRoutingConfig config = config(serverBaseUrl());
        RestActionExecutionService service = service(config);

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "cancel_order",
            Map.of("orderId", "ORD-1"),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("AUTHZ_DENIED");
        assertThat(result.message()).isEqualTo("Order not owned by caller");
        assertThat(upstreamCalls.get()).isZero();
    }

    @Test
    void executeRequiresVerifiedAuthContextWhenRouteLevelAuthzIsEnabled() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/authz/check", exchange -> respond(exchange, 200, """
            {"granted":true,"reason":"ok","policyVersion":"p1"}
            """));
        server.createContext("/orders/ORD-1/cancel", exchange -> respond(exchange, 200, "{\"status\":\"cancelled\"}"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        RestRoutingConfig config = config(serverBaseUrl());
        RestActionExecutionService service = service(config);

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "cancel_order",
            Map.of("orderId", "ORD-1"),
            null,
            new TraceContextDto("req-2", "chat-2", null)
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("AUTHZ_DENIED");
        assertThat(result.message()).contains("Verified auth context is required");
    }

    @Test
    void executeForwardsRuntimeActionConfigThroughTraceBodyTemplate() throws Exception {
        AtomicReference<JsonNode> upstreamPayload = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/orders/ORD-1/cancel", exchange -> {
            upstreamPayload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "{\"status\":\"cancelled\"}");
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        RestRoutingConfig config = config(serverBaseUrl());
        RestRoutingConfig.ActionRoute route = config.getActions().get("cancel_order");
        route.getAuthz().setEnabled(false);
        route.getRequest().setBody(Map.of(
            "actionId", "{{actionId}}",
            "params", "{{params}}",
            "trace", "{{trace}}"
        ));
        RestActionExecutionService service = service(config);
        Map<String, Object> actionConfig = Map.of(
            "adapterType", "mcp-tool",
            "execution", Map.of("mcp", Map.of(
                "dispatchMode", "CONNECTOR",
                "toolName", "create_cart"
            ))
        );
        TraceContextDto trace = new TraceContextDto(
            "req-connector",
            "chat-connector",
            null,
            "shopper-1",
            "session-1",
            "shop.example",
            actionConfig,
            Map.of("MCP_PROFILE_REF", "profile-value")
        );

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "cancel_order",
            Map.of("orderId", "ORD-1"),
            "idem-1",
            trace
        ));

        assertThat(result.success()).isTrue();
        JsonNode payload = upstreamPayload.get();
        assertThat(payload).isNotNull();
        assertThat(payload.path("trace").path("shopDomain").asText()).isEqualTo("shop.example");
        assertThat(payload.path("trace").path("sessionId").asText()).isEqualTo("session-1");
        assertThat(payload.path("trace").path("actionConfig").path("execution").path("mcp").path("dispatchMode").asText())
            .isEqualTo("CONNECTOR");
        assertThat(payload.path("trace").path("actionConfig").path("execution").path("mcp").path("toolName").asText())
            .isEqualTo("create_cart");
        assertThat(payload.path("trace").path("mcpSecretValues").path("MCP_PROFILE_REF").asText())
            .isEqualTo("profile-value");
    }

    @Test
    void executeMarksCanonicalEmptyListAsInsufficientGrounding() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/vehicles", exchange -> respond(exchange, 200, "{\"items\":[]}"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        RestRoutingConfig config = config(serverBaseUrl());
        RestRoutingConfig.ActionRoute route = config.getActions().get("cancel_order");
        route.setPath("/vehicles");
        route.setMethod("GET");
        route.getAuthz().setEnabled(false);
        route.getResponse().setResult("{{body.items}}");
        RestActionExecutionService service = service(config);

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "cancel_order",
            Map.of(),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("_count", 0).containsEntry("_items", List.of());
        assertThat(result.groundingSufficiency()).isEqualTo("INSUFFICIENT");
        assertThat(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(result))
            .path("groundingSufficiency").asText()).isEqualTo("INSUFFICIENT");
    }

    @Test
    void executeHonorsConfiguredGroundingSufficiencyOverride() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/vehicles", exchange -> respond(exchange, 200, "{\"items\":[]}"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        RestRoutingConfig config = config(serverBaseUrl());
        RestRoutingConfig.ActionRoute route = config.getActions().get("cancel_order");
        route.setPath("/vehicles");
        route.setMethod("GET");
        route.getAuthz().setEnabled(false);
        route.getResponse().setResult("{{body.items}}");
        route.getResponse().setGroundingSufficiency(
            RestRoutingConfig.Response.GroundingSufficiency.SUFFICIENT
        );
        RestActionExecutionService service = service(config);

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "cancel_order",
            Map.of(),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.groundingSufficiency()).isEqualTo("SUFFICIENT");
    }

    @Test
    void providerOnlyRouteDoesNotRequireGlobalUpstream() {
        RestRoutingConfig config = new RestRoutingConfig();
        RestRoutingConfig.ConnectionProfile profile = new RestRoutingConfig.ConnectionProfile();
        profile.setEnvironment("sandbox");
        profile.setBaseUrl("https://provider.example");
        profile.setAllowedHosts(List.of("provider.example"));
        profile.setCapabilityGrants(List.of("stock:read"));
        config.getConnectionProfiles().put("provider", profile);
        RestRoutingConfig.ProtectedResourceBinding binding = new RestRoutingConfig.ProtectedResourceBinding();
        binding.setConnectionProfileRef("provider");
        binding.setEnvironment("sandbox");
        binding.setResourceType("advertiser");
        binding.setResourceId("advertiser-1");
        binding.setCapabilityGrants(List.of("stock:read"));
        config.getProtectedResources().put("advertiser", binding);
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath("/stock");
        route.setMethod("GET");
        route.setConnectionProfileRef("provider");
        route.setProtectedResourceBindingRef("advertiser");
        route.setRequiredCapabilityGrants(List.of("stock:read"));
        RestRoutingConfig.ResourcePlacement placement = new RestRoutingConfig.ResourcePlacement();
        placement.setTarget(RestRoutingConfig.ResourcePlacement.Target.QUERY);
        placement.setField("advertiserId");
        route.setTrustedResourcePlacements(List.of(placement));
        route.getResponse().setSuccessHttpStatus(List.of(200));
        route.getResponse().setResult("{{body.results}}");
        config.getActions().put("provider_stock", route);
        ProviderHttpClient providerClient = mock(ProviderHttpClient.class);
        when(providerClient.execute(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ProviderHttpClient.ProviderResponse(
                200,
                "{\"results\":[{\"id\":\"stock-1\"}]}",
                Map.of()
            ));
        TemplateEngine templateEngine = new TemplateEngine();
        RestActionExecutionService service = new RestActionExecutionService(
            config,
            templateEngine,
            OBJECT_MAPPER,
            new InMemoryIdempotencyStore(config, Clock.systemUTC()),
            new RestAuthzProxyService(config),
            providerClient,
            new ProtectedResourceService(config, OBJECT_MAPPER)
        );

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "provider_stock",
            Map.of(),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("_count", 1);
    }

    @Test
    void providerRouteFiltersNonPublishableRecordsBeforeTemplatingAndGrounding() {
        RestRoutingConfig config = new RestRoutingConfig();
        RestRoutingConfig.ConnectionProfile profile = new RestRoutingConfig.ConnectionProfile();
        profile.setEnvironment("sandbox");
        profile.setBaseUrl("https://provider.example");
        profile.setAllowedHosts(List.of("provider.example"));
        profile.setCapabilityGrants(List.of("stock:read"));
        config.getConnectionProfiles().put("provider", profile);
        RestRoutingConfig.ProtectedResourceBinding binding = new RestRoutingConfig.ProtectedResourceBinding();
        binding.setConnectionProfileRef("provider");
        binding.setEnvironment("sandbox");
        binding.setResourceType("advertiser");
        binding.setResourceId("advertiser-1");
        binding.setCapabilityGrants(List.of("stock:read"));
        config.getProtectedResources().put("advertiser", binding);
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath("/stock");
        route.setMethod("GET");
        route.setConnectionProfileRef("provider");
        route.setProtectedResourceBindingRef("advertiser");
        route.setRequiredCapabilityGrants(List.of("stock:read"));
        RestRoutingConfig.ResourcePlacement placement = new RestRoutingConfig.ResourcePlacement();
        placement.setTarget(RestRoutingConfig.ResourcePlacement.Target.QUERY);
        placement.setField("advertiserId");
        route.setTrustedResourcePlacements(List.of(placement));
        route.getResponse().setSuccessHttpStatus(List.of(200));
        RestRoutingConfig.ResponseCollectionFilter filter = new RestRoutingConfig.ResponseCollectionFilter();
        filter.setCollectionJsonPointer("/results");
        filter.setCountJsonPointer("/totalResults");
        RestRoutingConfig.RecordInclusionCondition condition = new RestRoutingConfig.RecordInclusionCondition();
        condition.setJsonPointer("/adverts/retailAdverts/advertiserAdvert/status");
        condition.setAllowedValues(List.of("PUBLISHED"));
        filter.setInclusionConditions(List.of(condition));
        route.getResponse().setCollectionFilters(List.of(filter));
        RestRoutingConfig.ResponseCollectionFieldProjection projection =
            new RestRoutingConfig.ResponseCollectionFieldProjection();
        projection.setCollectionJsonPointer("/results");
        projection.setFields(Map.of(
            "stockId", "/metadata/stockId",
            "lifecycleState", "/metadata/lifecycleState"
        ));
        route.getResponse().setCollectionFieldProjections(List.of(projection));
        route.getResponse().setResult(Map.of(
            "_items", "{{body.results}}",
            "_count", "{{body.totalResults}}"
        ));
        config.getActions().put("provider_stock", route);
        ProviderHttpClient providerClient = mock(ProviderHttpClient.class);
        when(providerClient.execute(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ProviderHttpClient.ProviderResponse(
                200,
                """
                    {
                      "results": [
                        {
                          "metadata": {"stockId": "visible", "lifecycleState": "FORECOURT"},
                          "adverts": {"retailAdverts": {"advertiserAdvert": {"status": "PUBLISHED"}}}
                        },
                        {
                          "metadata": {"stockId": "hidden", "lifecycleState": "FORECOURT"},
                          "adverts": {"retailAdverts": {"advertiserAdvert": {"status": "NOT_PUBLISHED"}}}
                        }
                      ],
                      "totalResults": 2
                    }
                    """,
                Map.of()
            ));
        RestActionExecutionService service = new RestActionExecutionService(
            config,
            new TemplateEngine(),
            OBJECT_MAPPER,
            new InMemoryIdempotencyStore(config, Clock.systemUTC()),
            new RestAuthzProxyService(config),
            providerClient,
            new ProtectedResourceService(config, OBJECT_MAPPER)
        );

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "provider_stock",
            Map.of(),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("_count", 1);
        assertThat((List<?>) result.data().get("_items"))
            .singleElement()
            .satisfies(item -> {
                JsonNode projected = OBJECT_MAPPER.valueToTree(item);
                assertThat(projected.path("metadata").path("stockId").asText()).isEqualTo("visible");
                assertThat(projected.path("stockId").asText()).isEqualTo("visible");
                assertThat(projected.path("lifecycleState").asText()).isEqualTo("FORECOURT");
            });
    }

    @Test
    void sourceProjectionRouteFiltersDurableDataAndCreatesConversationTargets() {
        RestRoutingConfig config = new RestRoutingConfig();
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        RestRoutingConfig.SourceProjectionQuery projection = new RestRoutingConfig.SourceProjectionQuery();
        projection.setSourceRef("stock-source");
        projection.setOutputFields(List.of("stockId", "make", "model", "fuelType", "priceGbp"));
        projection.setDefaultLimit(5);
        projection.setMaxLimit(10);
        RestRoutingConfig.SourceProjectionFilter fuelFilter = new RestRoutingConfig.SourceProjectionFilter();
        fuelFilter.setParam("fuelType");
        fuelFilter.setFields(List.of("fuelType"));
        fuelFilter.setOperator(RestRoutingConfig.SourceProjectionFilterOperator.EQUALS_IGNORE_CASE);
        projection.setFilters(List.of(fuelFilter));
        route.setSourceProjection(projection);
        route.getResponse().setResult(Map.of(
            "_items", "{{body.results}}",
            "_count", "{{body.returnedCount}}",
            "_totalCount", "{{body.totalResults}}",
            "source", "{{body.source}}"
        ));
        RestRoutingConfig.PinnedTargetsFromCollection pinned = new RestRoutingConfig.PinnedTargetsFromCollection();
        pinned.setCollectionJsonPointer("/results");
        pinned.setIdJsonPointer("/stockId");
        pinned.setVectorSpace("dealer-vehicle");
        pinned.setContentFields(Map.of("make", "/make", "model", "/model", "priceGbp", "/priceGbp"));
        pinned.setMetadataFields(Map.of("make", "/make", "model", "/model"));
        route.getResponse().setPinnedTargetsFromCollection(pinned);
        config.getActions().put("search_stock", route);

        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        repository.startSync("stock-source", "run-1", "source-v1");
        repository.applyProjectionChanges(
            "stock-source",
            "run-1",
            List.of(new IntegrationStateRepository.SourceProjectionRecord(
                "stock-1",
                "fingerprint-1",
                "make: Northstar\nmodel: S4",
                Map.of(
                    "stockId", "stock-1",
                    "make", "Northstar",
                    "model", "S4",
                    "fuelType", "Electric",
                    "priceGbp", 20_000
                ),
                Map.of(),
                Instant.now()
            )),
            Set.of()
        );
        repository.completeSync(
            "stock-source",
            "run-1",
            null,
            new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0)
        );
        RestActionExecutionService service = new RestActionExecutionService(
            config,
            new TemplateEngine(),
            OBJECT_MAPPER,
            new InMemoryIdempotencyStore(config, Clock.systemUTC()),
            new RestAuthzProxyService(config),
            null,
            null,
            new SourceProjectionQueryService(repository)
        );

        ActionResultDto result = service.execute(new ActionExecuteRequestDto(
            "search_stock",
            Map.of("fuelType", "electric"),
            null,
            verifiedTrace()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("_count", 1).containsEntry("_totalCount", 1);
        assertThat(result.pinnedTargets()).singleElement().satisfies(target -> {
            assertThat(target.id()).isEqualTo("stock-1");
            assertThat(target.vectorSpace()).isEqualTo("dealer-vehicle");
            assertThat(target.contentText()).contains("make: Northstar", "model: S4");
        });
    }

    private RestActionExecutionService service(RestRoutingConfig config) {
        TemplateEngine templateEngine = new TemplateEngine();
        RestAuthzProxyService authzProxyService = new RestAuthzProxyService(config);
        InMemoryIdempotencyStore idempotencyStore = new InMemoryIdempotencyStore(config, Clock.systemUTC());
        return new RestActionExecutionService(config, templateEngine, OBJECT_MAPPER, idempotencyStore, authzProxyService);
    }

    private RestRoutingConfig config(String baseUrl) {
        RestRoutingConfig config = new RestRoutingConfig();
        config.getConnector().getUpstream().setBaseUrl(baseUrl);
        config.getAuthz().setEnabled(true);
        config.getAuthz().setPath("/authz/check");
        config.getAuthz().getUpstream().setBaseUrl(baseUrl);

        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath("/orders/ORD-1/cancel");
        route.setMethod("POST");
        route.getResponse().setSuccessHttpStatus(List.of(200));
        RestRoutingConfig.ActionAuthz authz = route.getAuthz();
        authz.setEnabled(true);
        authz.setResourceId("order:{{params.orderId}}");
        authz.setOperationType("ORDER_CANCEL");
        authz.setRequestedScopes(List.of("orders:write"));
        Map<String, Object> requestContext = new LinkedHashMap<>();
        requestContext.put("orderId", "{{params.orderId}}");
        authz.setRequestContext(requestContext);
        config.getActions().put("cancel_order", route);
        return config;
    }

    private TraceContextDto verifiedTrace() {
        return new TraceContextDto(
            "req-1",
            "chat-1",
            new VerifiedAuthContextDto(
                "customer-123",
                "END_USER",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "TRUSTED_BACKEND",
                "customer-session-123",
                "dep-123",
                "cus-123",
                "ten-123",
                "shop-backend",
                "2026-04-07T12:00:00Z",
                List.of("chat:query", "orders:write"),
                List.of("storefront-chat")
            )
        );
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
