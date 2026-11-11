package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedDnsHttpClientTest {
    @TempDir static Path temporary;
    private static SSLContext serverTls;
    private static SSLContext trustedTls;
    private final List<HttpsServer> servers = new ArrayList<>();
    private final List<PinnedDnsHttpClient> clients = new ArrayList<>();
    private final McpGatewayProperties properties = new McpGatewayProperties("test", "test", "internal", "X-MCP-GATEWAY-API-KEY",
        "2025-11-25", List.of(), List.of(), false, "MCP_SECRET_", Duration.ofSeconds(1), Duration.ofSeconds(2));

    @BeforeAll
    static void localTlsFixture() throws Exception {
        Path storeFile = temporary.resolve("fixture.p12");
        var keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair",
            "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-storetype", "PKCS12",
            "-keystore", storeFile.toString(), "-storepass", "fixture-password", "-keypass", "fixture-password",
            "-dname", "CN=fixture.loomai.test", "-ext", "SAN=dns:fixture.loomai.test", "-noprompt")
            .redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(keytool.waitFor(15, TimeUnit.SECONDS)).as(output).isTrue();
        assertThat(keytool.exitValue()).as(output).isZero();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storeFile)) { store.load(input, "fixture-password".toCharArray()); }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(store, "fixture-password".toCharArray());
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        serverTls = SSLContext.getInstance("TLS"); serverTls.init(keys.getKeyManagers(), null, null);
        trustedTls = SSLContext.getInstance("TLS"); trustedTls.init(null, trust.getTrustManagers(), null);
    }

    @AfterEach
    void close() { clients.forEach(PinnedDnsHttpClient::close); servers.forEach(server -> server.stop(0)); }

    @Test
    void connectsToTheResolvedAddressWithoutAnotherLookupAndRetainsTlsHostname() throws Exception {
        AtomicInteger lookups = new AtomicInteger(); AtomicInteger received = new AtomicInteger();
        HttpsServer server = server(exchange -> {
            received.incrementAndGet(); assertThat(exchange.getRequestHeaders().getFirst("Host")).startsWith("fixture.loomai.test:");
            write(exchange, 200, "connected using validated address");
        });
        var policy = loopbackFixtureDns(lookups);
        var client = client(policy);
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri(server, "fixture.loomai.test")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("connected using validated address");
        assertThat(lookups.get()).isEqualTo(1);
        assertThat(received.get()).isEqualTo(1);
    }

    @Test
    void wrongTlsHostnameIsRejectedBeforeSendingCredentials() throws Exception {
        AtomicInteger received = new AtomicInteger();
        HttpsServer server = server(exchange -> { received.incrementAndGet(); write(exchange, 200, "unexpected"); });
        var client = client(loopbackFixtureDns(new AtomicInteger()));
        assertThatThrownBy(() -> client.send(HttpRequest.newBuilder(uri(server, "wrong.loomai.test"))
            .header("Authorization", "Bearer local-test-only").build(), HttpResponse.BodyHandlers.ofString())).isInstanceOf(java.io.IOException.class);
        assertThat(received.get()).isZero();
    }

    @Test
    void mixedPrivateAnswerNeverReachesTheReceiver() throws Exception {
        AtomicInteger received = new AtomicInteger();
        HttpsServer server = server(exchange -> { received.incrementAndGet(); write(exchange, 200, "unexpected"); });
        var policy = new PublicMcpAddressPolicy(host -> new InetAddress[]{InetAddress.getByName("1.1.1.1"), InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() -> client(policy).send(HttpRequest.newBuilder(uri(server, "customer.loomai.test"))
            .header("Authorization", "Bearer local-test-only").build(), HttpResponse.BodyHandlers.ofString())).isInstanceOf(java.io.IOException.class);
        assertThat(received.get()).isZero();
    }

    @Test
    void doesNotFollowRedirectsOrUseTheSystemProxy() throws Exception {
        AtomicInteger requests = new AtomicInteger(); AtomicInteger proxySelections = new AtomicInteger();
        HttpsServer server = server(exchange -> {
            requests.incrementAndGet(); exchange.getResponseHeaders().set("Location", "https://127.0.0.1/private");
            write(exchange, 302, "redirect denied");
        });
        ProxySelector prior = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) { proxySelections.incrementAndGet(); return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 9))); }
            @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) { }
        });
        try {
            HttpResponse<String> response = client(loopbackFixtureDns(new AtomicInteger())).send(
                HttpRequest.newBuilder(uri(server, "fixture.loomai.test")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(requests.get()).isEqualTo(1);
            assertThat(proxySelections.get()).isZero();
        } finally { ProxySelector.setDefault(prior); }
    }

    @Test
    void streamsFirstChunkBeforeResponseEndsAndCancellationClosesTheExchange() throws Exception {
        HttpsServer server = server(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: first\n\n".getBytes(StandardCharsets.UTF_8)); exchange.getResponseBody().flush();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        var client = client(loopbackFixtureDns(new AtomicInteger()));
        var response = client.sendAsync(HttpRequest.newBuilder(uri(server, "fixture.loomai.test")).build(), HttpResponse.BodyHandlers.ofPublisher()).get(2, TimeUnit.SECONDS);
        var first = new java.util.concurrent.CompletableFuture<String>();
        response.body().subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
            public void onNext(List<java.nio.ByteBuffer> buffers) {
                String value = StandardCharsets.UTF_8.decode(buffers.get(0)).toString();
                subscription.cancel(); first.complete(value);
            }
            public void onError(Throwable failure) { first.completeExceptionally(failure); }
            public void onComplete() { }
        });
        assertThat(first.get(500, TimeUnit.MILLISECONDS)).contains("data: first");
        assertThat((java.util.Set<?>) org.springframework.test.util.ReflectionTestUtils.getField(client, "active")).isEmpty();
    }

    private PublicMcpAddressPolicy loopbackFixtureDns(AtomicInteger lookups) {
        // Only this isolated TLS fixture substitutes loopback; production's resolver rejects it.
        return new PublicMcpAddressPolicy() {
            @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                if (lookups.incrementAndGet() > 1) throw new UnknownHostException("A second lookup is forbidden in this pinning fixture.");
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }
        };
    }

    private PinnedDnsHttpClient client(PublicMcpAddressPolicy policy) {
        var client = new PinnedDnsHttpClient(properties, policy, trustedTls); clients.add(client); return client;
    }

    private HttpsServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls)); server.createContext("/mcp", handler);
        server.start(); servers.add(server); return server;
    }

    private URI uri(HttpsServer server, String host) { return URI.create("https://" + host + ":" + server.getAddress().getPort() + "/mcp"); }

    private void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
}
