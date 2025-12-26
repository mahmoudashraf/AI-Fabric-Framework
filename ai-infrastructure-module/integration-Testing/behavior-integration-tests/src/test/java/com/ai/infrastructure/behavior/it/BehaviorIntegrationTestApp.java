package com.ai.infrastructure.behavior.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.ai.infrastructure")
@EntityScan(basePackages = "com.ai.infrastructure")
@EnableJpaRepositories(basePackages = "com.ai.infrastructure")
public class BehaviorIntegrationTestApp {
    public static void main(String[] args) {
        SpringApplication.run(BehaviorIntegrationTestApp.class, args);
    }
}
