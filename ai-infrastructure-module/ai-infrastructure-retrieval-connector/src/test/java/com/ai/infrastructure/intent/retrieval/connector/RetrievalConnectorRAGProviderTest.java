package com.ai.infrastructure.intent.retrieval.connector;

import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.http.AIHttpClientFactory;
import com.ai.infrastructure.http.HttpClient;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalConnectorRAGProviderTest {

    @Test
    void performRag_shouldParseDocumentsAndContext() {
        StubHttpClient stub = new StubHttpClient(List.of(
            ResponseEntity.ok("""
                {"success":true,"documents":[{"id":"d1","content":"c1","score":0.9,"source":"policy","url":"https://x","vectorSpace":"policy","metadata":{"locale":"en_US"}}],"count":1,"totalCount":1,"cursor":null}
                """.trim())
        ));

        RetrievalConnectorRAGProvider provider = new RetrievalConnectorRAGProvider(
            props("https://example", 1, Duration.ZERO),
            factory(stub),
            null,
            fixedClock()
        );

        RAGResponse response = provider.performRag(RAGRequest.builder()
            .query("return policy")
            .entityType("policy")
            .limit(10)
            .build());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).getId()).isEqualTo("d1");
        assertThat(response.getContext()).contains("Relevant Context:");
    }

    @Test
    void performRag_shouldRetryOnRetryableErrorCode() {
        StubHttpClient stub = new StubHttpClient(List.of(
            ResponseEntity.ok("{\"success\":false,\"errorCode\":\"SERVICE_UNAVAILABLE\",\"message\":\"temp\"}"),
            ResponseEntity.ok("{\"success\":true,\"documents\":[{\"id\":\"d1\",\"content\":\"c1\",\"score\":0.9}],\"count\":1}")
        ));

        RetrievalConnectorRAGProvider provider = new RetrievalConnectorRAGProvider(
            props("https://example", 2, Duration.ZERO),
            factory(stub),
            null,
            fixedClock()
        );

        RAGResponse response = provider.performRag(RAGRequest.builder()
            .query("q")
            .entityType("vs")
            .limit(1)
            .build());

        assertThat(response.getSuccess()).isTrue();
        assertThat(stub.callCount()).isEqualTo(2);
    }

    private static AIRetrievalConnectorProperties props(String baseUrl, int maxAttempts, Duration initialBackoff) {
        AIRetrievalConnectorProperties props = new AIRetrievalConnectorProperties();
        props.setEnabled(true);
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

