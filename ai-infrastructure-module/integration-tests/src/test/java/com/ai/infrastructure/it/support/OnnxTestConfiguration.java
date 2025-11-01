package com.ai.infrastructure.it.support;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;

/**
 * Shared ONNX test configuration that swaps the default embedding service with
 * a lightweight ONNX-backed implementation for integration testing.
 */
@TestConfiguration
public class OnnxTestConfiguration {

    @Bean
    @Primary
    public AIEmbeddingService onnxEmbeddingService(AIProviderConfig config, ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:models/text-identity-encoder.onnx");
        byte[] modelBytes;
        try (var inputStream = resource.getInputStream()) {
            modelBytes = StreamUtils.copyToByteArray(inputStream);
        }
        return new OnnxBackedEmbeddingService(config, modelBytes);
    }
}
