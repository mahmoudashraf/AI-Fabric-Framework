package com.ai.fabric.platform.backend.deployment.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.*;
import static org.assertj.core.api.Assertions.*;

class DeploymentOperationsClientTest {
    private HttpServer server;
    private final DeploymentOperationsProperties properties = new DeploymentOperationsProperties();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentOperationsClient client = new DeploymentOperationsClient(properties, mapper);
    private final AssessmentRequest request = new AssessmentRequest("1", "command", "customer", "tenant", "deployment",
        "target", "command", "version", "a".repeat(64), "APPLY", "ENFORCE", "b".repeat(64),
        new ResourceDemand(1000,1024,1024,1,1,0,1,1), Instant.now());

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
        properties.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAllowInsecureHttp(true); properties.setApiKey("a-private-test-key");
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void authenticationAndExactContractReachTheServiceOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/internal/v1/deployment-operations/assess", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer a-private-test-key");
            var body = mapper.readTree(exchange.getRequestBody());
            assertThat(body.path("requestedAt").isTextual()).isTrue();
            var parsed = mapper.treeToValue(body, AssessmentRequest.class);
            assertThat(parsed.commandId()).isEqualTo("command");
            byte[] response = mapper.writeValueAsBytes(new AssessmentResponse("1", "command", request.requestFingerprint(),
                "assessment", Decision.DENY, "CAPACITY_EXCEEDED", "policy", Instant.now(), Instant.now().plusSeconds(10)));
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        assertThat(client.assess(request).decision()).isEqualTo(Decision.DENY);
        assertThat(calls).hasValue(1);
    }

    @Test void unavailableHttpIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/internal/v1/deployment-operations/assess", exchange -> {
            calls.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        assertThatThrownBy(() -> client.assess(request)).hasMessage("OPERATIONS_UNAVAILABLE");
        assertThat(calls).hasValue(1);
    }

    @Test void responseBodyIsBounded() {
        server.createContext("/internal/v1/deployment-operations/assess", exchange -> {
            byte[] bytes = new byte[70_000]; exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        assertThatThrownBy(() -> client.assess(request)).hasMessage("OPERATIONS_UNAVAILABLE");
    }

    @Test void deadlineIncludesTheWholeResponseNotOnlyHeaders() {
        properties.setTimeout(Duration.ofMillis(150));
        server.createContext("/internal/v1/deployment-operations/assess", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            try { Thread.sleep(1500); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        long start = System.nanoTime();
        assertThatThrownBy(() -> client.assess(request)).hasMessage("OPERATIONS_UNAVAILABLE");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test void redirectsDoNotReceiveTheCredential() {
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/redirected", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/internal/v1/deployment-operations/assess", exchange -> {
            exchange.getResponseHeaders().add("Location", "/redirected"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        assertThatThrownBy(() -> client.assess(request)).hasMessage("OPERATIONS_UNAVAILABLE");
        assertThat(redirected).hasValue(0);
    }

    @Test void insecureOrCredentialBearingEndpointsNeedNoNetworkCall() {
        properties.setAllowInsecureHttp(false); assertThat(client.configured()).isFalse();
        properties.setServiceUrl("https://username:password@example.invalid"); assertThat(client.configured()).isFalse();
        properties.setServiceUrl("https://example.invalid?key=secret"); assertThat(client.configured()).isFalse();
        properties.setServiceUrl("https://example.invalid"); properties.setApiKey(""); assertThat(client.configured()).isFalse();
    }
}
