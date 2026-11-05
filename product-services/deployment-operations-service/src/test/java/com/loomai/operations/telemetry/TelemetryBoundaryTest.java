package com.loomai.operations.telemetry;

import static com.loomai.operations.telemetry.TelemetryFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.operations.OperationsTestConfiguration;
import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.web.OperationsApiFilter;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TelemetryBoundaryTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "has whitespace despite its long enough length"})
    void enabledTelemetryRequiresStrongReadAndIngestCredentials(String key) {
        assertThatThrownBy(() -> registry(true, key, SOURCES)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Telemetry keys must be 32-512 printable non-space ASCII characters.");
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace(INGEST_KEY, key == null ? "" : key)))
            .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{}", "null", "[null]", "[{}]", "{", "[]{}"})
    void invalidOrEmptyEnabledSourceRegistryFailsClosed(String json) {
        assertThatThrownBy(() -> registry(true, READ_KEY, json)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registryRejectsKeyReuseDuplicateSourcesAndUnreviewedConfiguration() {
        assertThatThrownBy(() -> registry(true, OperationsTestConfiguration.API_KEY, SOURCES)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace(INGEST_KEY, READ_KEY))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace(OTHER_KEY, INGEST_KEY))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace(INGEST_KEY, OperationsTestConfiguration.API_KEY))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace("prod-host", "stage-host"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace("\"displayName\":", "\"url\":\"https://private\",\"displayName\":")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(true, READ_KEY, SOURCES.replace("\"environment\":\"staging\"", "\"environment\":\"staging\",\"environment\":\"staging\"")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledTelemetryNeedsNoSecretsAndCannotUseTheOperationsKey() throws Exception {
        TelemetryRegistry registry = registry(false, "", "[]");
        assertThat(registry.sources()).isEmpty();
        assertThat(registry.authenticate("")).isNull();
        var request = new MockHttpServletRequest("GET", PREFIX + "/servers");
        request.addHeader("Authorization", "Bearer " + OperationsTestConfiguration.API_KEY);
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        new TelemetryApiFilter(registry, mapper).filter(request, response, (req, res) -> invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("TELEMETRY_DISABLED");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(invoked).isFalse();
    }

    @Test
    void telemetryStreamIsBoundedEvenWithoutContentLength() throws Exception {
        var request = new MockHttpServletRequest("POST", PREFIX + "/samples");
        request.addHeader("Authorization", "Bearer " + INGEST_KEY);
        request.setContentType("application/json");
        request.setContent(" ".repeat(262145).getBytes(StandardCharsets.UTF_8));
        var streamed = new HttpServletRequestWrapper(request) {
            @Override public long getContentLengthLong() { return -1; }
        };
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        new TelemetryApiFilter(registry(true, READ_KEY, SOURCES), mapper)
            .filter(streamed, response, (req, res) -> invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked).isFalse();
    }

    @Test
    void operationsFilterDelegatesOnlyExactTelemetryNamespaceThroughExplicitAuthentication() throws Exception {
        var telemetry = new TelemetryApiFilter(registry(true, READ_KEY, SOURCES), mapper);
        for (String path : new String[] {PREFIX, PREFIX + "/servers", PREFIX + "-other/servers", PREFIX + "%2Fservers"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.addHeader("Authorization", "Bearer " + OperationsTestConfiguration.API_KEY);
            var response = new MockHttpServletResponse();
            var invoked = new AtomicBoolean();
            new OperationsApiFilter(operations(), mapper, telemetry).doFilter(request, response, (req, res) -> invoked.set(true));
            if (path.equals(PREFIX) || path.startsWith(PREFIX + "/")) {
                assertThat(response.getStatus()).isEqualTo(401);
                assertThat(invoked).isFalse();
            } else assertThat(invoked).isTrue();
        }
    }

    @Test
    void telemetryControllerCannotBeReadWithoutTheExplicitBoundaryEvenIfRoutingNormalizesAPath() throws Exception {
        var service = org.mockito.Mockito.mock(TelemetryService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new TelemetryController(service))
            .setControllerAdvice(new TelemetryErrorHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(PREFIX + "/servers")
            .header("Authorization", "Bearer " + OperationsTestConfiguration.API_KEY))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private TelemetryRegistry registry(boolean enabled, String readKey, String sources) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return new TelemetryRegistry(new TelemetryProperties(enabled, readKey, sources), operations(), mapper, factory.getValidator());
        }
    }

    private OperationsProperties operations() {
        return new OperationsProperties(OperationsTestConfiguration.API_KEY, "{}", Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofSeconds(30), 16384);
    }
}
