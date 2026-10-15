package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoolifyDatabaseCapacityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTypedProfileCapacityAndAcceptsEquivalentProviderNormalization() throws Exception {
        var settings = CoolifyDatabaseCapacity.fromTargetDefaults(
            mapper.readTree("{\"runtimeDatabaseCpuMillicores\":250,\"runtimeDatabaseMemoryMiB\":512}"),
            true
        );
        var database = new CoolifyDatabaseSummary("db", "db", "running", "postgresql", "user", "database",
            mapper.readTree("{\"limits_memory\":\"512m\",\"limits_cpus\":\"0.25\"}"));

        CoolifyDatabaseCapacity.requireReadback(database, settings, "DRIFT");

        assertThat(CoolifyDatabaseCapacity.cpuMillicores(settings)).isEqualTo(250);
        assertThat(CoolifyDatabaseCapacity.memoryBytes(settings)).isEqualTo(512L * 1024L * 1024L);
    }

    @Test
    void customerCapacityIsRequiredAndReadbackDriftFailsClosed() throws Exception {
        assertThatThrownBy(() -> CoolifyDatabaseCapacity.fromTargetDefaults(mapper.readTree("{}"), true))
            .hasMessageContaining("RUNTIME_DATABASE_CAPACITY_INVALID");

        var settings = CoolifyDatabaseCapacity.of(250, 512);
        var database = new CoolifyDatabaseSummary("db", "db", "running", "postgresql", "user", "database",
            mapper.readTree("{\"limits_memory\":\"256M\",\"limits_cpus\":\"0.25\"}"));
        assertThatThrownBy(() -> CoolifyDatabaseCapacity.requireReadback(database, settings, "DRIFT"))
            .hasMessageContaining("DRIFT");
    }
}
