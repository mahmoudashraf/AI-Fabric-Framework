package com.ai.fabric.embedding.worker.web;

import ai.fabric.core.AIEmbeddingService;
import ai.fabric.dto.AIEmbeddingRequest;
import ai.fabric.dto.AIEmbeddingResponse;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class EmbeddingWorkerController {

    private final AIEmbeddingService embeddingService;
    private final String requiredApiKey;

    public EmbeddingWorkerController(AIEmbeddingService embeddingService,
                                     @Value("${ai.fabric.embedding-worker.api-key:}") String requiredApiKey) {
        this.embeddingService = embeddingService;
        this.requiredApiKey = requiredApiKey == null ? "" : requiredApiKey.trim();
    }

    @PostMapping("/embeddings")
    @ResponseStatus(HttpStatus.OK)
    public OpenAiEmbeddingsResponse embeddings(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                               @RequestBody OpenAiEmbeddingsRequest request) {
        authorize(authorization);
        List<String> inputs = request.normalizedInput();
        if (inputs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input must not be empty");
        }

        List<OpenAiEmbeddingItem> data = new ArrayList<>();
        long totalProcessingMs = 0L;
        for (int index = 0; index < inputs.size(); index++) {
            AIEmbeddingResponse response = embeddingService.generateEmbedding(
                AIEmbeddingRequest.builder()
                    .text(inputs.get(index))
                    .model(request.model())
                    .build()
            );
            totalProcessingMs += response.getProcessingTimeMs() == null ? 0L : response.getProcessingTimeMs();
            data.add(new OpenAiEmbeddingItem(
                "embedding",
                index,
                response.getEmbedding()
            ));
        }

        int promptTokens = inputs.stream()
            .mapToInt(value -> Math.max(1, value.length() / 4))
            .sum();
        return new OpenAiEmbeddingsResponse(
            "list",
            data,
            request.model() == null || request.model().isBlank() ? "bge-small-en-v1.5" : request.model().trim(),
            Map.of(
                "prompt_tokens", promptTokens,
                "total_tokens", promptTokens,
                "processing_time_ms", totalProcessingMs
            )
        );
    }

    private void authorize(String authorization) {
        if (requiredApiKey.isBlank()) {
            return;
        }
        String bearer = authorization == null ? "" : authorization.trim();
        if (!bearer.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required.");
        }
        String token = bearer.substring("Bearer ".length()).trim();
        if (!requiredApiKey.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid embedding worker token.");
        }
    }

    public record OpenAiEmbeddingsRequest(
        String model,
        Object input
    ) {
        List<String> normalizedInput() {
            if (input instanceof String single && !single.isBlank()) {
                return List.of(single);
            }
            if (input instanceof List<?> list) {
                return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(value -> !value.isBlank())
                    .toList();
            }
            return List.of();
        }
    }

    public record OpenAiEmbeddingsResponse(
        String object,
        @NotEmpty List<OpenAiEmbeddingItem> data,
        String model,
        Map<String, Object> usage
    ) {
    }

    public record OpenAiEmbeddingItem(
        String object,
        int index,
        List<Double> embedding
    ) {
    }
}
