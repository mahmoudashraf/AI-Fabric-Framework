package com.ai.infrastructure.provider.openai;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.http.HttpClient;
import com.ai.infrastructure.provider.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIProviderTest {

    @Test
    void omitsNullOptionalGenerationFields() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse());
        OpenAIProvider provider = new OpenAIProvider(config(null, null), httpClient);

        provider.generateContent(AIGenerationRequest.builder()
            .prompt("Classify this request")
            .systemPrompt("Return JSON only.")
            .model("gpt-5.4-nano")
            .build());

        assertThat(httpClient.lastRequestBody())
            .doesNotContainKeys("max_tokens", "temperature");
    }

    @Test
    void keepsConfiguredOptionalGenerationFields() {
        RecordingHttpClient httpClient = new RecordingHttpClient(successResponse());
        OpenAIProvider provider = new OpenAIProvider(config(512, 0.2d), httpClient);

        provider.generateContent(AIGenerationRequest.builder()
            .prompt("Classify this request")
            .systemPrompt("Return JSON only.")
            .model("gpt-5.4-nano")
            .build());

        assertThat(httpClient.lastRequestBody())
            .containsEntry("max_tokens", 512)
            .containsEntry("temperature", 0.2d);
    }

    private static ProviderConfig config(Integer maxTokens, Double temperature) {
        return ProviderConfig.builder()
            .providerName("openai")
            .apiKey("test-key")
            .baseUrl("https://api.openai.com/v1")
            .defaultModel("gpt-4o-mini")
            .defaultEmbeddingModel("text-embedding-3-small")
            .maxTokens(maxTokens)
            .temperature(temperature)
            .timeoutSeconds(30)
            .enabled(true)
            .build();
    }

    private static ResponseEntity<Map> successResponse() {
        return ResponseEntity.ok(Map.of(
            "model", "gpt-5.4-nano",
            "choices", List.of(Map.of(
                "message", Map.of("content", "{\"ok\":true}"),
                "finish_reason", "stop"
            )),
            "usage", Map.of(
                "prompt_tokens", 10,
                "completion_tokens", 5,
                "total_tokens", 15
            )
        ));
    }

    private static final class RecordingHttpClient implements HttpClient {
        private final ResponseEntity<Map> response;
        private Map<String, Object> lastRequestBody;

        private RecordingHttpClient(ResponseEntity<Map> response) {
            this.response = response;
        }

        @Override
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) requestEntity.getBody();
            lastRequestBody = body;
            @SuppressWarnings("unchecked")
            ResponseEntity<T> casted = (ResponseEntity<T>) response;
            return casted;
        }

        private Map<String, Object> lastRequestBody() {
            return lastRequestBody;
        }
    }
}
