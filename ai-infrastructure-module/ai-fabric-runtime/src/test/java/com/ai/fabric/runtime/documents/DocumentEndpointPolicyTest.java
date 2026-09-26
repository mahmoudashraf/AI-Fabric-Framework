package com.ai.fabric.runtime.documents;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentEndpointPolicyTest {

    @Test
    void acceptsOnlyTheExactApprovedPublicHttpsAuthority() throws Exception {
        var endpoint = DocumentEndpointPolicy.validate(
            "https://objects.customer.example",
            false,
            "objects.customer.example",
            false,
            ignored -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})}
        );

        assertThat(endpoint.toString()).isEqualTo("https://objects.customer.example");
        assertThatThrownBy(() -> DocumentEndpointPolicy.validate(
            "https://other.customer.example",
            false,
            "objects.customer.example",
            false,
            ignored -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})}
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("approved endpoint authority");
    }

    @Test
    void rejectsPrivateResolutionAndInsecurePublicEndpoints() {
        assertThatThrownBy(() -> DocumentEndpointPolicy.validate(
            "https://objects.customer.example",
            false,
            "objects.customer.example",
            false,
            ignored -> new InetAddress[]{InetAddress.getByAddress(new byte[]{10, 0, 0, 7})}
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("public network boundary");

        assertThatThrownBy(() -> DocumentEndpointPolicy.validate(
            "http://objects.customer.example",
            true,
            "objects.customer.example",
            false,
            ignored -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})}
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("approved endpoint authority");
    }

    @Test
    void permitsAnExactPrivateEndpointOnlyWhenTheImmutablePolicyAllowsIt() {
        var endpoint = DocumentEndpointPolicy.validate(
            "http://minio.internal:9000",
            true,
            "minio.internal",
            true,
            ignored -> {
                throw new AssertionError("Explicit private endpoints must not be resolved by public-host validation.");
            }
        );

        assertThat(endpoint.toString()).isEqualTo("http://minio.internal:9000");
    }
}
