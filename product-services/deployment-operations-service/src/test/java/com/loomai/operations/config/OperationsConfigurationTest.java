package com.loomai.operations.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.operations.OperationsTestConfiguration;
import com.loomai.operations.web.OperationsApiFilter;
import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OperationsConfigurationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"too-short", "has whitespace but otherwise a sufficiently long secret"})
    void missingOrInvalidApiKeyFailsAtStartupWithoutLoggingItsValue(String key) {
        OperationsProperties properties = properties(key, "{\"revision\":\"unconfigured\",\"targets\":{}}");
        assertThatThrownBy(() -> new OperationsApiFilter(properties, new ObjectMapper()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("DEPLOYMENT_OPERATIONS_API_KEY must be 32-512 printable non-space ASCII characters.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{", "{}", "{\"revision\":\"test\",\"targets\":null}",
        "{\"revision\":\"test\",\"targets\":{\"target\":{\"cpuMillis\":-1}}}",
        "{\"revision\":\"test\",\"targets\":{\"target\":null}}"
    })
    void rejectsInvalidCapacityPolicyRatherThanStartingWithAnArbitraryDecision(String policy) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThatThrownBy(() -> new OperationsConfiguration().targetCapacityPolicy(
                properties(OperationsTestConfiguration.API_KEY, policy), new ObjectMapper(), factory.getValidator()))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void boundsHoldLifetimeBeforeServingRequests() {
        OperationsProperties properties = new OperationsProperties(OperationsTestConfiguration.API_KEY,
            "{\"revision\":\"test\",\"targets\":{}}", Duration.ofDays(1), Duration.ofMinutes(5), Duration.ofSeconds(30), 16384);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThatThrownBy(() -> new OperationsConfiguration().targetCapacityPolicy(properties, new ObjectMapper(), factory.getValidator()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("hold TTL");
        }
    }

    private OperationsProperties properties(String key, String policy) {
        return new OperationsProperties(key, policy, Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofSeconds(30), 16384);
    }
}
