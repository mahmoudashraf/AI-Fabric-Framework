package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.retirement.ProviderHostCommandRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class RuntimeColdStoreReadbackPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final List<String> TABLES = List.of("chat_sessions", "ai_conversation_control", "ai_conversation_orchestration_state", "ai_specialist_execution");
    @BeforeEach void tables() throws Exception {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE"); statement.execute("CREATE SCHEMA public");
            for (String table : TABLES) statement.execute("CREATE TABLE " + table + " (id integer, status text)");
        }
    }
    @Test void actualFixedQueryReturnsAllFourZeroTotals() {
        var proof = reader().requireEmpty(TargetIntent.CUSTOMER_STAGING, "synthetic-server", PG.getContainerId());
        assertThat(proof.databaseUuid()).isEqualTo(PG.getContainerId()); assertThat(proof.counts()).hasSize(4);
        assertThat(proof.counts().values()).containsOnly(0L);
    }
    @ParameterizedTest @ValueSource(strings = {"chat_sessions", "ai_conversation_control", "ai_conversation_orchestration_state", "ai_specialist_execution"})
    void totalsIncludeClosedOrOtherwiseInactiveRows(String table) throws Exception {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + table + " VALUES (1,'CLOSED')");
        }
        assertThatThrownBy(() -> reader().requireEmpty(TargetIntent.CUSTOMER_STAGING, "synthetic-server", PG.getContainerId())).hasMessageContaining("NOT_EMPTY");
    }
    @Test void absentTableIsNeverReportedAsZero() throws Exception {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE ai_specialist_execution");
        }
        assertThatThrownBy(() -> reader().requireEmpty(TargetIntent.CUSTOMER_STAGING, "synthetic-server", PG.getContainerId())).hasMessageContaining("READBACK_FAILED");
    }
    @Test void fixedScriptActuallyEnforcesReadOnlyAtPostgresBoundary() throws Exception {
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE chat_sessions");
            statement.execute("CREATE TABLE read_only_probe(id integer)");
            statement.execute("CREATE FUNCTION read_only_probe_function() RETURNS SETOF integer LANGUAGE plpgsql AS $$ BEGIN INSERT INTO read_only_probe VALUES (1); RETURN NEXT 1; END $$");
            statement.execute("CREATE VIEW chat_sessions AS SELECT * FROM read_only_probe_function()");
        }
        assertThatThrownBy(() -> reader().requireEmpty(TargetIntent.CUSTOMER_STAGING, "synthetic-server", PG.getContainerId())).hasMessageContaining("READBACK_FAILED");
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM read_only_probe")) {
            result.next(); assertThat(result.getLong(1)).isZero();
        }
    }
    private RuntimeColdStoreReadback reader() {
        return new RuntimeColdStoreReadback((intent, server, command) -> {
            assertThat(command).isEqualTo(RuntimeColdStoreReadback.command(PG.getContainerId()));
            try {
                var result = PG.execInContainer("sh", "-lc", RuntimeColdStoreReadback.fixedScript());
                return new ProviderHostCommandRunner.Result(result.getExitCode(), result.getStdout() + result.getStderr());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("synthetic local test interrupted");
            } catch (java.io.IOException error) { throw new IllegalStateException("synthetic local test unavailable", error); }
        }, new ObjectMapper());
    }
}
