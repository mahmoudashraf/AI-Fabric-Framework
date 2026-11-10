package com.ai.fabric.platform.backend.assistant;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
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
import org.apache.hc.core5.net.InetAddressUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One opt-in public document GET, not a model tool, crawler, authenticated client or ref loader.
 * Apache HttpClient 5.6.5 MinimalHttpClient has no proxy/auth/cookie/redirect/retry/decompression
 * execution chain. Its connection manager receives only a validated immutable DNS snapshot.
 * TLS still uses the original hostname, normal PKIX trust, SNI and BOTH hostname verifiers.
 * Official APIs/source: https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/
 * and https://github.com/apache/httpcomponents-client/tree/rel/v5.6.5
 */
final class AuthoringDocumentPublicFetcher {
    private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final int MAX_DNS_ADDRESSES = 8;
    private static final ThreadPoolExecutor DNS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "authoring-document-dns");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "authoring-document-deadline");
        thread.setDaemon(true);
        return thread;
    });
    static {
        DNS.allowCoreThreadTimeOut(true);
        DEADLINES.setRemoveOnCancelPolicy(true);
    }
    private final AddressLookup lookup;
    private final ClientFactory clients;

    AuthoringDocumentPublicFetcher() {
        this(InetAddress::getAllByName, AuthoringDocumentPublicFetcher::client);
    }

    AuthoringDocumentPublicFetcher(AddressLookup lookup, ClientFactory clients) {
        this.lookup = Objects.requireNonNull(lookup);
        this.clients = Objects.requireNonNull(clients);
    }

    Document fetch(String url) {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        URI uri = validateUri(url);
        String host = unbracket(uri.getHost());
        // Apache payload-level logging would disclose the as-yet unredacted response body/headers.
        if (LoggerFactory.getLogger("org.apache.hc.client5.http.wire").isDebugEnabled()
                || LoggerFactory.getLogger("org.apache.hc.client5.http.headers").isDebugEnabled()) {
            throw new IllegalStateException("Public document fetch requires HTTP wire/header payload logging to be disabled.");
        }
        InetAddress[] addresses = resolve(host, deadline);
        PinnedDnsResolver pinned = new PinnedDnsResolver(host, addresses);
        HttpGet request = new HttpGet(uri);
        request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(1))
                .setResponseTimeout(Timeout.ofSeconds(3)).setHardCancellationEnabled(true).build());
        request.setHeader("Accept", "application/json, application/yaml, text/yaml, text/plain, text/markdown, application/pdf");
        request.setHeader("Accept-Encoding", "identity");
        request.setHeader("Connection", "close");
        try (CloseableHttpClient http = clients.create(pinned)) {
            var cancellation = DEADLINES.schedule(() -> {
                request.cancel();
                http.close(CloseMode.IMMEDIATE);
            }, remaining(deadline), TimeUnit.NANOSECONDS);
            ClassicHttpResponse response = null;
            try {
                response = http.executeOpen(HttpHost.create(uri), request, HttpClientContext.create());
                checkDeadline(deadline);
                if (response.getCode() != 200) {
                    throw new FetchFailure("Public document fetch requires HTTP 200; redirects, authentication challenges and other statuses are not followed.");
                }
                Header encoding = response.getFirstHeader("Content-Encoding");
                if (response.getHeaders("Content-Encoding").length > 1 || (encoding != null && !encoding.getValue().equalsIgnoreCase("identity"))) {
                    throw new FetchFailure("Compressed public documents are unsupported; the server must return identity encoding.");
                }
                if (response.getEntity() == null) {
                    throw new FetchFailure("Public document response has no content.");
                }
                if (response.getEntity().getContentLength() > AuthoringDocumentImporter.MAX_FILE_BYTES) {
                    throw new FetchFailure("Public document exceeds the 2097152-byte (2 MiB) limit.");
                }
                Header contentType = response.getFirstHeader("Content-Type");
                if (contentType == null || response.getHeaders("Content-Type").length != 1 || contentType.getValue().length() > 512) {
                    throw new FetchFailure("Public document must declare one supported Content-Type.");
                }
                String type = contentType.getValue().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (!AuthoringDocumentImporter.supportedMediaType(type)) {
                    throw new FetchFailure("Unsupported public document media type; use the exact JSON/YAML specification URL or upload text/PDF.");
                }
                byte[] bytes;
                var input = response.getEntity().getContent();
                try (var output = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer, 0, Math.min(buffer.length, AuthoringDocumentImporter.MAX_FILE_BYTES + 1 - output.size()))) != -1) {
                        checkDeadline(deadline);
                        if (output.size() + count > AuthoringDocumentImporter.MAX_FILE_BYTES) {
                            // Cancel before closing the entity: never drain an oversized or streaming response.
                            request.cancel();
                            http.close(CloseMode.IMMEDIATE);
                            throw new FetchFailure("Public document exceeds the 2097152-byte (2 MiB) limit.");
                        }
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
                checkDeadline(deadline);
                return new Document(type, bytes);
            } finally {
                request.cancel();
                http.close(CloseMode.IMMEDIATE);
                cancellation.cancel(false);
                if (response != null) {
                    response.close();
                }
            }
        } catch (FetchFailure ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // TLS, DNS, HTTP parser and transport causes may contain addresses, URLs or header values.
            throw new FetchFailure("Public document fetch failed or timed out; TLS verification is required and no redirects or alternate destinations are attempted.");
        }
    }

    private InetAddress[] resolve(String host, long deadline) {
        try {
            InetAddress[] addresses;
            if (InetAddressUtils.isIPv4Address(host) || InetAddressUtils.isIPv6Address(host)) {
                addresses = new InetAddress[]{InetAddress.getByName(host)};
            } else {
                var resolution = DNS.submit(() -> lookup.resolve(host));
                try {
                    addresses = resolution.get(Math.min(remaining(deadline), TimeUnit.SECONDS.toNanos(2)), TimeUnit.NANOSECONDS);
                } finally {
                    resolution.cancel(true);
                }
            }
            if (addresses == null || addresses.length == 0 || addresses.length > MAX_DNS_ADDRESSES) {
                throw new FetchFailure("Public document DNS must resolve to between one and eight public addresses.");
            }
            InetAddress[] snapshot = new InetAddress[addresses.length];
            for (int i = 0; i < addresses.length; i++) {
                if (addresses[i] == null || !isPublicAddress(addresses[i])) {
                    throw new FetchFailure("Public document destination denied: every resolved address must be public routable, not private, local, special-purpose or metadata infrastructure.");
                }
                snapshot[i] = InetAddress.getByAddress(host, addresses[i].getAddress());
            }
            return snapshot;
        } catch (UnknownHostException | ExecutionException | TimeoutException | RejectedExecutionException ex) {
            throw new FetchFailure("Public document DNS lookup failed, timed out or exceeded its bounded capacity.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FetchFailure("Public document DNS lookup interrupted.");
        }
    }

    private static URI validateUri(String url) {
        try {
            if (url == null || url.length() > 4096) {
                throw new URISyntaxException("", "URL length");
            }
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque() || uri.getHost() == null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                throw new URISyntaxException("", "HTTPS 443 only");
            }
            String host = unbracket(uri.getHost()).toLowerCase(Locale.ROOT);
            boolean literal = InetAddressUtils.isIPv4Address(host) || InetAddressUtils.isIPv6Address(host);
            if (host.contains("%") || (!literal && (!host.contains(".") || host.endsWith(".")
                    || !host.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")
                    || host.matches("[0-9.]+") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")))) {
                throw new URISyntaxException("", "Invalid public hostname");
            }
            return new URIBuilder(uri).setHost(host).setPort(443).build();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new FetchFailure("Public document URL must be a valid HTTPS URL on port 443 without credentials, fragments or scoped/local hostnames.");
        }
    }

    /** Conservative public egress policy, based on IANA special-purpose IPv4/IPv6 registries. */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= raw[i] == 0;
            }
            if (mapped && raw[10] == (byte) 0xff && raw[11] == (byte) 0xff) {
                raw = Arrays.copyOfRange(raw, 12, 16);
            } else {
                // Excludes ULA, link/site-local, NAT64, compatible, mapped and other non-global ranges.
                return (raw[0] & 0xe0) == 0x20
                        && !(raw[0] == 0x20 && raw[1] == 0x01 && (raw[2] & 0xfe) == 0)
                        && !(raw[0] == 0x20 && raw[1] == 0x01 && raw[2] == 0x0d && raw[3] == (byte) 0xb8)
                        && !(raw[0] == 0x20 && raw[1] == 0x02)
                        && !(raw[0] == 0x3f && raw[1] == (byte) 0xfe)
                        && !(raw[0] == 0x3f && raw[1] == (byte) 0xff && (raw[2] & 0xf0) == 0);
            }
        }
        int a = raw[0] & 255;
        int b = raw[1] & 255;
        int c = raw[2] & 255;
        int d = raw[3] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
                && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                && !(a == 203 && b == 0 && c == 113)
                && !(a == 168 && b == 63 && c == 129 && d == 16);
    }

    private static CloseableHttpClient client(DnsResolver resolver) {
        var connections = new DirectConnectionManagerBuilder().setDnsResolver(resolver)
                .setMaxConnTotal(1).setMaxConnPerRoute(1)
                .setConnectionFactory(ManagedHttpClientConnectionFactory.builder()
                        .http1Config(Http1Config.custom().setMaxHeaderCount(64).setMaxLineLength(8192).build()).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(3))
                        .setSocketTimeout(Timeout.ofSeconds(3)).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(3)).build())
                .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build())
                .setTlsSocketStrategy(ClientTlsStrategyBuilder.create().setSslContext(SSLContexts.createDefault())
                        .setTlsVersions("TLSv1.3", "TLSv1.2").setHostVerificationPolicy(HostnameVerificationPolicy.BOTH)
                        .setHostnameVerifier(new DefaultHostnameVerifier()).buildClassic()).build();
        return HttpClients.createMinimal(connections);
    }

    private static java.net.Socket directSocket(java.net.Proxy proxy) {
        // new Socket() can consult the global SOCKS ProxySelector even without useSystemProperties().
        return new java.net.Socket(java.net.Proxy.NO_PROXY);
    }

    private static final class DirectConnectionManagerBuilder extends PoolingHttpClientConnectionManagerBuilder {
        @Override
        protected org.apache.hc.client5.http.io.HttpClientConnectionOperator createConnectionOperator(
                SchemePortResolver ports, DnsResolver dns, TlsSocketStrategy tls) {
            // Pinned 5.6.5 extension hook; Apache still owns connect, TLS/SNI and all HTTP parsing.
            return new DefaultHttpClientConnectionOperator(AuthoringDocumentPublicFetcher::directSocket, ports, dns,
                    RegistryBuilder.<TlsSocketStrategy>create().register("https", tls).build());
        }
    }

    private static String unbracket(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static long remaining(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
            throw new FetchFailure("Public document fetch deadline exceeded or interrupted.");
        }
        return remaining;
    }

    private static void checkDeadline(long deadline) {
        remaining(deadline);
    }

    static final class PinnedDnsResolver implements DnsResolver {
        private final String host;
        private final InetAddress[] addresses;

        PinnedDnsResolver(String host, InetAddress[] addresses) {
            if (addresses == null || addresses.length == 0 || addresses.length > MAX_DNS_ADDRESSES
                    || Arrays.stream(addresses).anyMatch(address -> address == null || !isPublicAddress(address))) {
                throw new FetchFailure("Pinned document DNS requires a prevalidated public address set.");
            }
            this.host = host;
            this.addresses = addresses.clone();
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (!this.host.equals(host)) {
                throw new UnknownHostException("Unapproved document host.");
            }
            return addresses.clone();
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            resolve(host);
            return this.host; // Never reverse-resolve or substitute the TLS hostname.
        }

        @Override
        public List<java.net.InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
            if (port != 443) {
                throw new UnknownHostException("Unapproved document port.");
            }
            return Arrays.stream(resolve(host)).map(address -> new java.net.InetSocketAddress(address, port)).toList();
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

    record Document(String mediaType, byte[] bytes) { }

    private static final class FetchFailure extends IllegalArgumentException {
        private FetchFailure(String message) {
            super(message);
        }
    }
}
