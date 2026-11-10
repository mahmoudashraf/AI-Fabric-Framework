import ai.fabric.http.AIHttpClientProperties;
import ai.fabric.http.DefaultAIHttpClientFactory;
import ai.fabric.http.DefaultOutboundHttpExecutor;
import ai.fabric.http.OutboundHttpExecutor;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.connector.AIActionCatalogProperties;
import ai.fabric.intent.action.connector.AIActionConnectorProperties;
import ai.fabric.intent.action.connector.ActionConnectorExecutor;
import ai.fabric.intent.action.connector.ConnectorActionCatalogLoader;
import ai.fabric.intent.action.connector.ConnectorActionDefinition;
import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfigLoader;
import com.ai.infrastructure.connector.rest.service.InMemoryIdempotencyStore;
import com.ai.infrastructure.connector.rest.service.RestActionExecutionService;
import com.ai.infrastructure.connector.rest.service.RestAuthzProxyService;
import com.ai.infrastructure.connector.rest.template.TemplateEngine;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/** Binary contract probe only: no provider calls, Spring application boot, or business endpoints. */
public final class GeneratedActionLoadProbe {
    private static final String READ_ACTION = "search_records";
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public static void main(String[] args) throws Exception {
        var report = MAPPER.createObjectNode().put("providerCalls", 0).put("businessApiCalls", 0)
            .put("writeActionExecutions", 0);
        JsonNode negative = snapshot(Path.of(args[0]));
        try {
            loadActions(actions(negative));
            throw new AssertionError("The untouched first-generation catalogue unexpectedly loaded.");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("unsupported resultPresentationHint"),
                "Negative control failed for a different contract: " + expected.getMessage());
            report.put("negativeControl", "REJECTED_UNSUPPORTED_RESULT_PRESENTATION_HINT")
                .put("negativeFailure", expected.getMessage());
        }
        if (args.length == 1) {
            System.out.println("PROBE_RESULT " + report.put("status", "NEGATIVE_CONTROL_PASSED"));
            return;
        }

        JsonNode candidate = snapshot(Path.of(args[1]));
        var definitions = loadActions(actions(candidate));
        var loaded = report.putArray("loadedActions");
        for (var definition : definitions) loaded.addObject().put("name", definition.name())
            .put("accessMode", definition.accessMode().name())
            .put("resultPresentationHint", definition.resultPresentationHint().name());
        var selected = definitions.stream().filter(action -> READ_ACTION.equals(action.name())).findFirst().orElseThrow();
        require(selected.accessMode() == ActionAccessMode.READ, "Only the declared READ search_records action can execute.");
        require(selected.adapterType() == null || "connector-http".equals(selected.adapterType()),
            "This proof does not dispatch MCP or other adapters.");
        require(selected.params().stream().anyMatch(param -> "q".equals(param.name()))
            && selected.params().stream().anyMatch(param -> "limit".equals(param.name())),
            "The generated definition must expose the fictional specification's q and limit parameters.");
        JsonNode route = candidate.at("/routingConfig/actions/" + READ_ACTION);
        require(route.isObject(), "Provide the actual candidate routingConfig.actions.search_records route.");

        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var threads = Executors.newFixedThreadPool(2);
        server.setExecutor(threads);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var operator = (ObjectNode) MAPPER.readTree(Files.readString(Path.of(args[2])));
        require(operator.path("actions").isObject() && operator.path("actions").isEmpty(), "The operator fixture must contain no preconfigured routes.");
        require("http://127.0.0.1:1".equals(operator.at("/connector/upstream/base-url").asText()), "Use only the local operator fixture.");
        ((ObjectNode) operator.at("/connector/upstream")).put("base-url", base);
        ((ObjectNode) operator.path("actions")).set(READ_ACTION, route);
        RestRoutingConfig config = new RestRoutingConfigLoader(resources(operator), null, MAPPER).load("probe:routing");
        var selectedRoute = config.getActions().get(READ_ACTION);
        require("GET".equalsIgnoreCase(selectedRoute.getMethod()) && "/v1/records".equals(selectedRoute.getPath())
            && !org.springframework.util.StringUtils.hasText(selectedRoute.getUrl()), "Only the documented GET /v1/records path is allowed; no absolute or rewritten target.");
        require(!org.springframework.util.StringUtils.hasText(selectedRoute.getConnectionProfileRef()) && selectedRoute.getSourceProjection() == null
            && !selectedRoute.getAuthz().isEnabled() && !config.getAuthz().isEnabled(),
            "The fictional public READ fixture must not dispatch provider bindings, projections, or remote authorization.");
        var service = new RestActionExecutionService(config, new TemplateEngine(), MAPPER,
            new InMemoryIdempotencyStore(config, Clock.systemUTC()), new RestAuthzProxyService(config));
        String q = "fictional ledger & records";
        int limit = 2;
        JsonNode upstream = MAPPER.readTree("""
            {"records":[{"id":"fictional-record-1","title":"Fictional first record"},
                        {"id":"fictional-record-2","title":"Fictional second record"}]}
            """);
        var connectorCalls = new AtomicInteger();
        var upstreamCalls = new AtomicInteger();
        var unexpectedCalls = new AtomicInteger();
        var receivedQuery = new AtomicReference<Map<String, String>>();
        var handlerFailure = new AtomicReference<Throwable>();
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod()) && "/actions/execute".equals(path)) {
                    connectorCalls.incrementAndGet();
                    require("fictional-loopback-key".equals(exchange.getRequestHeaders().getFirst("X-AIFABRIC-API-KEY")), "Missing local fixture key.");
                    var request = MAPPER.readValue(exchange.getRequestBody(), ActionExecuteRequestDto.class);
                    require(READ_ACTION.equals(request.actionId()), "A non-READ action was blocked at the local connector boundary.");
                    respond(exchange, 200, MAPPER.valueToTree(service.execute(request)));
                } else if ("GET".equals(exchange.getRequestMethod()) && "/v1/records".equals(path)) {
                    upstreamCalls.incrementAndGet();
                    var query = query(exchange.getRequestURI());
                    require(Map.of("q", q, "limit", Integer.toString(limit)).equals(query), "Actual q/limit template mapping did not match.");
                    require(exchange.getRequestBody().readAllBytes().length == 0, "The documented READ must not acquire a request body.");
                    receivedQuery.set(query);
                    respond(exchange, 200, upstream);
                } else {
                    unexpectedCalls.incrementAndGet();
                    throw new AssertionError("Only the local connector and fictional GET route may be called.");
                }
            } catch (Throwable failure) {
                handlerFailure.compareAndSet(null, failure);
                respond(exchange, 500, MAPPER.createObjectNode().put("success", false).put("message", "Local probe assertion failed."));
            } finally { exchange.close(); }
        });
        try {
            server.start();
            var properties = new AIActionConnectorProperties();
            properties.setBaseUrl(base);
            properties.setMaxAttempts(1);
            properties.setConnectTimeout(Duration.ofSeconds(2));
            properties.setReadTimeout(Duration.ofSeconds(5));
            properties.getApiKey().setValue("fictional-loopback-key");
            var http = new DefaultOutboundHttpExecutor(new DefaultAIHttpClientFactory(new RestTemplateBuilder(), new AIHttpClientProperties()));
            OutboundHttpExecutor localOnly = request -> {
                require((base + "/actions/execute").equals(request.url()), "Non-loopback connector dispatch was blocked.");
                try {
                    require(READ_ACTION.equals(MAPPER.readTree(request.body()).path("actionId").asText()), "Write action dispatch was blocked.");
                } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                    throw new IllegalArgumentException("The canonical client emitted invalid request JSON.", invalid);
                }
                return http.execute(request);
            };
            var result = new ActionConnectorExecutor(properties, localOnly, null, Clock.systemUTC())
                .execute(selected.name(), selected.accessMode(), Map.of("q", q, "limit", limit), null);
            if (handlerFailure.get() != null) throw new AssertionError("Local fixture failed", handlerFailure.get());
            require(result.isSuccess(), "Canonical connector execution failed: " + result.getErrorCode() + ": " + result.getMessage());
            JsonNode data = MAPPER.valueToTree(result.getData().toMap());
            require(upstream.path("records").equals(data.at(args[3])), "Canonical response projection did not return the expected fictional records at " + args[3]);
            require(connectorCalls.get() == 1 && upstreamCalls.get() == 1 && unexpectedCalls.get() == 0, "Expected exactly one READ dispatch and one mock upstream request.");
            report.put("status", "LOAD_AND_LOCAL_READ_PASSED").put("connectorCalls", connectorCalls.get())
                .put("mockUpstreamCalls", upstreamCalls.get()).put("unexpectedCalls", unexpectedCalls.get())
                .put("executedAction", selected.name()).put("resultPointer", args[3]);
            report.set("receivedQuery", MAPPER.valueToTree(receivedQuery.get()));
            report.set("resultData", data);
            report.putArray("operatorOverrides").add("Only connector.upstream.base-url was changed to the ephemeral loopback mock; the actual READ route was not rewritten.");
            System.out.println("PROBE_RESULT " + report);
        } finally {
            server.stop(0);
            threads.shutdownNow();
        }
    }

    private static JsonNode snapshot(Path path) throws Exception {
        JsonNode root = MAPPER.readTree(Files.readString(path));
        return root.path("draftRaw").isTextual() ? MAPPER.readTree(root.path("draftRaw").asText()) : root;
    }

    private static JsonNode actions(JsonNode root) {
        return root.has("actionsConfig") ? root.path("actionsConfig") : root;
    }

    private static List<ConnectorActionDefinition> loadActions(JsonNode catalogue) {
        var source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setPath("probe:catalogue");
        return new ConnectorActionCatalogLoader(resources(catalogue)).loadActions(List.of(source));
    }

    private static DefaultResourceLoader resources(JsonNode document) {
        byte[] bytes = document.toString().getBytes(StandardCharsets.UTF_8);
        return new DefaultResourceLoader() {
            @Override public Resource getResource(String location) { return new ByteArrayResource(bytes, location); }
        };
    }

    private static Map<String, String> query(URI uri) {
        var result = new LinkedHashMap<String, String>();
        for (String entry : uri.getRawQuery().split("&")) {
            String[] parts = entry.split("=", 2);
            String previous = result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
            require(previous == null, "Duplicate mock query key.");
        }
        return result;
    }

    private static void respond(HttpExchange exchange, int status, JsonNode body) throws java.io.IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
