package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.retirement.ProviderHostCommandRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeColdStoreReadbackTest {
    static final String EMPTY = "{\"chat_sessions\":0,\"ai_conversation_control\":0,\"ai_conversation_orchestration_state\":0,\"ai_specialist_execution\":0}";
    static final Instant NOW = Instant.parse("2026-10-21T01:00:00Z");
    @Test void returnsAnImmutableScopeBoundFourTableProof() {
        var calls = new AtomicInteger();
        var readback = new RuntimeColdStoreReadback((intent, server, command) -> {
            calls.incrementAndGet(); assertThat(intent).isEqualTo(TargetIntent.CUSTOMER_STAGING); assertThat(server).isEqualTo("server");
            assertThat(command).isEqualTo(RuntimeColdStoreReadback.command("database"));
            return new ProviderHostCommandRunner.Result(0, "\n " + EMPTY + "\n");
        }, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        var proof = readback.requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database");
        assertThat(calls).hasValue(1); assertThat(proof.databaseUuid()).isEqualTo("database"); assertThat(proof.observedAt()).isEqualTo(NOW);
        assertThat(proof.counts()).containsOnlyKeys("chat_sessions", "ai_conversation_control", "ai_conversation_orchestration_state", "ai_specialist_execution");
        assertThat(proof.counts().values()).containsOnly(0L);
        assertThat(proof.reference()).matches("owned-postgres-empty-store-v1:[a-f0-9]{64}");
        assertThatThrownBy(() -> proof.counts().put("chat_sessions", 1L)).isInstanceOf(UnsupportedOperationException.class);
        var other = reader(0, EMPTY).requireEmpty(TargetIntent.CUSTOMER_PRODUCTION, "server", "other-database");
        assertThat(other.reference()).isNotEqualTo(proof.reference());
    }
    @ParameterizedTest @ValueSource(strings = {"", "not-json", "[]", "{}", "null", "{\"chat_sessions\":0}",
        "{\"chat_sessions\":0,\"ai_conversation_control\":0,\"ai_conversation_orchestration_state\":0,\"ai_specialist_execution\":0,\"unknown\":0}",
        "{\"chat_sessions\":0,\"chat_sessions\":0,\"ai_conversation_control\":0,\"ai_conversation_orchestration_state\":0,\"ai_specialist_execution\":0}"})
    void rejectsIncompleteUnknownOrDuplicateOutput(String output) {
        assertThatThrownBy(() -> reader(0, output).requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database"))
            .hasMessageContaining("OUTPUT_INVALID");
    }
    @ParameterizedTest @ValueSource(strings = {"\"0\"", "0.0", "null", "-1", "9223372036854775808", "[]", "{}"})
    void countsMustBeNonnegativeIntegralLongs(String count) {
        String output = EMPTY.replace("\"chat_sessions\":0", "\"chat_sessions\":" + count);
        assertThatThrownBy(() -> reader(0, output).requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database"))
            .hasMessageContaining("OUTPUT_INVALID");
    }
    @ParameterizedTest @ValueSource(strings = {"chat_sessions", "ai_conversation_control", "ai_conversation_orchestration_state", "ai_specialist_execution"})
    void everyNonzeroTotalDeniesEmptiness(String table) {
        assertThatThrownBy(() -> reader(0, EMPTY.replace("\"" + table + "\":0", "\"" + table + "\":1"))
            .requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database")).hasMessageContaining("NOT_EMPTY");
    }
    @Test void rejectsTrailingJsonOversizeAndProcessFailureWithoutExposingOutput() {
        for (String output : List.of(EMPTY + "{}", EMPTY + "\n0", EMPTY + " ".repeat(4096)))
            assertThatThrownBy(() -> reader(0, output).requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database")).hasMessageContaining("OUTPUT_INVALID");
        assertThatThrownBy(() -> reader(1, "sensitive-error-output").requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database"))
            .hasMessageContaining("READBACK_FAILED").hasMessageNotContaining("sensitive-error-output");
        assertThatThrownBy(() -> reader(0, null).requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", "database")).hasMessageContaining("OUTPUT_INVALID");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"--privileged", "db;id", "$(id)", "db\nwhoami", "db/name", " db", "db'"})
    void invalidNativeIdentityIsRejectedBeforeRunner(String uuid) {
        var calls = new AtomicInteger(); var readback = new RuntimeColdStoreReadback((intent, server, command) -> {
            calls.incrementAndGet(); throw new AssertionError("must not execute");
        }, new ObjectMapper());
        assertThatThrownBy(() -> readback.requireEmpty(TargetIntent.CUSTOMER_STAGING, "server", uuid)).hasMessageContaining("SCOPE_INVALID");
        assertThat(calls).hasValue(0);
    }
    @Test void shellQuotingPreservesTheExactFixedContainerScript() throws Exception {
        var command = RuntimeColdStoreReadback.command("owned-database");
        var process = new ProcessBuilder("/bin/sh", "-c", "printf '%s' " + command.get(5)).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isZero(); assertThat(output).isEqualTo(RuntimeColdStoreReadback.fixedScript());
        assertThat(output).contains("PGOPTIONS='-c default_transaction_read_only=on'", "psql -X -qAt -v ON_ERROR_STOP=1",
            "--username=\"${POSTGRES_USER:?}\"", "--dbname=\"${POSTGRES_DB:?}\"");
        assertThat(command).containsExactly("docker", "exec", "owned-database", "sh", "-lc", command.get(5));
    }
    private RuntimeColdStoreReadback reader(int exit, String output) {
        return new RuntimeColdStoreReadback((intent, server, command) -> new ProviderHostCommandRunner.Result(exit, output),
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
