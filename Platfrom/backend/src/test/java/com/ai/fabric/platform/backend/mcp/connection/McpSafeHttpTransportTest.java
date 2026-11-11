package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class McpSafeHttpTransportTest {
    @Test void onlyPublicHttpsHostnamesWithoutUrlCredentialsFragmentsOrPortsAreAccepted() {
        assertThat(McpSafeHttpTransport.validate("https://docs.example.org/mcp").getHost()).isEqualTo("docs.example.org");
        for (String value : List.of("http://docs.example.org/mcp", "https://user:password@docs.example.org/mcp", "https://docs.example.org/mcp#token",
            "https://127.0.0.1/mcp", "https://localhost/mcp", "https://metadata.internal/mcp", "https://docs.example.org:8080/mcp", "file:///etc/passwd"))
            assertThatThrownBy(() -> McpSafeHttpTransport.validate(value)).as(value).isInstanceOf(ResponseStatusException.class);
    }
    @Test void privateMetadataDocumentationAndMappedAddressesAreNotPublicDestinations() throws Exception {
        for (String address : List.of("127.0.0.1", "10.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1", "100.64.0.1",
            "168.63.129.16", "192.0.2.1", "198.51.100.1", "203.0.113.1", "::1", "fc00::1", "2001:db8::1", "64:ff9b::a00:1"))
            assertThat(McpSafeHttpTransport.publicAddress(InetAddress.getByName(address))).as(address).isFalse();
        assertThat(McpSafeHttpTransport.publicAddress(InetAddress.getByName("1.1.1.1"))).isTrue();
        assertThat(McpSafeHttpTransport.publicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }
}
