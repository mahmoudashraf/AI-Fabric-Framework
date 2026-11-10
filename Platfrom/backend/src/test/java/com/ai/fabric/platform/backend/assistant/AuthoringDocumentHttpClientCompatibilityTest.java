package com.ai.fabric.platform.backend.assistant;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.SpringVersion;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual backend Spring/Apache classpath without making an outbound request. */
class AuthoringDocumentHttpClientCompatibilityTest {
    @Test
    void resolvesTheQualifiedPinsInsteadOfBootsOlderManagedHttpCore() {
        assertThat(SpringBootVersion.getVersion()).isEqualTo("3.2.0");
        assertThat(SpringVersion.getVersion()).isEqualTo("6.1.1");
        assertThat(HttpClients.class.getPackage().getImplementationVersion()).isEqualTo("5.6.5");
        assertThat(HttpHost.class.getPackage().getImplementationVersion()).isEqualTo("5.4.4");
        assertThat(HttpVersionPolicy.class.getPackage().getImplementationVersion()).isEqualTo("5.4.4");
    }

    @Test
    void springBootsDefaultFactoryAndSpringWebRequestConstructionRemainCompatible() throws Exception {
        var rest = new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(1))
                .setReadTimeout(Duration.ofSeconds(1)).build();
        assertThat(rest.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
        var factory = (HttpComponentsClientHttpRequestFactory) rest.getRequestFactory();
        try {
            var request = factory.createRequest(URI.create("https://example.invalid/compatibility"), HttpMethod.GET);
            assertThat(request.getURI()).isEqualTo(URI.create("https://example.invalid/compatibility"));
            assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        } finally {
            factory.destroy();
        }
    }
}
