package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.http.AIHttpClientFactory;
import com.ai.infrastructure.http.HttpClient;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionListPayload;
import com.ai.infrastructure.intent.action.ActionObjectPayload;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActionConnectorExecutorTest {

    @Test
    void execute_shouldParseObjectPayloadSuccess() {
        StubHttpClient stub = new StubHttpClient(List.of(
            ResponseEntity.ok("{\"success\":true,\"message\":\"ok\",\"data\":{\"orderRef\":\"PO-1\"}}")
        ));

        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            connectorProps("https://example", 1, Duration.ZERO),
            factory(stub),
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "create_purchase_order",
            ActionAccessMode.WRITE_ONLY,
            Map.of("sku", "SKU-1", "quantity", 1),
            testContext()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData()).isInstanceOf(ActionObjectPayload.class);
        assertThat(result.getData().toMap()).containsEntry("orderRef", "PO-1");
    }

    @Test
    void execute_shouldParseListPayloadWithCursorAndTotalCount() {
        StubHttpClient stub = new StubHttpClient(List.of(
            ResponseEntity.ok("""
                {"success":true,"message":"Products","data":{"_count":2,"_items":[{"id":"p1"},{"id":"p2"}],"_totalCount":10,"_cursor":"abc","note":"x"}}
                """.trim())
        ));

        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            connectorProps("https://example", 1, Duration.ZERO),
            factory(stub),
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "list_products",
            ActionAccessMode.READ,
            Map.of("q", "sony"),
            testContext()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(ActionListPayload.class);
        Map<String, Object> map = result.getData().toMap();
        assertThat(map).containsEntry("_count", 2);
        assertThat(map).containsEntry("_totalCount", 10L);
        assertThat(map).containsEntry("_cursor", "abc");
        assertThat(map).containsEntry("note", "x");
    }

    @Test
    void execute_shouldRetryOnRetryableErrorCodeWhenIdempotent() {
        StubHttpClient stub = new StubHttpClient(List.of(
            ResponseEntity.ok("{\"success\":false,\"errorCode\":\"SERVICE_UNAVAILABLE\",\"message\":\"temp\"}"),
            ResponseEntity.ok("{\"success\":true,\"message\":\"ok\",\"data\":{\"orderRef\":\"PO-2\"}}")
        ));

        ActionConnectorExecutor executor = new ActionConnectorExecutor(
            connectorProps("https://example", 2, Duration.ZERO),
            factory(stub),
            null,
            fixedClock()
        );

        ActionResult result = executor.execute(
            "create_purchase_order",
            ActionAccessMode.WRITE_ONLY,
            Map.of("sku", "SKU-2", "quantity", 1),
            testContext()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(stub.callCount()).isEqualTo(2);
    }

    private static AIActionConnectorProperties connectorProps(String baseUrl, int maxAttempts, Duration initialBackoff) {
        AIActionConnectorProperties props = new AIActionConnectorProperties();
        props.setBaseUrl(baseUrl);
        props.setMaxAttempts(maxAttempts);
        props.setInitialBackoff(initialBackoff);
        props.setConnectTimeout(Duration.ofMillis(1));
        props.setReadTimeout(Duration.ofMillis(1));
        return props;
    }

    private static AIHttpClientFactory factory(HttpClient client) {
        return new AIHttpClientFactory() {
            @Override
            public HttpClient create() {
                return client;
            }

            @Override
            public HttpClient create(Duration connectTimeout, Duration readTimeout) {
                return client;
            }
        };
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-02-11T00:00:00Z"), ZoneOffset.UTC);
    }

    private static ActionContext testContext() {
        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("u1")
            .sessionId("s1")
            .conversationId("c1")
            .requestId("r1")
            .build();
        return new ActionContext(orch, null);
    }

    private static final class StubHttpClient implements HttpClient {
        private final List<ResponseEntity<String>> responses;
        private final AtomicInteger calls = new AtomicInteger(0);

        private StubHttpClient(List<ResponseEntity<String>> responses) {
            this.responses = responses != null ? new ArrayList<>(responses) : List.of();
        }

        @Override
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
            int idx = calls.getAndIncrement();
            @SuppressWarnings("unchecked")
            ResponseEntity<T> casted = (ResponseEntity<T>) responses.get(Math.min(idx, responses.size() - 1));
            return casted;
        }

        int callCount() {
            return calls.get();
        }
    }
}
