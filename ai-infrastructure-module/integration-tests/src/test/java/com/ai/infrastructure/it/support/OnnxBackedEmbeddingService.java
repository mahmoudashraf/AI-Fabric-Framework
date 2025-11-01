package com.ai.infrastructure.it.support;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only embedding service that uses an ONNX model for local embedding generation.
 * <p>
 * Embeddings are generated through a lightweight identity ONNX model and a deterministic
 * hashing-based featurizer. This allows integration tests to validate hybrid AI flows
 * without relying on OpenAI for embeddings while still exercising the rest of the stack.
 */
@Slf4j
public class OnnxBackedEmbeddingService extends AIEmbeddingService {

    private static final int VECTOR_SIZE = 32;
    private static final String MODEL_NAME = "onnx-local-text-identity";

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final AtomicLong totalEmbeddings = new AtomicLong();

    public OnnxBackedEmbeddingService(AIProviderConfig config, byte[] modelBytes) {
        super(config);
        try {
            this.environment = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                this.session = environment.createSession(modelBytes, options);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to initialize ONNX embedding session", e);
        }
        log.info("Initialized ONNX-backed embedding service with model {} and vector size {}", MODEL_NAME, VECTOR_SIZE);
    }

    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        long start = System.currentTimeMillis();
        String text = request.getText();
        float[] features = encodeText(text);

        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, new float[][]{features});
             OrtSession.Result result = session.run(Map.of("input", tensor))) {

            float[][] output = (float[][]) result.get(0).getValue();
            List<Double> embedding = toDoubleList(output[0]);

            long processingTime = System.currentTimeMillis() - start;
            totalEmbeddings.incrementAndGet();

            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(MODEL_NAME)
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId("onnx-" + totalEmbeddings.get())
                .build();

        } catch (OrtException e) {
            throw new IllegalStateException("Failed to execute ONNX embedding inference", e);
        }
    }

    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts, String entityType) {
        List<AIEmbeddingResponse> responses = new ArrayList<>(texts.size());
        for (String text : texts) {
            responses.add(generateEmbedding(AIEmbeddingRequest.builder()
                .text(text)
                .model(MODEL_NAME)
                .entityType(entityType)
                .build()));
        }
        return responses;
    }

    private float[] encodeText(String text) {
        float[] vector = new float[VECTOR_SIZE];
        if (text == null || text.isBlank()) {
            vector[0] = 1.0f;
            return vector;
        }

        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("[\\p{M}]", "")
            .replaceAll("[^a-z0-9 ]", " ");

        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.abs(token.hashCode()) % VECTOR_SIZE;
            vector[index] += 1.0f;
        }

        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        float norm = 0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm == 0f) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    private List<Double> toDoubleList(float[] values) {
        List<Double> doubles = new ArrayList<>(values.length);
        for (float value : values) {
            doubles.add((double) value);
        }
        return doubles;
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            log.warn("Error closing ONNX session", e);
        }
    }
}
