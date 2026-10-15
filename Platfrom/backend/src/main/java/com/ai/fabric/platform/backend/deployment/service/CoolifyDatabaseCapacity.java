package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

final class CoolifyDatabaseCapacity {
    static final String CPU_FIELD = "runtimeDatabaseCpuMillicores";
    static final String MEMORY_FIELD = "runtimeDatabaseMemoryMiB";
    private CoolifyDatabaseCapacity() { }

    static CoolifyDatabaseRuntimeSettings of(long cpuMillicores, long memoryMiB) {
        requireBounded(cpuMillicores, memoryMiB);
        return new CoolifyDatabaseRuntimeSettings(
            memoryMiB + "M",
            null,
            null,
            BigDecimal.valueOf(cpuMillicores, 3).toPlainString(),
            null
        );
    }

    static CoolifyDatabaseRuntimeSettings fromTargetDefaults(JsonNode defaults, boolean required) {
        boolean hasCpu = defaults != null && defaults.has(CPU_FIELD);
        boolean hasMemory = defaults != null && defaults.has(MEMORY_FIELD);
        if (!hasCpu && !hasMemory && !required) {
            return null;
        }
        if (!hasCpu || !hasMemory || !defaults.path(CPU_FIELD).canConvertToInt()
            || !defaults.path(MEMORY_FIELD).canConvertToInt()) {
            throw invalidProfile();
        }
        try {
            return of(defaults.path(CPU_FIELD).intValue(), defaults.path(MEMORY_FIELD).intValue());
        } catch (IllegalArgumentException ex) {
            throw invalidProfile();
        }
    }

    static long cpuMillicores(CoolifyDatabaseRuntimeSettings settings) {
        try {
            return new BigDecimal(settings.limitsCpus()).movePointRight(3).longValueExact();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid Coolify database CPU limit.", ex);
        }
    }

    static long memoryBytes(CoolifyDatabaseRuntimeSettings settings) {
        return CoolifyCapacityValues.memoryBytes(settings.limitsMemory());
    }

    static void requireReadback(CoolifyDatabaseSummary database, CoolifyDatabaseRuntimeSettings expected,
                                String driftCode) {
        if (expected == null) return;
        String observedMemory = text(database.raw(), "limits_memory", "limitsMemory");
        String observedCpu = text(database.raw(), "limits_cpus", "limitsCpus");
        boolean matches;
        try {
            matches = CoolifyCapacityValues.memoryBytes(expected.limitsMemory())
                == CoolifyCapacityValues.memoryBytes(observedMemory)
                && new BigDecimal(expected.limitsCpus()).compareTo(new BigDecimal(observedCpu)) == 0;
        } catch (RuntimeException ex) {
            matches = false;
        }
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, driftCode);
        }
    }

    private static void requireBounded(long cpuMillicores, long memoryMiB) {
        if (cpuMillicores < 10 || cpuMillicores > 64_000 || memoryMiB < 64 || memoryMiB > 1_048_576) {
            throw new IllegalArgumentException("Database capacity is outside supported bounds.");
        }
    }

    private static String text(JsonNode raw, String snake, String camel) {
        if (raw == null) return null;
        String value = raw.path(snake).asText(null);
        return value == null || value.isBlank() ? raw.path(camel).asText(null) : value;
    }

    private static ResponseStatusException invalidProfile() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "RUNTIME_DATABASE_CAPACITY_INVALID");
    }
}
