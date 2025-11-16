package com.ai.behavior.config;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnClass(BehaviorIngestionService.class)
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BehaviorProperties.class)
@Import(BehaviorProcessingConfiguration.class)
@ComponentScan("com.ai.behavior")
public class AIBehaviorAutoConfiguration {
}
