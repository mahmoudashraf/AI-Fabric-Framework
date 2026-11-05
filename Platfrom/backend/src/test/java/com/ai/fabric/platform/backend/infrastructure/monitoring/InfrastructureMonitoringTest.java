package com.ai.fabric.platform.backend.infrastructure.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfrastructureMonitoringTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String key = "read-key-" + "a".repeat(40);

    @Test void disabledMonitoringDoesNotParseConfigurationOrContactAnyService() {
        var props = properties(); props.setEnabled(false); props.setSourcesJson("broken");
        var client = mock(InfrastructureMonitoringClient.class);
        var jdbc = mock(JdbcTemplate.class);
        var service = new InfrastructureMonitoringService(props, client, mapper, jdbc);
        assertThat(service.fleet().path("sources").get(0).path("status").asText()).isEqualTo("DISABLED");
        verifyNoInteractions(client, jdbc);
    }

    @Test void configurationIsBoundedSecretSafeAndUnavailableRatherThanStartupDependency() throws Exception {
        var props = properties();
        assertThat(props.sources(mapper).getFirst().toString()).doesNotContain(key);
        for (var config : List.of("broken", "[null]", "[{\"id\":\"x\"}]", mapper.writeValueAsString(List.of(source("http://bad.example"))))) {
            props.setAllowInsecureHttp(false); props.setSourcesJson(config);
            assertThatThrownBy(() -> props.sources(mapper)).isInstanceOf(MonitoringUnavailableException.class)
                .hasMessage("MONITORING_CONFIGURATION_INVALID");
        }
        props.setSourcesJson(mapper.writeValueAsString(List.of(source("https://user:password@example.test"))));
        assertThatThrownBy(() -> props.sources(mapper)).hasMessage("MONITORING_CONFIGURATION_INVALID");
    }

    @Test void oneUnavailableSourceDoesNotHideAnotherAndResponseNeverContainsReadKey() throws Exception {
        var props = properties();
        var other = new InfrastructureMonitoringProperties.Source("other", "Other", "production", "https://other.test", key, null);
        props.setSourcesJson(mapper.writeValueAsString(List.of(source("https://one.test"), other)));
        var client = mock(InfrastructureMonitoringClient.class);
        when(client.read(any(), anyString())).thenAnswer(call -> {
            InfrastructureMonitoringProperties.Source s = call.getArgument(0);
            if (s.id().equals("other")) throw new MonitoringUnavailableException("MONITORING_SOURCE_UNAVAILABLE");
            return mapper.readTree("{\"serverId\":\"staging\",\"status\":\"STALE\",\"host\":{\"memoryTotalBytes\":100,\"secret\":\"NO\"},\"resources\":[{\"resourceId\":\"abcdef123456\",\"name\":\"runtime\",\"environment\":\"NO\",\"customerId\":\"attacker\"}],\"readKey\":\"NO\"}");
        });
        var service = new InfrastructureMonitoringService(props, client, mapper, mock(JdbcTemplate.class));
        var view = service.fleet();
        assertThat(view.path("servers").size()).isEqualTo(1);
        assertThat(view.path("sources").get(1).path("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(view.toString()).doesNotContain(key, "attacker", "secret", "readKey", "NO");
        assertThat(view.path("servers").get(0).path("status").asText()).isEqualTo("STALE");
        service.fleet();
        verify(client, times(1)).read(argThat(s -> s.id().equals("staging")), anyString());
    }

    @Test void unknownServersAndUnboundedHistoryAreRejectedBeforeRemoteAccess() {
        var props = properties(); var client = mock(InfrastructureMonitoringClient.class);
        var service = new InfrastructureMonitoringService(props, client, mapper, mock(JdbcTemplate.class));
        assertThatThrownBy(() -> service.server("unknown")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.history("staging", "1y", null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.history("staging", "1h", "../secret")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(client);
    }

    @Test void httpAdapterUsesScopedReadCredentialValidatesIdentityAndDoesNotFollowRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/internal/v1/telemetry/servers/staging", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + key);
            var bytes = "{\"serverId\":\"staging\",\"resources\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var props = properties(); props.setAllowInsecureHttp(true);
            var source = source("http://127.0.0.1:" + server.getAddress().getPort());
            var client = new InfrastructureMonitoringClient(props, mapper);
            assertThat(client.read(source, "servers/staging").path("serverId").asText()).isEqualTo("staging");
            assertThatThrownBy(() -> client.read(source, "../../env")).isInstanceOf(IllegalArgumentException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void remoteRedirectsOversizedBodiesAndWrongIdentitiesAreNotAccepted() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var mode = new AtomicInteger(); var redirected = new AtomicInteger();
        server.createContext("/internal/v1/telemetry/servers/staging", exchange -> {
            if (mode.get() == 0) {
                exchange.getResponseHeaders().set("Location", "/redirect-target");
                exchange.sendResponseHeaders(302, -1);
            } else {
                var body = (mode.get() == 1 ? "{\"serverId\":\"other\"}" : "x".repeat(524289))
                    .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.createContext("/redirect-target", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            var props = properties(); props.setAllowInsecureHttp(true);
            var client = new InfrastructureMonitoringClient(props, mapper);
            var source = source("http://127.0.0.1:" + server.getAddress().getPort());
            for (int i = 0; i < 3; i++) {
                mode.set(i);
                assertThatThrownBy(() -> client.read(source, "servers/staging"))
                    .hasMessage("MONITORING_SOURCE_UNAVAILABLE");
            }
            assertThat(redirected.get()).isZero();
        } finally { server.stop(0); }
    }

    private InfrastructureMonitoringProperties properties() {
        var p = new InfrastructureMonitoringProperties(); p.setEnabled(true);
        try { p.setSourcesJson(mapper.writeValueAsString(List.of(source("https://one.test")))); }
        catch (Exception error) { throw new IllegalStateException(error); }
        return p;
    }
    private InfrastructureMonitoringProperties.Source source(String url) {
        return new InfrastructureMonitoringProperties.Source("staging", "Staging", "staging", url, key, null);
    }
}
