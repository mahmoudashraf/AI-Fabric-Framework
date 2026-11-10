package com.ai.fabric.platform.backend.deployment.setup;

import ch.qos.logback.classic.Level;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.MinimalHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupProbeHttpClientTest {
    private static final String URL = "https://api.example/health";
    private static final String SECRET = "private-credential-fixture";

    @Test
    void absentSafeCheckUrlIsNotTestedAndDoesNotResolveOrConnect() {
        var client = noNetworkClient();
        for (String url : new String[]{null, "", "   "}) {
            var result = client.probe(url, Map.of("Authorization", SECRET));
            assertThat(result.status()).isEqualTo("NOT_TESTED");
            assertThat(result.httpStatus()).isNull();
            assertSafe(result);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.example/health", "ftp://api.example/health", "file:///etc/passwd", "not a URL",
            "//api.example/health", "https:api.example", "https:///health", "https://api.example:80/health",
            "https://api.example:444/health", "https://user:private-credential-fixture@api.example/health",
            "https://api.example/health?token=private-credential-fixture", "https://api.example/health?",
            "https://api.example/health#private-credential-fixture", "https://api.example/health#",
            "https://localhost/health", "https://service/health", "https://api.localhost/health", "https://api.local/health",
            "https://api.internal/health", "https://api.home.arpa/health", "https://api.example./health", "https://123.456/health",
            "https://2130706433/health", "https://[fe80::1%25en0]/health", "https://api.example/{id}/health",
            "https://api.example/%7bid%7d/health", "https://api.example/%257bid%257d/health", "https://api.example/$TOKEN/health",
            "https://api.example/%24%7BTOKEN%7D/health", "https://api.example/:id/health", "https://api.example/%5Bid%5D/health",
            "https://api.example/%3cid%3e/health", "https://api.example/%0d%0a/health", "https://api.example/%5cprivate/health",
            "https://api.example/%GG/health", "https://api.example/white space", "https://api.example\\@127.0.0.1/health"})
    void rejectsMalformedUnsafeOrUnresolvedUrlsBeforeDnsOrHttp(String url) {
        var result = noNetworkClient().probe(url, Map.of("Authorization", SECRET));
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.httpStatus()).isNull();
        assertSafe(result);
        assertThat(result.message()).doesNotContain(url);
    }

    @Test
    void boundsUrlLength() {
        var result = noNetworkClient().probe("https://api.example/" + "x".repeat(4096), Map.of());
        assertThat(result.status()).isEqualTo("FAILED");
        assertSafe(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "0.1.2.3", "10.0.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "172.31.255.254",
            "192.168.1.1", "100.64.0.1", "100.127.255.254", "100.100.100.200", "168.63.129.16", "192.0.0.1", "192.0.0.9",
            "192.0.2.1", "192.31.196.1", "192.52.193.1", "192.88.99.1", "192.175.48.1", "198.18.0.1", "198.19.255.254",
            "198.51.100.1", "203.0.113.1", "224.0.0.1", "239.255.255.254", "240.0.0.1", "255.255.255.255",
            "::", "::1", "fe80::1", "fec0::1", "ff02::1", "fc00::1", "fdff::1", "::ffff:192.168.1.1", "::127.0.0.1",
            "64:ff9b::808:808", "64:ff9b:1::1", "100::1", "100:0:0:1::1", "2001:db8::1", "2001::7f00:1", "2001:2::1",
            "2001:20::1", "2001:30::1", "2002:808:808::1", "2620:4f:8000::1", "3ffe::1", "3fff::1", "3fff:fff::1", "5f00::1"})
    void deniesSpecialPrivateAndMetadataDnsAnswersBeforeTransport(String address) throws Exception {
        var client = new SetupProbeHttpClient(host -> new InetAddress[]{InetAddress.getByName(address)},
                pinned -> { throw new AssertionError("No transport for non-public DNS"); });
        var result = client.probe(URL, Map.of("Authorization", SECRET));
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.httpStatus()).isNull();
        assertSafe(result);
        assertThat(result.message()).doesNotContain(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://127.0.0.1/health", "https://10.0.0.1/health", "https://169.254.169.254/health",
            "https://[::1]/health", "https://[::ffff:127.0.0.1]/health", "https://[::ffff:8.8.8.8]/health",
            "https://[2001:db8::1]/health", "https://[64:ff9b::808:808]/health"})
    void validatesLiteralAddressesWithoutDnsAndRejectsMappedLiteralBypasses(String url) {
        assertThat(noNetworkClient().probe(url, Map.of()).status()).isEqualTo("FAILED");
    }

    @Test
    void rejectsMixedEmptyNullAndExcessiveDnsAnswers() throws Exception {
        var answers = new ArrayList<InetAddress[]>();
        answers.add(null);
        answers.add(new InetAddress[0]);
        answers.add(new InetAddress[]{null});
        answers.add(new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1")});
        var excessive = new InetAddress[9];
        java.util.Arrays.fill(excessive, InetAddress.getByName("8.8.8.8"));
        answers.add(excessive);
        for (InetAddress[] answer : answers) {
            var client = new SetupProbeHttpClient(host -> answer,
                    pinned -> { throw new AssertionError("Invalid DNS must not reach transport"); });
            assertThat(client.probe(URL, Map.of()).status()).isEqualTo("FAILED");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "93.184.216.34", "2001:4860:4860::8888", "2606:4700:4700::1111"})
    void permitsNativePublicIpv4AndIpv6(String address) throws Exception {
        assertThat(SetupProbeHttpClient.isPublicAddress(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void rejectsRawIpv4MappedIpv6EvenWhenItsEmbeddedIpv4IsPublic() throws Exception {
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        mapped[12] = 8;
        mapped[13] = 8;
        mapped[14] = 8;
        mapped[15] = 8;
        var address = java.net.Inet6Address.getByAddress(null, mapped, -1);
        assertThat(SetupProbeHttpClient.isPublicAddress(address)).isFalse();
    }

    @Test
    void pinsOneImmutableAddressAndPreservesTheOriginalTlsHostnameAndExactAuth() throws Exception {
        InetAddress publicIp = InetAddress.getByName("8.8.8.8");
        InetAddress[] answers = {publicIp, InetAddress.getByName("1.1.1.1")};
        AtomicInteger lookups = new AtomicInteger();
        CloseableHttpClient http = mock(CloseableHttpClient.class);
        var response = spy(new BasicClassicHttpResponse(204));
        when(http.executeOpen(any(HttpHost.class), any(ClassicHttpRequest.class), any(HttpContext.class))).thenAnswer(invocation -> {
            HttpHost target = invocation.getArgument(0);
            HttpGet request = invocation.getArgument(1);
            assertThat(target.getSchemeName()).isEqualTo("https");
            assertThat(target.getHostName()).isEqualTo("api.example");
            assertThat(target.getPort()).isEqualTo(443);
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getUri().getRawPath()).isEqualTo("/api%20health");
            assertThat(request.getEntity()).isNull();
            assertThat(request.getFirstHeader("Authorization").getValue()).isEqualTo("Bearer " + SECRET);
            assertThat(request.getFirstHeader("X-Api-Key").getValue()).isEqualTo(SECRET);
            assertThat(request.getFirstHeader("Cookie")).isNull();
            assertThat(request.getFirstHeader("Proxy-Authorization")).isNull();
            assertThat(request.getFirstHeader("Accept-Encoding").getValue()).isEqualTo("identity");
            assertThat(request.getFirstHeader("Connection").getValue()).isEqualTo("close");
            assertThat(request.getConfig().getConnectionRequestTimeout().toMilliseconds()).isEqualTo(300);
            assertThat(request.getConfig().getResponseTimeout().toMilliseconds()).isEqualTo(1500);
            assertThat(request.getConfig().isHardCancellationEnabled()).isTrue();
            return response;
        });
        var client = new SetupProbeHttpClient(host -> { lookups.incrementAndGet(); return answers; }, pinned -> {
            try {
                answers[0] = InetAddress.getByName("127.0.0.1");
                InetAddress[] returned = pinned.resolve("api.example");
                assertThat(returned).containsExactly(publicIp);
                assertThat(returned[0].getHostName()).isEqualTo("api.example");
                returned[0] = answers[0];
                assertThat(pinned.resolve("api.example")).containsExactly(publicIp);
                assertThat(pinned.resolveCanonicalHostname("api.example")).isEqualTo("api.example");
                assertThat(pinned.resolve("api.example", 443)).hasSize(1);
                assertThatThrownBy(() -> pinned.resolve("other.example")).isInstanceOf(UnknownHostException.class);
                assertThatThrownBy(() -> pinned.resolve("API.EXAMPLE")).isInstanceOf(UnknownHostException.class);
                assertThatThrownBy(() -> pinned.resolve("api.example", 80)).isInstanceOf(UnknownHostException.class);
            } catch (UnknownHostException ex) {
                throw new AssertionError(ex);
            }
            return http;
        });
        var result = client.probe("https://API.EXAMPLE:443/api%20health", Map.of("Authorization", "Bearer " + SECRET, "X-Api-Key", SECRET));
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(lookups.get()).isEqualTo(1);
        verify(http, times(1)).executeOpen(any(), any(), any());
        verify(http).close(CloseMode.IMMEDIATE);
        verify(http, never()).close();
        verify(response, never()).getEntity();
        verify(response, never()).close();
        assertSafe(result);
    }

    @ParameterizedTest
    @CsvSource({"200,VERIFIED", "201,VERIFIED", "202,VERIFIED", "204,VERIFIED", "206,VERIFIED", "299,VERIFIED",
            "301,FAILED", "302,FAILED", "303,FAILED", "304,FAILED", "307,FAILED", "308,FAILED",
            "401,FAILED", "403,FAILED", "404,FAILED", "405,FAILED", "429,FAILED", "500,FAILED", "503,FAILED"})
    void classifiesStatusesWithoutReadingBodiesReasonsOrFollowingRedirectsAndChallenges(int status, String expected) throws Exception {
        CloseableHttpClient http = mock(CloseableHttpClient.class);
        var response = spy(new BasicClassicHttpResponse(status));
        response.setReasonPhrase(SECRET);
        response.setHeader("Location", "https://127.0.0.1/" + SECRET);
        response.setHeader("WWW-Authenticate", "Basic realm=" + SECRET);
        response.setHeader("Set-Cookie", "session=" + SECRET);
        response.setEntity(new InputStreamEntity(unreadableBody(), -1, ContentType.TEXT_PLAIN));
        AtomicReference<CloseMode> closeMode = new AtomicReference<>();
        var closeable = CloseableHttpResponse.create(response, (ignored, mode) -> closeMode.set(mode));
        when(http.executeOpen(any(), any(), any())).thenReturn(closeable);
        var result = client(http).probe(URL, Map.of("Authorization", "Bearer " + SECRET));
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.httpStatus()).isEqualTo(status);
        if (status == 401) assertThat(result.message()).contains("Authorization was rejected");
        if (status == 403) assertThat(result.message()).contains("Access was denied");
        if (status >= 300 && status < 400) assertThat(result.message()).contains("redirects are not followed");
        assertThat(closeMode.get()).isEqualTo(CloseMode.IMMEDIATE);
        verify(http, times(1)).executeOpen(any(), any(), any());
        verify(http).close(CloseMode.IMMEDIATE);
        verify(response, never()).getEntity();
        verify(response, never()).getReasonPhrase();
        verify(response, never()).close();
        assertSafe(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Host", "Cookie", "Cookie2", "Set-Cookie", "Connection", "Content-Length", "Transfer-Encoding",
            "TE", "Trailer", "Upgrade", "Expect", "Accept-Encoding", "Proxy-Authorization", "Proxy-Authenticate", "Proxy-Connection"})
    void forbidsCookieProxyAndTransportHeaderOverrides(String name) {
        var result = noNetworkClient().probe(URL, Map.of(name, SECRET));
        assertThat(result.status()).isEqualTo("FAILED");
        assertSafe(result);
    }

    @Test
    void boundsAndValidatesHeaderNamesValuesCountAndTotalSizeBeforeDns() {
        List<Map<String, String>> cases = new ArrayList<>();
        cases.add(Map.of("", SECRET));
        cases.add(Map.of("bad:name", SECRET));
        cases.add(Map.of("bad name", SECRET));
        cases.add(Map.of("x".repeat(129), SECRET));
        cases.add(Map.of("Authorization", "Bearer " + SECRET + "\r\nHost: 127.0.0.1"));
        cases.add(Map.of("Authorization", "Bearer\t" + SECRET));
        cases.add(Map.of("Authorization", "Bearer\u007f" + SECRET));
        cases.add(Map.of("Authorization", "Bearer\u20ac" + SECRET));
        cases.add(Map.of("Authorization", "${MISSING_SECRET}"));
        cases.add(Map.of("Authorization", "Bearer {{TOKEN}}"));
        cases.add(Map.of("Authorization", "Bearer <TOKEN>"));
        cases.add(Map.of("Authorization", SECRET, "authorization", SECRET));
        cases.add(Map.of("Authorization", "x".repeat(4096)));
        cases.add(Map.of("X-One", "x".repeat(3000), "X-Two", "x".repeat(3000), "X-Three", "x".repeat(3000)));
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 17; i++) tooMany.put("X-Header-" + i, SECRET);
        cases.add(tooMany);
        Map<String, String> nullName = new LinkedHashMap<>();
        nullName.put(null, SECRET);
        cases.add(nullName);
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("Authorization", null);
        cases.add(nullValue);
        for (Map<String, String> headers : cases) {
            var result = noNetworkClient().probe(URL, headers);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("headers");
            assertSafe(result);
        }
    }

    @Test
    void nullHeadersDoNotInventAuthorizationOrCookies() throws Exception {
        CloseableHttpClient http = mock(CloseableHttpClient.class);
        when(http.executeOpen(any(), any(), any())).thenAnswer(invocation -> {
            HttpGet request = invocation.getArgument(1);
            assertThat(request.getFirstHeader("Authorization")).isNull();
            assertThat(request.getFirstHeader("Cookie")).isNull();
            return new BasicClassicHttpResponse(200);
        });
        assertThat(client(http).probe(URL, null).status()).isEqualTo("VERIFIED");
    }

    @Test
    void boundsResponseHeaderCountLineLengthAndAggregateWithoutReadingTheBody() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            CloseableHttpClient http = mock(CloseableHttpClient.class);
            var response = spy(new BasicClassicHttpResponse(200));
            if (variant == 0) {
                for (int i = 0; i < 33; i++) response.addHeader("X-Header-" + i, SECRET);
            } else if (variant == 1) {
                response.addHeader("X-Long", "x".repeat(4096));
            } else {
                for (int i = 0; i < 6; i++) response.addHeader("X-Header-" + i, "x".repeat(3000));
            }
            response.setEntity(new InputStreamEntity(unreadableBody(), -1, ContentType.TEXT_PLAIN));
            when(http.executeOpen(any(), any(), any())).thenReturn(response);
            var result = client(http).probe(URL, Map.of());
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.message()).contains("response headers");
            verify(response, never()).getEntity();
            verify(response, never()).close();
            verify(http).close(CloseMode.IMMEDIATE);
            assertSafe(result);
        }
    }

    @Test
    void sanitizesTransportTlsFactoryAndCleanupFailuresWithoutRetrying() throws Exception {
        for (IOException failure : List.of(new IOException(URL + " " + SECRET), new SSLHandshakeException(SECRET))) {
            CloseableHttpClient http = mock(CloseableHttpClient.class);
            when(http.executeOpen(any(), any(), any())).thenThrow(failure);
            doThrow(new IllegalStateException(SECRET)).when(http).close(CloseMode.IMMEDIATE);
            var result = client(http).probe(URL, Map.of("Authorization", SECRET));
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.httpStatus()).isNull();
            verify(http, times(1)).executeOpen(any(), any(), any());
            assertSafe(result);
        }
        var client = new SetupProbeHttpClient(host -> publicAddresses(),
                pinned -> { throw new IllegalStateException(SECRET); });
        assertSafe(client.probe(URL, Map.of()));
    }

    @Test
    void sanitizesDnsFailureDetails() {
        var client = new SetupProbeHttpClient(host -> { throw new UnknownHostException(URL + SECRET); },
                pinned -> { throw new AssertionError("No transport after DNS failure"); });
        var result = client.probe(URL, Map.of());
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("DNS lookup failed");
        assertSafe(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.apache.hc.client5.http.wire", "org.apache.hc.client5.http.headers"})
    void refusesNetworkActivityWhenCredentialPayloadLoggingIsEnabled(String name) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(name);
        var previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            var result = noNetworkClient().probe(URL, Map.of("Authorization", SECRET));
            assertThat(result.status()).isEqualTo("NOT_TESTED");
            assertThat(result.message()).contains("payload logging");
            assertSafe(result);
        } finally {
            logger.setLevel(previous);
        }
    }

    @Test
    @Timeout(3)
    void boundsDnsWaitAndCancelsTheLookupWithoutStartingHttp() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        var client = new SetupProbeHttpClient(host -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException ex) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            throw new UnknownHostException(SECRET);
        }, pinned -> { throw new AssertionError("No HTTP after DNS timeout"); });
        long start = System.nanoTime();
        var result = client.probe(URL, Map.of());
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(2000);
        assertThat(interrupted.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(result.status()).isEqualTo("FAILED");
        assertSafe(result);
    }

    @Test
    @Timeout(6)
    void absoluteDeadlineCancelsTheRequestAndClosesAStalledTransportBeforeFiveSeconds() throws Exception {
        CloseableHttpClient http = mock(CloseableHttpClient.class);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<HttpGet> request = new AtomicReference<>();
        doAnswer(invocation -> { closed.countDown(); return null; }).when(http).close(CloseMode.IMMEDIATE);
        when(http.executeOpen(any(), any(), any())).thenAnswer(invocation -> {
            request.set(invocation.getArgument(1));
            if (!closed.await(5, TimeUnit.SECONDS)) throw new AssertionError("Transport was not closed at deadline");
            throw new IOException(SECRET);
        });
        long start = System.nanoTime();
        var result = client(http).probe(URL, Map.of("Authorization", SECRET));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(4900);
        assertThat(request.get().isCancelled()).isTrue();
        assertThat(result.status()).isEqualTo("FAILED");
        verify(http, times(1)).executeOpen(any(), any(), any());
        assertSafe(result);
    }

    @Test
    void preservesCallerInterruptionAndDoesNotStartHttp() {
        var client = new SetupProbeHttpClient(host -> publicAddresses(),
                pinned -> { throw new AssertionError("No HTTP on interrupted caller"); });
        Thread.currentThread().interrupt();
        try {
            var result = client.probe(URL, Map.of());
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertSafe(result);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void productionFactoryUsesMinimalClientWithoutOptionalProtocolExecutionChains() throws Exception {
        var create = SetupProbeHttpClient.class.getDeclaredMethod("client", DnsResolver.class);
        create.setAccessible(true);
        DnsResolver dns = mock(DnsResolver.class);
        try (var http = (CloseableHttpClient) create.invoke(null, dns)) {
            assertThat(http).isInstanceOf(MinimalHttpClient.class);
        }
    }

    @Test
    void productionSocketBypassesGlobalSocksAndHttpProxySelection() throws Exception {
        AtomicInteger selected = new AtomicInteger();
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                selected.incrementAndGet();
                return List.of(new Proxy(Proxy.Type.SOCKS, new java.net.InetSocketAddress("127.0.0.1", 9)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
        });
        try (var listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(1000);
            var create = SetupProbeHttpClient.class.getDeclaredMethod("directSocket", Proxy.class);
            create.setAccessible(true);
            try (var socket = (Socket) create.invoke(null, new Object[]{null})) {
                socket.connect(new java.net.InetSocketAddress(listener.getInetAddress(), listener.getLocalPort()), 1000);
                try (var accepted = listener.accept()) {
                    assertThat(accepted.isConnected()).isTrue();
                }
            }
            assertThat(selected.get()).isZero();
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    private static SetupProbeHttpClient noNetworkClient() {
        return new SetupProbeHttpClient(host -> { throw new AssertionError("No DNS lookup expected"); },
                pinned -> { throw new AssertionError("No HTTP client expected"); });
    }

    private static SetupProbeHttpClient client(CloseableHttpClient http) {
        return new SetupProbeHttpClient(host -> publicAddresses(), pinned -> http);
    }

    private static InetAddress[] publicAddresses() throws UnknownHostException {
        return new InetAddress[]{InetAddress.getByName("8.8.8.8")};
    }

    private static InputStream unreadableBody() {
        return new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Probe must never read a response body");
            }

            @Override
            public void close() {
                throw new AssertionError("Probe must never drain or close a response entity gracefully");
            }
        };
    }

    private static void assertSafe(SetupProbeHttpClient.Result result) {
        assertThat(result.toString()).doesNotContain(SECRET, URL, "api.example", "127.0.0.1", "Bearer", "Exception");
    }
}
