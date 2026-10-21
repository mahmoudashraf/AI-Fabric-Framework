package com.ai.fabric.platform.backend.web;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class PlatformForwardedTransportTest {
    @Test
    void nativeForwardingIsEnabledWithoutTrustingEveryProxy() throws Exception {
        var properties = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        assertThat(properties.getFirst().getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(properties.getFirst().containsProperty("server.tomcat.remoteip.internal-proxies")).isFalse();
        assertThat(properties.getFirst().containsProperty("server.tomcat.remoteip.trusted-proxies")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"172.18.0.2,https,true", "172.18.0.2,http,false", "203.0.113.8,https,false", "203.0.113.8,http,false"})
    void tlsPostureUsesTheNativeTrustedProxyBoundary(String peer, String forwardedProtocol, boolean secure) throws Exception {
        var connector = new Connector();
        var request = new Request(connector);
        var coyote = new org.apache.coyote.Request();
        coyote.scheme().setString("http");
        coyote.serverName().setString("platform.example");
        coyote.setServerPort(8088);
        coyote.getMimeHeaders().addValue("x-forwarded-proto").setString(forwardedProtocol);
        request.setCoyoteRequest(coyote);
        request.setRemoteAddr(peer);
        request.setRemoteHost(peer);
        var response = new Response();
        response.setCoyoteResponse(new org.apache.coyote.Response());
        response.setRequest(request);
        request.setResponse(response);
        var observed = new AtomicBoolean();
        var next = mock(Valve.class);
        doAnswer(invocation -> { observed.set(((Request) invocation.getArgument(0)).isSecure()); return null; })
            .when(next).invoke(any(), any());
        var valve = new RemoteIpValve();
        valve.setProtocolHeader("x-forwarded-proto");
        valve.setNext(next);

        valve.invoke(request, response);

        assertThat(observed.get()).isEqualTo(secure);
    }
}
