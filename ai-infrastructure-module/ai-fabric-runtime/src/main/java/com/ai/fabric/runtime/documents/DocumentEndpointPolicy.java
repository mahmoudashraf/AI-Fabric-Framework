package com.ai.fabric.runtime.documents;

import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

final class DocumentEndpointPolicy {

    private DocumentEndpointPolicy() {
    }

    static URI validate(
        String value,
        boolean allowInsecure,
        String allowedHost,
        boolean allowPrivateEndpoint
    ) {
        return validate(value, allowInsecure, allowedHost, allowPrivateEndpoint, InetAddress::getAllByName);
    }

    static URI validate(
        String value,
        boolean allowInsecure,
        String allowedHost,
        boolean allowPrivateEndpoint,
        HostResolver resolver
    ) {
        URI endpoint;
        try {
            endpoint = URI.create(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Configured S3 endpoint is invalid", exception);
        }
        String host = normalizeHost(endpoint.getHost());
        String expectedHost = normalizeHost(allowedHost);
        String scheme = endpoint.getScheme();
        if (!StringUtils.hasText(host)
            || !host.equals(expectedHost)
            || endpoint.getUserInfo() != null
            || endpoint.getQuery() != null
            || endpoint.getFragment() != null
            || (StringUtils.hasText(endpoint.getPath()) && !"/".equals(endpoint.getPath()))
            || (!"https".equalsIgnoreCase(scheme)
                && !(allowPrivateEndpoint && allowInsecure && "http".equalsIgnoreCase(scheme)))) {
            throw new IllegalStateException("Configured S3 endpoint is outside the approved endpoint authority");
        }
        if (!allowPrivateEndpoint) {
            InetAddress[] addresses;
            try {
                addresses = resolver.resolve(host);
            } catch (UnknownHostException exception) {
                throw new IllegalStateException("Configured S3 endpoint host could not be resolved", exception);
            }
            if (addresses == null || addresses.length == 0
                || Arrays.stream(addresses).anyMatch(address -> !isPubliclyRoutable(address))) {
                throw new IllegalStateException("Configured S3 endpoint resolved outside the approved public network boundary");
            }
        }
        return endpoint;
    }

    private static String normalizeHost(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String host = value.trim().toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static boolean isPubliclyRoutable(InetAddress address) {
        if (address == null
            || address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0
                && first != 10
                && first != 127
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168)
                && first < 224;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
