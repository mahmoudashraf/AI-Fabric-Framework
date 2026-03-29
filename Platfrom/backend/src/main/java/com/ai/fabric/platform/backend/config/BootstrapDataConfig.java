package com.ai.fabric.platform.backend.config;

import com.ai.fabric.platform.backend.deployment.service.EcommerceDemoBootstrapService;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapDataConfig {

    @Bean
    @ConditionalOnProperty(prefix = "platform.bootstrap", name = "sample-enabled", havingValue = "true")
    public ApplicationRunner bootstrapDataInitializer(DeploymentService deploymentService) {
        return args -> deploymentService.ensureBootstrapSample();
    }

    @Bean
    public ApplicationRunner bootstrapEcommerceDemoInitializer(EcommerceDemoBootstrapService ecommerceDemoBootstrapService) {
        return args -> ecommerceDemoBootstrapService.ensureBootstrapDeployment();
    }

    @Bean
    public ApplicationRunner bootstrapIdentityInitializer(PlatformIdentityService platformIdentityService) {
        return args -> platformIdentityService.ensureBootstrapAdmin();
    }
}
