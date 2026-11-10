package com.ai.fabric.platform.backend.deployment.setup;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One authenticated GET to an explicitly configured safe check URL, never an action executor.
 * The caller owns authorization and safe-path selection. This client enforces the public-egress,
 * credential-transport and resource boundaries; a successful check is not permission for other actions.
 */
@Component
public class SetupProbeHttpClient {
    private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(4);
    private static final int MAX_DNS_ADDRESSES = 8;
    private static final int MAX_REQUEST_HEADERS = 16;
    private static final int MAX_RESPONSE_HEADERS = 32;
    private static final int MAX_HEADER_LINE = 4096;
    private static final int MAX_REQUEST_HEADER_BYTES = 8192;
    private static final int MAX_RESPONSE_HEADER_BYTES = 16384;
    private static final Set<String> RESERVED_HEADERS = Set.of("host", "connection", "content-length",
            "transfer-encoding", "te", "trailer", "upgrade", "expect", "accept-encoding", "cookie",
            "cookie2", "set-cookie", "proxy-authorization", "proxy-authenticate", "proxy-connection");
    private static final ThreadPoolExecutor DNS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "setup-probe-dns");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "setup-probe-deadline");
        thread.setDaemon(true);
        return thread;
    });
    static {
        DNS.allowCoreThreadTimeOut(true);
        DEADLINES.setRemoveOnCancelPolicy(true);
    }

    private final AddressLookup lookup;
    private final ClientFactory clients;

    public SetupProbeHttpClient() {
        this(InetAddress::getAllByName, SetupProbeHttpClient::client);
    }

    SetupProbeHttpClient(AddressLookup lookup, ClientFactory clients) {
        this.lookup = Objects.requireNonNull(lookup);
        this.clients = Objects.requireNonNull(clients);
    }

    public record Result(String status, String message, Integer httpStatus) { }

    public Result probe(String url, Map<String, String> headers) {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        if (url == null || (url.length() <= 4096 && url.isBlank())) {
            return new Result("NOT_TESTED", "No explicit safe HTTPS check URL is configured.", null);
        }
        CloseableHttpClient http = null;
        ClassicHttpResponse response = null;
        HttpGet request = null;
        ScheduledFuture<?> cancellation = null;
        try {
            URI uri = validateUri(url);
            Map<String, String> checkedHeaders = validateHeaders(headers);
            // Apache wire/header logging could disclose credentials even though we never log them.
            if (LoggerFactory.getLogger("org.apache.hc.client5.http.wire").isDebugEnabled()
                    || LoggerFactory.getLogger("org.apache.hc.client5.http.headers").isDebugEnabled()) {
                return new Result("NOT_TESTED", "Connection check is unavailable while HTTP payload logging is enabled.", null);
            }
            String host = unbracket(uri.getHost());
            PinnedDnsResolver pinned = new PinnedDnsResolver(host, resolve(host, deadline));
            request = new HttpGet(uri);
            request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofMilliseconds(300))
                    .setResponseTimeout(Timeout.ofMilliseconds(1500)).setHardCancellationEnabled(true).build());
            request.setHeader("Accept", "application/json");
            request.setHeader("Accept-Encoding", "identity");
            request.setHeader("Connection", "close");
            checkedHeaders.forEach(request::setHeader);
            http = clients.create(pinned);
            HttpGet activeRequest = request;
            CloseableHttpClient activeClient = http;
            cancellation = DEADLINES.schedule(() -> abort(activeRequest, activeClient), remaining(deadline), TimeUnit.NANOSECONDS);
            response = http.executeOpen(HttpHost.create(uri), request, HttpClientContext.create());
            remaining(deadline);
            validateResponseHeaders(response);
            int status = response.getCode();
            if (status >= 200 && status < 300) {
                return new Result("VERIFIED", "The configured safe GET check returned a successful response.", status);
            }
            if (status == 401) {
                return new Result("FAILED", "Authorization was rejected by the check endpoint.", status);
            }
            if (status == 403) {
                return new Result("FAILED", "Access was denied by the check endpoint. Check credential permissions.", status);
            }
            if (status >= 300 && status < 400) {
                return new Result("FAILED", "The check endpoint returned a redirect; redirects are not followed.", status);
            }
            return new Result("FAILED", "The check endpoint returned an unsuccessful HTTP status.", status);
        } catch (ProbeFailure ex) {
            return new Result("FAILED", ex.getMessage(), null);
        } catch (IOException | RuntimeException ex) {
            // DNS, TLS and HTTP exceptions can contain URLs, credentials or upstream error material.
            return new Result("FAILED", "HTTPS check failed or timed out. Check reachability and TLS configuration.", null);
        } finally {
            abort(request, http);
            if (cancellation != null) {
                cancellation.cancel(false);
            }
            // Immediate close discards the connection; never close/drain a response entity gracefully.
            if (response instanceof ModalCloseable closeable) {
                try {
                    closeable.close(CloseMode.IMMEDIATE);
                } catch (RuntimeException ignored) {
                    // The transport is already discarded; cleanup errors must not expose credentials.
                }
            }
        }
    }

    private InetAddress resolve(String host, long deadline) {
        try {
            InetAddress[] addresses;
            if (InetAddressUtils.isIPv4Address(host) || InetAddressUtils.isIPv6Address(host)) {
                addresses = new InetAddress[]{InetAddress.getByName(host)};
                if (InetAddressUtils.isIPv6Address(host) && addresses[0].getAddress().length != 16) {
                    throw new ProbeFailure("Check destination must resolve only to public routable addresses.");
                }
            } else {
                var resolution = DNS.submit(() -> lookup.resolve(host));
                try {
                    addresses = resolution.get(Math.min(remaining(deadline), TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
                } finally {
                    resolution.cancel(true);
                }
            }
            if (addresses == null || addresses.length == 0 || addresses.length > MAX_DNS_ADDRESSES) {
                throw new ProbeFailure("Check DNS must resolve to between one and eight public addresses.");
            }
            for (InetAddress address : addresses) {
                if (address == null || !isPublicAddress(address)) {
                    throw new ProbeFailure("Check destination must resolve only to public routable addresses.");
                }
            }
            // Validate the entire answer, then pin one address: no re-resolution or address retries.
            return InetAddress.getByAddress(host, addresses[0].getAddress());
        } catch (UnknownHostException | ExecutionException | TimeoutException | RejectedExecutionException ex) {
            throw new ProbeFailure("Check DNS lookup failed, timed out or is unavailable.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProbeFailure("Connection check was interrupted.");
        }
    }

    private static URI validateUri(String url) {
        try {
            if (url.length() > 4096) {
                throw new URISyntaxException("", "URL length");
            }
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque() || uri.getHost() == null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || uri.getRawQuery() != null) {
                throw new URISyntaxException("", "HTTPS 443 only");
            }
            String host = unbracket(uri.getHost()).toLowerCase(Locale.ROOT);
            boolean literal = InetAddressUtils.isIPv4Address(host) || InetAddressUtils.isIPv6Address(host);
            if (host.length() > 253 || host.contains("%") || (!literal && (!host.contains(".") || host.endsWith(".")
                    || !host.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")
                    || host.matches("[0-9.]+") || host.endsWith(".localhost") || host.endsWith(".local")
                    || host.endsWith(".internal") || host.endsWith(".home.arpa")))) {
                throw new URISyntaxException("", "Public hostname required");
            }
            String path = uri.getPath();
            if (path != null && (hasPlaceholder(path) || path.indexOf('%') >= 0 || path.indexOf('\\') >= 0
                    || path.indexOf('[') >= 0 || path.indexOf(']') >= 0 || path.contains("/:")
                    || path.chars().anyMatch(Character::isISOControl))) {
                throw new URISyntaxException("", "Unresolved or ambiguous path");
            }
            return new URIBuilder(uri).setHost(host).setPort(443).build();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new ProbeFailure("Use a public HTTPS check URL on port 443 without credentials, query, fragment or placeholders.");
        }
    }

    private static Map<String, String> validateHeaders(Map<String, String> headers) {
        if (headers == null) {
            return Map.of();
        }
        if (headers.size() > MAX_REQUEST_HEADERS) {
            throw invalidHeaders();
        }
        int bytes = 0;
        Set<String> names = new HashSet<>();
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (var entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.length() > 128 || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || value == null || value.length() > MAX_HEADER_LINE - name.length() - 2
                    || value.chars().anyMatch(character -> character < 32 || character > 126)
                    || hasPlaceholder(value) || RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                    || !names.add(name.toLowerCase(Locale.ROOT))) {
                throw invalidHeaders();
            }
            bytes += name.length() + value.length() + 4;
            if (bytes > MAX_REQUEST_HEADER_BYTES) {
                throw invalidHeaders();
            }
            snapshot.put(name, value);
        }
        return Map.copyOf(snapshot);
    }

    private static ProbeFailure invalidHeaders() {
        return new ProbeFailure("Check headers are invalid, unresolved or exceed the permitted size.");
    }

    private static boolean hasPlaceholder(String value) {
        return value.indexOf('{') >= 0 || value.indexOf('}') >= 0 || value.indexOf('<') >= 0
                || value.indexOf('>') >= 0 || value.indexOf('$') >= 0;
    }

    private static void validateResponseHeaders(ClassicHttpResponse response) {
        Header[] headers = response.getHeaders();
        if (headers.length > MAX_RESPONSE_HEADERS) {
            throw new ProbeFailure("Check response headers exceed the permitted size.");
        }
        int bytes = 0;
        for (Header header : headers) {
            int length = header.getName().length() + (header.getValue() == null ? 0 : header.getValue().length()) + 4;
            bytes += length;
            if (length > MAX_HEADER_LINE || bytes > MAX_RESPONSE_HEADER_BYTES) {
                throw new ProbeFailure("Check response headers exceed the permitted size.");
            }
        }
    }

    /** Conservative public egress: exclude IANA special-purpose space and cloud metadata infrastructure. */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 16) {
            // Only native global unicast; reject mapped/compatible, NAT64, local and special ranges.
            return (raw[0] & 0xe0) == 0x20
                    && !(raw[0] == 0x20 && raw[1] == 0x01 && (raw[2] & 0xfe) == 0)
                    && !(raw[0] == 0x20 && raw[1] == 0x01 && raw[2] == 0x0d && raw[3] == (byte) 0xb8)
                    && !(raw[0] == 0x20 && raw[1] == 0x02)
                    && !(raw[0] == 0x26 && raw[1] == 0x20 && raw[2] == 0 && raw[3] == 0x4f
                        && raw[4] == (byte) 0x80 && raw[5] == 0)
                    && !(raw[0] == 0x3f && raw[1] == (byte) 0xfe)
                    && !(raw[0] == 0x3f && raw[1] == (byte) 0xff && (raw[2] & 0xf0) == 0);
        }
        int a = raw[0] & 255;
        int b = raw[1] & 255;
        int c = raw[2] & 255;
        int d = raw[3] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)
                    || (b == 31 && c == 196) || (b == 52 && c == 193) || (b == 175 && c == 48)))
                && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                && !(a == 203 && b == 0 && c == 113)
                && !(a == 168 && b == 63 && c == 129 && d == 16);
    }

    private static CloseableHttpClient client(DnsResolver resolver) {
        var connections = new DirectConnectionManagerBuilder().setDnsResolver(resolver)
                .setMaxConnTotal(1).setMaxConnPerRoute(1)
                .setConnectionFactory(ManagedHttpClientConnectionFactory.builder()
                        .http1Config(Http1Config.custom().setMaxHeaderCount(MAX_RESPONSE_HEADERS)
                                .setMaxLineLength(MAX_HEADER_LINE).setMaxEmptyLineCount(4).build()).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(1500))
                        .setSocketTimeout(Timeout.ofMilliseconds(1500)).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.ofMilliseconds(1500)).build())
                .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofMilliseconds(1500)).build())
                .setTlsSocketStrategy(ClientTlsStrategyBuilder.create().setSslContext(SSLContexts.createDefault())
                        .setTlsVersions("TLSv1.3", "TLSv1.2").setHostVerificationPolicy(HostnameVerificationPolicy.BOTH)
                        .setHostnameVerifier(new DefaultHostnameVerifier()).buildClassic()).build();
        // No redirect/retry/proxy/auth negotiation/cookie/decompression execution chain.
        return HttpClients.createMinimal(connections);
    }

    private static Socket directSocket(Proxy ignored) {
        return new Socket(Proxy.NO_PROXY);
    }

    private static final class DirectConnectionManagerBuilder extends PoolingHttpClientConnectionManagerBuilder {
        @Override
        protected org.apache.hc.client5.http.io.HttpClientConnectionOperator createConnectionOperator(
                SchemePortResolver ports, DnsResolver dns, TlsSocketStrategy tls) {
            return new DefaultHttpClientConnectionOperator(SetupProbeHttpClient::directSocket, ports, dns,
                    RegistryBuilder.<TlsSocketStrategy>create().register("https", tls).build());
        }
    }

    private static void abort(HttpGet request, CloseableHttpClient http) {
        if (request != null) {
            try {
                request.cancel();
            } catch (RuntimeException ignored) {
                // Still close the dedicated transport, without logging exception material.
            }
        }
        if (http != null) {
            try {
                http.close(CloseMode.IMMEDIATE);
            } catch (RuntimeException ignored) {
                // Cleanup must never turn a sanitized result into a credential-bearing exception.
            }
        }
    }

    private static long remaining(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
            throw new ProbeFailure("Connection check timed out or was interrupted.");
        }
        return remaining;
    }

    private static String unbracket(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static final class PinnedDnsResolver implements DnsResolver {
        private final String host;
        private final InetAddress address;

        private PinnedDnsResolver(String host, InetAddress address) {
            this.host = host;
            this.address = address;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (!this.host.equals(host)) {
                throw new UnknownHostException("Unapproved check host.");
            }
            return new InetAddress[]{address};
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            resolve(host);
            return this.host;
        }

        @Override
        public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
            if (port != 443) {
                throw new UnknownHostException("Unapproved check port.");
            }
            return List.of(new InetSocketAddress(resolve(host)[0], port));
        }
    }

    @FunctionalInterface
    interface AddressLookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    @FunctionalInterface
    interface ClientFactory {
        CloseableHttpClient create(DnsResolver resolver);
    }

    private static final class ProbeFailure extends IllegalArgumentException {
        private ProbeFailure(String message) {
            super(message);
        }
    }
}
