package com.ai.fabric.platform.backend.mcp.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.apache.hc.client5.http.*;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.*;
import org.apache.hc.client5.http.ssl.*;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Finite public HTTPS egress with pinned validated DNS, no redirects, cookies, proxies or retries. */
@Component
public class McpSafeHttpTransport {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final ExecutorService DNS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16), r -> { var t = new Thread(r, "mcp-oauth-dns"); t.setDaemon(true); return t; },
        new ThreadPoolExecutor.AbortPolicy());
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "mcp-oauth-deadline"); t.setDaemon(true); return t;
    });
    private final ObjectMapper mapper;
    public McpSafeHttpTransport(ObjectMapper mapper) { this.mapper = mapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS); }

    public JsonNode getJson(URI uri) { return json(request("GET", uri, Map.of(), null, null)); }
    public JsonNode postJson(URI uri, JsonNode value) {
        return json(request("POST", uri, Map.of(), value.toString(), "application/json"));
    }
    public JsonNode json(Response response) {
        if (response.status() < 200 || response.status() >= 300) throw failure("MCP authorization endpoint returned HTTP " + response.status() + ".");
        try {
            JsonNode result = mapper.readTree(response.body());
            if (result == null || !result.isObject()) throw new IllegalArgumentException();
            return result;
        } catch (Exception invalid) { throw failure("MCP authorization endpoint returned invalid JSON."); }
    }

    public Response request(String method, URI supplied, Map<String, String> headers, String body, String contentType) {
        return request(method, supplied, headers, body, contentType, true, 15, 5);
    }

    /** The URL must originate solely from the administrator-managed product-service registry. */
    Response managedGateway(URI uri, Map<String, String> headers, String body, boolean execution) {
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
            || !Set.of("https", "http").contains(uri.getScheme())) throw failure("Managed MCP gateway URL is invalid.");
        return request("POST", uri, headers, body, "application/json", false, 65, 60);
    }

    private Response request(String method, URI supplied, Map<String, String> headers, String body, String contentType,
        boolean publicOnly, int deadlineSeconds, int responseSeconds) {
        URI uri = publicOnly ? validate(supplied.toString()) : supplied;
        int maxBytes = publicOnly ? MAX_BYTES : 8 * 1024 * 1024;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(deadlineSeconds);
        InetAddress[] addresses = resolve(uri.getHost(), publicOnly);
        if (org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http.wire").isDebugEnabled()
            || org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http.headers").isDebugEnabled())
            throw failure("MCP credential requests require HTTP payload logging to be disabled.");
        var request = new HttpUriRequestBase(method, uri);
        request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .setResponseTimeout(Timeout.ofSeconds(responseSeconds)).setHardCancellationEnabled(true).build());
        request.setHeader("Accept", "application/json");
        request.setHeader("Accept-Encoding", "identity");
        request.setHeader("Connection", "close");
        headers.forEach(request::setHeader);
        if (body != null) request.setEntity(new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8), ContentType.parse(contentType)));
        try (var http = client(uri.getHost(), addresses, responseSeconds)) {
            var cancellation = DEADLINES.schedule(() -> { request.cancel(); http.close(CloseMode.IMMEDIATE); },
                Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            try (var response = http.executeOpen(HttpHost.create(uri), request, null)) {
                String encoding = response.getFirstHeader("Content-Encoding") == null ? "identity" : response.getFirstHeader("Content-Encoding").getValue();
                if (!"identity".equalsIgnoreCase(encoding)) throw failure("MCP authorization responses must use identity encoding.");
                if (response.getEntity() != null && response.getEntity().getContentLength() > maxBytes) throw failure("MCP response exceeds its size limit.");
                byte[] bytes = response.getEntity() == null ? new byte[0] : response.getEntity().getContent().readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes || System.nanoTime() > deadline) throw failure("MCP response exceeded its finite size/deadline.");
                var responseHeaders = new LinkedHashMap<String, String>();
                for (Header h : response.getHeaders()) responseHeaders.put(h.getName().toLowerCase(Locale.ROOT), h.getValue());
                return new Response(response.getCode(), Map.copyOf(responseHeaders), new String(bytes, StandardCharsets.UTF_8));
            } finally {
                request.cancel(); http.close(CloseMode.IMMEDIATE); cancellation.cancel(false);
            }
        } catch (ResponseStatusException failure) { throw failure; }
        catch (Exception failure) { throw failure("MCP authorization request could not be completed."); }
    }

    public static URI validate(String raw) {
        try {
            URI uri = URI.create(raw);
            if (raw.length() > 4096 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getHost().contains("%")) throw new IllegalArgumentException();
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.contains(".") || host.endsWith(".") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.matches("[0-9.]+") || host.contains(":")) throw new IllegalArgumentException();
            return new org.apache.hc.core5.net.URIBuilder(uri).setHost(host).setPort(-1).build();
        } catch (Exception invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a public HTTPS hostname on port 443 without URL credentials or fragments."); }
    }

    private static InetAddress[] resolve(String host, boolean publicOnly) {
        Future<InetAddress[]> future;
        try { future = DNS.submit(() -> InetAddress.getAllByName(host)); }
        catch (RejectedExecutionException busy) { throw failure("MCP DNS capacity is busy; try again."); }
        try {
            InetAddress[] addresses = future.get(3, TimeUnit.SECONDS);
            if (addresses.length == 0 || addresses.length > 8 || (publicOnly && Arrays.stream(addresses).anyMatch(a -> !publicAddress(a))))
                throw failure("MCP destination resolves to a nonpublic address.");
            return addresses;
        } catch (ResponseStatusException invalid) { throw invalid; }
        catch (Exception invalid) { throw failure("MCP destination DNS could not be verified."); }
        finally { future.cancel(true); }
    }

    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] r = address.getAddress();
        if (r.length == 16) return (r[0] & 0xe0) == 0x20
            && !(r[0] == 0x20 && r[1] == 1 && ((r[2] & 0xfe) == 0 || (r[2] == 0x0d && r[3] == (byte) 0xb8)))
            && !(r[0] == 0x20 && r[1] == 2) && !(r[0] == 0x3f && (r[1] == (byte) 0xfe || r[1] == (byte) 0xff));
        int a = r[0] & 255, b = r[1] & 255, c = r[2] & 255, d = r[3] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
            && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
            && !(a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
            && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100))) && !(a == 203 && b == 0 && c == 113)
            && !(a == 168 && b == 63 && c == 129 && d == 16);
    }

    private static CloseableHttpClient client(String host, InetAddress[] addresses, int responseSeconds) {
        DnsResolver resolver = new DnsResolver() {
            public InetAddress[] resolve(String asked) throws UnknownHostException {
                if (!host.equals(asked)) throw new UnknownHostException("Unapproved MCP destination.");
                return addresses.clone();
            }
            public String resolveCanonicalHostname(String asked) throws UnknownHostException { resolve(asked); return host; }
        };
        var manager = new DirectManager().setDnsResolver(resolver).setMaxConnTotal(1).setMaxConnPerRoute(1)
            .setConnectionFactory(ManagedHttpClientConnectionFactory.builder().http1Config(Http1Config.custom()
                .setMaxHeaderCount(64).setMaxLineLength(8192).build()).build())
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(3))
                .setSocketTimeout(Timeout.ofSeconds(responseSeconds)).build())
            .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(responseSeconds)).build())
            .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build())
            .setTlsSocketStrategy(ClientTlsStrategyBuilder.create().setSslContext(SSLContexts.createDefault())
                .setTlsVersions("TLSv1.3", "TLSv1.2").setHostVerificationPolicy(HostnameVerificationPolicy.BOTH)
                .setHostnameVerifier(new DefaultHostnameVerifier()).buildClassic()).build();
        return HttpClients.createMinimal(manager);
    }
    private static final class DirectManager extends PoolingHttpClientConnectionManagerBuilder {
        @Override protected org.apache.hc.client5.http.io.HttpClientConnectionOperator createConnectionOperator(
            SchemePortResolver ports, DnsResolver dns, TlsSocketStrategy tls) {
            return new DefaultHttpClientConnectionOperator(proxy -> new Socket(Proxy.NO_PROXY), ports, dns,
                RegistryBuilder.<TlsSocketStrategy>create().register("https", tls).build());
        }
    }
    public record Response(int status, Map<String, String> headers, String body) { }
    private static ResponseStatusException failure(String message) { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message); }
}
