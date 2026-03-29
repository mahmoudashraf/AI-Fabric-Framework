package com.ai.fabric.platform.backend.config;

import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapDataConfig {

    @Bean
    public ApplicationRunner bootstrapDataInitializer(DeploymentService deploymentService) {
        return args -> deploymentService.ensureBootstrapSample();
    }
}

