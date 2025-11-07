package com.ai.infrastructure.onnxstarter;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.embedding.ONNXEmbeddingProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration entry point for the optional ONNX starter.
 * <p>
 * When this module is on the classpath it ensures the core AI infrastructure
 * auto-configuration is loaded and logs the availability of bundled ONNX assets.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ONNXEmbeddingProvider.class)
@Import(AIInfrastructureAutoConfiguration.class)
@EnableConfigurationProperties
public class ONNXStarterAutoConfiguration {

    @PostConstruct
    void logStarterActivation() {
        log.info("AI Infrastructure ONNX starter detected. Bundled ONNX runtime assets and defaults are active.");
    }
}
