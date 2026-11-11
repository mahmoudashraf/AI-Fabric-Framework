package com.ai.fabric.product.mcp.gateway.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicMcpAddressPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.0.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1",
        "100.64.0.1", "192.0.0.8", "192.0.2.1", "192.88.99.1", "198.18.0.1", "198.51.100.1", "203.0.113.1",
        "224.0.0.1", "255.255.255.255", "168.63.129.16", "::", "::1", "fc00::1", "fe80::1", "ff02::1",
        "64:ff9b::a00:1", "::ffff:127.0.0.1", "2001:db8::1", "2001::1", "2002:7f00:1::1", "3ffe::1", "3fff::1"})
    void rejectsNonpublicAndTransitionAddresses(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        assertThat(PublicMcpAddressPolicy.isPublic(resolved)).isFalse();
        var policy = new PublicMcpAddressPolicy(host -> new InetAddress[]{resolved});
        assertThatThrownBy(() -> policy.resolve("customer.example")).isInstanceOf(UnknownHostException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    void returnsTheValidatedAddressesDirectlyToTheConnectionOperator(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        assertThat(PublicMcpAddressPolicy.isPublic(resolved)).isTrue();
        AtomicInteger lookups = new AtomicInteger();
        var policy = new PublicMcpAddressPolicy(host -> {
            lookups.incrementAndGet(); return new InetAddress[]{resolved};
        });
        assertThat(policy.resolve("customer.example")).containsExactly(resolved);
        assertThat(policy.resolveCanonicalHostname("customer.example")).isEqualTo("customer.example");
        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    void rejectsMixedPublicAndPrivateAnswersRatherThanFilteringThem() throws Exception {
        var policy = new PublicMcpAddressPolicy(host -> new InetAddress[]{InetAddress.getByName("1.1.1.1"), InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() -> policy.resolve("customer.example")).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void aLaterPrivateDnsAnswerCannotReuseAPreflightApproval() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        var policy = new PublicMcpAddressPolicy(host -> new InetAddress[]{InetAddress.getByName(lookups.incrementAndGet() == 1 ? "1.1.1.1" : "127.0.0.1")});
        assertThat(policy.resolve("customer.example")).containsExactly(InetAddress.getByName("1.1.1.1"));
        assertThatThrownBy(() -> policy.resolve("customer.example")).isInstanceOf(UnknownHostException.class);
        assertThat(lookups.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/mcp", "https://user:pass@example.com/mcp", "https://example.com/mcp#fragment",
        "https://localhost/mcp", "https://a.internal/mcp", "https://127.0.0.1/mcp", "https://[::1]/mcp", "https://169.254.169.254/mcp"})
    void validatesUrisAndNumericLiteralsThatApacheWouldNotSendThroughDns(String address) {
        assertThatThrownBy(() -> new PublicMcpAddressPolicy().validateUri(URI.create(address))).isInstanceOf(UnknownHostException.class);
    }
}
