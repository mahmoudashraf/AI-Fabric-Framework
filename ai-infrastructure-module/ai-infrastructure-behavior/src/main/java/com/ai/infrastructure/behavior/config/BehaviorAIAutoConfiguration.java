package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@DependsOn("AIEntityConfigurationLoader")
@ComponentScan(basePackages = "com.ai.infrastructure.behavior")
@EntityScan(basePackages = "com.ai.infrastructure.behavior.entity")
@EnableJpaRepositories(basePackages = "com.ai.infrastructure.behavior.repository")
public class BehaviorAIAutoConfiguration {
    
    private final AIEntityConfigurationLoader frameworkConfigLoader;
    
    @Value("${ai.behavior.mode:LIGHT}")
    private String mode;
    
    @PostConstruct
    public void registerBehaviorConfig() {
        String presetFile = "classpath:behavior-presets/behavior-ai-" + mode.toLowerCase() + ".yml";
        
        log.info("Registering Behavior Module preset configuration (mode: {})", mode);
        
        frameworkConfigLoader.loadConfigurationFromFile(presetFile, false);
        
        log.info("Behavior AI Addon ready (mode: {})", mode);
    }
}
