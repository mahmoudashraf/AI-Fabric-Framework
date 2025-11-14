package com.ai.infrastructure.onnxstarter;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.provider.onnx.ONNXEmbeddingProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@AutoConfiguration
@ConditionalOnClass(ONNXEmbeddingProvider.class)
@Import(AIInfrastructureAutoConfiguration.class)
@EnableConfigurationProperties
public class ONNXStarterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ONNXStarterAutoConfiguration.class);

    @PostConstruct
    void logStarterActivation() {
        log.info("AI Infrastructure ONNX starter detected. Bundled ONNX runtime assets and defaults are active.");
    }
}
