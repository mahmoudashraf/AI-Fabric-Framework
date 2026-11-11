package com.ai.fabric.product.mcp.gateway.client;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Validates the addresses returned to Apache's connection operator, not a separate preflight lookup. */
public class PublicMcpAddressPolicy implements DnsResolver {
    private static final ExecutorService DNS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16), runnable -> {
            Thread thread = new Thread(runnable, "mcp-public-dns");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    @FunctionalInterface
    interface Lookup { InetAddress[] resolve(String host) throws UnknownHostException; }
    private final Lookup lookup;

    public PublicMcpAddressPolicy() { this(InetAddress::getAllByName); }
    PublicMcpAddressPolicy(Lookup lookup) { this.lookup = lookup; }

    public void validateUri(URI uri) throws DestinationRejectedException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.toString().length() > 4096
            || uri.getPort() == 0 || uri.getPort() > 65535) throw rejected();
        String host = normalize(uri.getHost());
        if (host.contains("%") || host.endsWith(".") || host.equals("localhost") || host.endsWith(".localhost")
            || host.endsWith(".local") || host.endsWith(".internal")) throw rejected();
        // Apache can bypass its DNS resolver for numeric literals, so guard those before executeOpen too.
        if (host.contains(":") || host.matches("[0-9.]+")) {
            try { if (!isPublic(InetAddress.getByName(host))) throw rejected(); }
            catch (UnknownHostException invalid) { throw rejected(); }
        }
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        Future<InetAddress[]> pending = null;
        try {
            pending = DNS.submit(() -> lookup.resolve(normalize(host)));
            InetAddress[] addresses = pending.get(3, TimeUnit.SECONDS);
            if (addresses == null || addresses.length == 0 || addresses.length > 8
                || Arrays.stream(addresses).anyMatch(address -> !isPublic(address))) throw rejected();
            return addresses.clone();
        } catch (DestinationRejectedException denied) { throw denied; }
        catch (Exception failure) { throw new DestinationRejectedException("MCP destination DNS could not be safely resolved."); }
        finally { if (pending != null) pending.cancel(true); }
    }

    @Override
    public String resolveCanonicalHostname(String host) { return normalize(host); }

    static String normalize(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("[") && normalized.endsWith("]") ? normalized.substring(1, normalized.length() - 1) : normalized;
    }

    static boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            return (bytes[0] & 0xe0) == 0x20
                && !(bytes[0] == 0x20 && bytes[1] == 1 && ((bytes[2] & 0xfe) == 0 || (bytes[2] == 0x0d && bytes[3] == (byte) 0xb8)))
                && !(bytes[0] == 0x20 && bytes[1] == 2)
                && !(bytes[0] == 0x3f && (bytes[1] == (byte) 0xfe || bytes[1] == (byte) 0xff));
        }
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255, d = bytes[3] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
            && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
            && !(a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
            && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100))) && !(a == 203 && b == 0 && c == 113)
            && !(a == 168 && b == 63 && c == 129 && d == 16);
    }

    private static DestinationRejectedException rejected() {
        return new DestinationRejectedException("MCP destination must be public HTTPS; no request was dispatched.");
    }

    public static final class DestinationRejectedException extends UnknownHostException {
        public DestinationRejectedException(String message) { super(message); }
    }
}
