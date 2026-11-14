package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.template.TemplateEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RestActionPathTemplateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<URI> receivedUri = new AtomicReference<>();
    private final AtomicReference<String> receivedHeader = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopReceiver() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({"enquiries, reference", "products, productId"})
    void expandsRelativeRoutesAtActualHttpReceiver(String resource, String parameter) throws Exception {
        RestRoutingConfig config = receiverConfig("/v1/" + resource + "/{{params." + parameter + "}}");

        ActionResultDto result = service(config).execute(request(Map.of(parameter, "HBR-123")));

        assertThat(result.success()).isTrue();
        assertThat(receivedUri.get().getRawPath()).isEqualTo("/v1/" + resource + "/HBR-123");
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @MethodSource("encodedIdentifiers")
    void substitutedIdentifierStaysOneEncodedPathSegment(String id, String encoded) throws Exception {
        RestRoutingConfig config = receiverConfig("/v1/enquiries/{{params.reference}}/status");

        ActionResultDto result = service(config).execute(request(Map.of("reference", id)));

        assertThat(result.success()).isTrue();
        URI uri = receivedUri.get();
        assertThat(uri.getRawPath()).isEqualTo("/v1/enquiries/" + encoded + "/status");
        assertThat(uri.getRawQuery()).isNull();
        assertThat(uri.getRawFragment()).isNull();
        assertThat(calls).hasValue(1);
    }

    static Stream<Arguments> encodedIdentifiers() {
        return Stream.of(
            Arguments.of("part/child", "part%2Fchild"),
            Arguments.of("../admin?x=1#fragment", "..%2Fadmin%3Fx%3D1%23fragment"),
            Arguments.of("//evil.example/a", "%2F%2Fevil.example%2Fa"),
            Arguments.of("%2e%2e%2fadmin", "%252e%252e%252fadmin"),
            Arguments.of("a\\b;role=admin", "a%5Cb%3Brole%3Dadmin"),
            Arguments.of("caf\u00e9 \u6771\u4eac", "caf%C3%A9%20%E6%9D%B1%E4%BA%AC"),
            Arguments.of(" HBR-123 ", "%20HBR-123%20"),
            Arguments.of("$1+&*{{params.other}}", "%241%2B%26%2A%7B%7Bparams.other%7D%7D")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void invalidPathInputFailsVisiblyWithoutSendingAnyRequest(Map<String, Object> params) throws Exception {
        RestRoutingConfig config = receiverConfig("/v1/enquiries/{{params.reference|invented-id}}");
        config.getConnector().getHttp().getRetry().setEnabled(true);
        config.getConnector().getHttp().getRetry().setMaxAttempts(3);
        config.getActions().get("lookup").getAuthz().setEnabled(true);

        ActionResultDto result = service(config).execute(request(params));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(result.message()).contains("params.reference");
        assertThat(calls).hasValue(0);
    }

    static Stream<Arguments> invalidParameters() {
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("reference", null);
        return Stream.of(
            Arguments.of((Object) null),
            Arguments.of(Map.of()),
            Arguments.of(explicitNull),
            Arguments.of(Map.of("reference", Map.of("id", "HBR-123"))),
            Arguments.of(Map.of("reference", List.of("HBR-123"))),
            Arguments.of(Map.of("reference", new String[]{"HBR-123"})),
            Arguments.of(Map.of("reference", "")),
            Arguments.of(Map.of("reference", " ")),
            Arguments.of(Map.of("reference", "\t\r\n")),
            Arguments.of(Map.of("reference", "\u2003")),
            Arguments.of(Map.of("reference", "\u3000")),
            Arguments.of(Map.of("reference", ".")),
            Arguments.of(Map.of("reference", ".."))
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staticRelativeAndAbsoluteRoutesKeepQueryBodyAndHeadersUnchanged(boolean absolute) throws Exception {
        RestRoutingConfig config = receiverConfig("/v1/enquiries/static%2Fid?fixed=1");
        RestRoutingConfig.ActionRoute route = config.getActions().get("lookup");
        if (absolute) {
            route.setUrl(config.getConnector().getUpstream().getBaseUrl() + route.getPath());
        }
        route.setMethod("POST");
        route.setHeaders(Map.of("X-Route-Label", "static-label"));
        route.getRequest().setQuery(Map.of("q", "{{params.reference}}"));
        route.getRequest().setBody(Map.of("reference", "{{params.reference}}", "default", "{{params.missing|1}}"));

        ActionResultDto result = service(config).execute(request(Map.of("reference", "a/b?# \u00e9")));

        assertThat(result.success()).isTrue();
        assertThat(receivedUri.get().getRawPath()).isEqualTo("/v1/enquiries/static%2Fid");
        assertThat(receivedUri.get().getRawQuery()).isEqualTo("fixed=1&q=a%2Fb%3F%23+%C3%A9");
        assertThat(receivedHeader).hasValue("static-label");
        JsonNode body = MAPPER.readTree(receivedBody.get());
        assertThat(body.path("reference").asText()).isEqualTo("a/b?# \u00e9");
        assertThat(body.path("default").asInt()).isEqualTo(1);
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotTemplateAbsoluteUrlAuthority() throws Exception {
        RestRoutingConfig config = receiverConfig("/v1/enquiries/static");
        config.getActions().get("lookup").setUrl("http://{{params.host}}/v1/enquiries/static");

        assertThatIllegalArgumentException().isThrownBy(() -> service(config).execute(request(
            Map.of("host", "127.0.0.1:" + server.getAddress().getPort())
        )));
        assertThat(calls).hasValue(0);
    }

    private RestRoutingConfig receiverConfig(String path) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            receivedUri.set(exchange.getRequestURI());
            receivedHeader.set(exchange.getRequestHeaders().getFirst("X-Route-Label"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"found\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        RestRoutingConfig config = new RestRoutingConfig();
        config.getConnector().getUpstream().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath(path);
        route.setMethod("GET");
        config.getActions().put("lookup", route);
        return config;
    }

    private RestActionExecutionService service(RestRoutingConfig config) {
        return new RestActionExecutionService(config, new TemplateEngine(), MAPPER,
            new InMemoryIdempotencyStore(config, Clock.systemUTC()), new RestAuthzProxyService(config));
    }

    private ActionExecuteRequestDto request(Map<String, Object> params) {
        return new ActionExecuteRequestDto("lookup", params, null, null);
    }
}
