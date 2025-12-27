package com.ai.infrastructure.behavior.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.ai.infrastructure",
    "com.ai.infrastructure.behavior"
})
@Import({
    com.ai.infrastructure.config.AIInfrastructureAutoConfiguration.class,
    com.ai.infrastructure.behavior.config.BehaviorAIAutoConfiguration.class
})
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.behavior.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.behavior.repository"
})
public class BehaviorIntegrationTestApp {
    public static void main(String[] args) {
        SpringApplication.run(BehaviorIntegrationTestApp.class, args);
    }
}
