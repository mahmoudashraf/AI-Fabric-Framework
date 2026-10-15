package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoolifyApplicationCapacityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoolifyApplicationRuntimeSettings expected = new CoolifyApplicationRuntimeSettings(
        null, null, null, null, "256M", "256M", "64M", "0.250", null
    );

    @Test
    void acceptsEquivalentProviderReadback() {
        CoolifyApplicationCapacity.requireReadback(
            application("256m", "256m", "64m", "0.25"),
            expected,
            "CAPACITY_DRIFT"
        );
    }

    @Test
    void rejectsMissingOrDifferentProviderReadback() {
        assertThatThrownBy(() -> CoolifyApplicationCapacity.requireReadback(
            application("0", "0", "0", "0"),
            expected,
            "CAPACITY_DRIFT"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CAPACITY_DRIFT");

        assertThatThrownBy(() -> CoolifyApplicationCapacity.requireReadback(
            application("256M", "256M", "32M", "0.250"),
            expected,
            "CAPACITY_DRIFT"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CAPACITY_DRIFT");
    }

    private CoolifyApplicationSummary application(String memory, String memorySwap,
                                                   String memoryReservation, String cpu) {
        return new CoolifyApplicationSummary(
            "app",
            "engage",
            "https://engage.example",
            "running:healthy",
            "image",
            "tag",
            objectMapper.createObjectNode()
                .put("limits_memory", memory)
                .put("limits_memory_swap", memorySwap)
                .put("limits_memory_reservation", memoryReservation)
                .put("limits_cpus", cpu)
        );
    }
}
