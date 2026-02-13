package com.ai.infrastructure.relay.service;

import com.ai.infrastructure.relay.api.ActionExecuteRequestDto;
import com.ai.infrastructure.relay.api.ActionResultDto;
import com.ai.infrastructure.relay.api.TraceContextDto;
import com.ai.infrastructure.relay.config.RelayProperties;
import com.ai.infrastructure.relay.error.RelayRequestRejectedException;
import com.ai.infrastructure.relay.store.InMemoryRelayKeyValueStore;
import com.ai.infrastructure.relay.store.RelayKeyValueStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyStoreTest {

    @Test
    void executeIdempotent_returnsSameResultForSameKeyAndParams() {
        RelayProperties props = new RelayProperties();
        props.getIdempotency().setEnabled(true);
        props.getIdempotency().setTtlSeconds(3600);

        ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
        RelayKeyValueStore kv = new InMemoryRelayKeyValueStore("test:", clock);
        IdempotencyStore store = new IdempotencyStore(props, mapper, kv);

        AtomicInteger executions = new AtomicInteger(0);
        ActionExecuteRequestDto request = new ActionExecuteRequestDto(
            "create_order",
            Map.of("sku", "SKU-1", "quantity", 1),
            "act_1",
            new TraceContextDto("req_1", "chat_1", "user_1", "sess_1")
        );

        ActionResultDto first = store.executeIdempotent(request, () -> {
            executions.incrementAndGet();
            return ActionResultDto.ok("ok", Map.of("orderRef", "PO-1"));
        });

        ActionResultDto second = store.executeIdempotent(request, () -> {
            executions.incrementAndGet();
            return ActionResultDto.ok("ok2", Map.of("orderRef", "PO-2"));
        });

        assertThat(executions.get()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void executeIdempotent_rejectsSameKeyDifferentParams() {
        RelayProperties props = new RelayProperties();
        props.getIdempotency().setEnabled(true);
        props.getIdempotency().setTtlSeconds(3600);

        ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
        RelayKeyValueStore kv = new InMemoryRelayKeyValueStore("test:", clock);
        IdempotencyStore store = new IdempotencyStore(props, mapper, kv);

        ActionExecuteRequestDto requestA = new ActionExecuteRequestDto(
            "create_order",
            Map.of("sku", "SKU-1"),
            "act_1",
            new TraceContextDto("req_1", "chat_1", "user_1", "sess_1")
        );

        ActionExecuteRequestDto requestB = new ActionExecuteRequestDto(
            "create_order",
            Map.of("sku", "SKU-2"),
            "act_1",
            new TraceContextDto("req_2", "chat_1", "user_1", "sess_1")
        );

        store.executeIdempotent(requestA, () -> ActionResultDto.ok("ok", Map.of()));

        assertThatThrownBy(() -> store.executeIdempotent(requestB, () -> ActionResultDto.ok("ok", Map.of())))
            .isInstanceOf(RelayRequestRejectedException.class)
            .satisfies(ex -> {
                RelayRequestRejectedException rejected = (RelayRequestRejectedException) ex;
                assertThat(rejected.getStatus().value()).isEqualTo(409);
                assertThat(rejected.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
            });
    }
}
