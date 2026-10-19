package com.ai.fabric.platform.backend.externalintegration;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/** No network in static review. Pre-effect DNS checks never contact a business API. */
@Component
public class ExternalIntegrationEndpointPolicy {
    @FunctionalInterface interface Lookup { InetAddress[] resolve(String host) throws UnknownHostException; }
    private final Lookup lookup;
    public ExternalIntegrationEndpointPolicy() { this(InetAddress::getAllByName); }
    ExternalIntegrationEndpointPolicy(Lookup lookup) { this.lookup = lookup; }

    public void requireHost(String host) {
        if (host == null || host.length() > 253 || !host.equals(host.toLowerCase(Locale.ROOT))
            || !host.matches("[a-z][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)*\\.[a-z]{2,63}")
            || Set.of("localhost", "local", "internal", "lan", "home", "invalid").contains(host.substring(host.lastIndexOf('.') + 1))) {
            throw ExternalIntegrationPolicy.conflict("ENDPOINT_HOST_INVALID");
        }
    }
    public URI requireUrl(String value, Set<String> allowedHosts) {
        try {
            URI uri = new URI(value);
            requireHost(uri.getHost());
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !allowedHosts.contains(uri.getHost()) || !uri.normalize().equals(uri)
                || !uri.getRawPath().matches("[A-Za-z0-9/_~.-]*")
                || uri.getRawPath().contains("//")) throw ExternalIntegrationPolicy.conflict("ENDPOINT_NOT_REVIEWED");
            return uri;
        } catch (URISyntaxException | NullPointerException error) {
            throw ExternalIntegrationPolicy.conflict("ENDPOINT_INVALID");
        }
    }
    public void requirePublicDns(Set<String> hosts) {
        for (String host : hosts) {
            requireHost(host);
            try {
                InetAddress[] addresses = lookup.resolve(host);
                if (addresses == null || addresses.length == 0 || addresses.length > 32) throw ExternalIntegrationPolicy.conflict("ENDPOINT_DNS_UNPROVEN");
                for (InetAddress address : addresses) if (!routable(address)) throw ExternalIntegrationPolicy.conflict("ENDPOINT_ADDRESS_FORBIDDEN");
            } catch (UnknownHostException error) { throw ExternalIntegrationPolicy.conflict("ENDPOINT_DNS_UNPROVEN"); }
        }
    }
    private boolean routable(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = Byte.toUnsignedInt(bytes[0]), b = Byte.toUnsignedInt(bytes[1]);
        if (bytes.length == 4) return a != 0 && a != 127 && a < 224
            && !(a == 100 && b >= 64 && b <= 127) && !(a == 192 && b == 0)
            && !(a == 198 && (b == 18 || b == 19 || b == 51)) && !(a == 203 && b == 0);
        return bytes.length == 16 && (a & 0xe0) == 0x20
            && !(a == 0x20 && b == 0x01 && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8);
    }
}
