package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

final class CoolifyApplicationCapacity {
    private CoolifyApplicationCapacity() { }

    static void requireReadback(CoolifyApplicationSummary application,
                                CoolifyApplicationRuntimeSettings expected,
                                String driftCode) {
        if (expected == null) {
            return;
        }
        String observedMemory = text(application, "limits_memory", "limitsMemory");
        String observedMemorySwap = text(application, "limits_memory_swap", "limitsMemorySwap");
        String observedMemoryReservation = text(application, "limits_memory_reservation", "limitsMemoryReservation");
        String observedCpu = text(application, "limits_cpus", "limitsCpus");
        String observedCpuSet = text(application, "limits_cpuset", "limitsCpuSet");
        boolean matches;
        try {
            matches = CoolifyCapacityValues.memoryBytes(expected.limitsMemory())
                == CoolifyCapacityValues.memoryBytes(observedMemory)
                && sameMemory(expected.limitsMemorySwap(), observedMemorySwap)
                && sameMemory(expected.limitsMemoryReservation(), observedMemoryReservation)
                && new BigDecimal(expected.limitsCpus()).compareTo(new BigDecimal(observedCpu)) == 0;
            if (expected.limitsCpuSet() != null && !expected.limitsCpuSet().isBlank()) {
                matches = matches && expected.limitsCpuSet().trim().equals(observedCpuSet);
            }
        } catch (RuntimeException ex) {
            matches = false;
        }
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, driftCode);
        }
    }

    private static boolean sameMemory(String expected, String observed) {
        return expected == null || expected.isBlank()
            || CoolifyCapacityValues.memoryBytes(expected) == CoolifyCapacityValues.memoryBytes(observed);
    }

    private static String text(CoolifyApplicationSummary application, String snake, String camel) {
        if (application == null || application.raw() == null) {
            return null;
        }
        String value = application.raw().path(snake).asText(null);
        return value == null || value.isBlank() ? application.raw().path(camel).asText(null) : value;
    }
}
